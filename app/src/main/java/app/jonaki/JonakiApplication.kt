package app.jonaki

import android.app.Application
import app.jonaki.core.modelcatalog.ModelCatalog
import app.jonaki.core.storage.JonakiDatabase
import app.jonaki.memory.MemoryExtractor
import app.jonaki.run.AgentRunner
import app.jonaki.run.BackgroundModel
import app.jonaki.run.ChatProviders
import app.jonaki.run.ThreadCompactor
import app.jonaki.run.ThreadTitles
import app.jonaki.core.model.Role
import app.jonaki.core.skills.BuiltInSkill
import app.jonaki.core.skills.SkillLibrary
import app.jonaki.skills.AssetSkills
import app.jonaki.settings.AccountBalances
import app.jonaki.settings.AppSettings
import app.jonaki.settings.SecretStore
import app.jonaki.settings.UsdRates
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** Builds the app's long-lived parts once; screens and the service read them from here. */
class JonakiApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var database: JonakiDatabase
        private set
    lateinit var settings: AppSettings
        private set
    lateinit var secrets: SecretStore
        private set

    /** Model lists for the model picker and prices for the cost display (D-027, D-028). */
    lateinit var catalog: ModelCatalog
        private set
    lateinit var runner: AgentRunner
        private set

    /** The cheapest set-up model, for memory extraction and compaction (D-036). */
    lateinit var backgroundModel: BackgroundModel
        private set

    /** The skill library in files/skills/, which read_file can read as /skills/ (D-037). */
    lateinit var skillLibrary: SkillLibrary
        private set

    /** Account balances for the settings cards (D-031); call refreshAll() when Settings opens. */
    lateinit var balances: AccountBalances
        private set

    override fun onCreate() {
        super.onCreate()
        database = JonakiDatabase.open(this)
        skillLibrary = SkillLibrary(File(filesDir, "skills"), File(filesDir, "skills-builtin.json"))
        settings = AppSettings(this, ChatProviders::defaultModel)
        secrets = SecretStore(this)
        // One client for every call, so connections and threads are shared.
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            // Streaming replies can pause while a model thinks; the agent loop's own limits apply on top.
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
        catalog = ModelCatalog(File(cacheDir, "openrouter-models.json"), httpClient)
        backgroundModel = BackgroundModel(database, settings, secrets, httpClient, catalog)
        val memoryExtractor = MemoryExtractor(
            database = database,
            backgroundModel = backgroundModel,
            reviewMode = { settings.snapshot.value.reviewExtractedMemories },
            clock = System::currentTimeMillis,
        )
        val threadCompactor = ThreadCompactor(database, backgroundModel, catalog, clock = System::currentTimeMillis)
        runner = AgentRunner(this, database, settings, secrets, httpClient, catalog, applicationScope, memoryExtractor, threadCompactor, skillLibrary)
        balances = AccountBalances(secrets, httpClient, UsdRates(httpClient))
        applicationScope.launch {
            // A run cannot survive a killed process; mark what it left half-done.
            database.messageDao().closeInterrupted()
            database.stepDao().stopInterrupted()
        }
        applicationScope.launch {
            catalog.refreshIfStale()
        }
        applicationScope.launch {
            restoreNamesCutByOldVersion()
        }
        applicationScope.launch(Dispatchers.IO) {
            // Installs new built-in skills and updates unedited ones after an app update (D-038).
            skillLibrary.installBuiltIns(builtInSkills())
        }
    }

    /** The skills shipped in assets/skills/, read fresh each time; they are a few kilobytes. */
    fun builtInSkills(): List<BuiltInSkill> = AssetSkills.load(assets)

    /** Threads named by version 0.1.0 had their names cut at 40 characters; the first message holds the whole name. */
    private suspend fun restoreNamesCutByOldVersion() {
        val threads = database.threadDao().observeAll().first()
        for (thread in threads) {
            if (!ThreadTitles.looksCutByOldVersion(thread.title)) {
                continue
            }
            val firstUserMessage = database.messageDao().listThread(thread.id)
                .firstOrNull { message -> message.role == Role.USER.name } ?: continue
            database.threadDao().rename(thread.id, ThreadTitles.fromMessage(firstUserMessage.text))
        }
    }
}
