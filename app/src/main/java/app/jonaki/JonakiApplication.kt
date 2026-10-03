package app.jonaki

import android.app.Application
import app.jonaki.core.modelcatalog.ModelCatalog
import app.jonaki.core.storage.JonakiDatabase
import app.jonaki.files.AndroidFileDestinations
import app.jonaki.files.AttachmentDrafts
import app.jonaki.files.MediaStorePhotoLibrary
import app.jonaki.files.MediaThumbnails
import app.jonaki.feature.gallery.GallerySource
import app.jonaki.files.IncomingShares
import app.jonaki.files.LinkedFolder
import app.jonaki.files.VisibleActivity
import app.jonaki.memory.MemoryExtractor
import app.jonaki.phone.AndroidPhone
import app.jonaki.phone.ReminderBook
import app.jonaki.phone.Reminders
import app.jonaki.phone.RuntimePermissions
import app.jonaki.schedule.ScheduleBook
import app.jonaki.schedule.ScheduledTasks
import app.jonaki.schedule.ThreadTaskScheduler
import app.jonaki.run.AgentRunner
import app.jonaki.run.BackgroundModel
import app.jonaki.run.ChatProviders
import app.jonaki.run.CodeRuntimes
import app.jonaki.run.PythonSetup
import app.jonaki.runtimes.pyodide.PyodideInstaller
import app.jonaki.run.ThreadCompactor
import app.jonaki.run.ThreadTitles
import app.jonaki.core.model.Role
import app.jonaki.core.skills.BuiltInSkill
import app.jonaki.core.skills.SkillLibrary
import app.jonaki.core.skills.SkillDownloader
import app.jonaki.skills.AssetSkills
import app.jonaki.skills.SkillImporter
import app.jonaki.settings.AccountBalances
import app.jonaki.settings.AppSettings
import app.jonaki.settings.McpServerStore
import app.jonaki.settings.SecretStore
import app.jonaki.settings.UsdRates
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
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

    /** Adds skills from links and picked files (D-041). */
    lateinit var skillImporter: SkillImporter
        private set

    /** Account balances for the settings cards (D-031); call refreshAll() when Settings opens. */
    lateinit var balances: AccountBalances
        private set

    /** The activity on screen, for pickers and the share sheet that share_file opens (D-045). */
    val visibleActivity = VisibleActivity()

    /** MCP servers from Settings, for the mcp tool (D-MCP-4). */
    lateinit var mcpServers: McpServerStore
        private set

    /** The folder linked in Settings (D-043). */
    lateinit var linkedFolder: LinkedFolder
        private set

    /** Files waiting as chips for each thread's next message (D-042). */
    lateinit var attachmentDrafts: AttachmentDrafts
        private set

    /** Files and text shared from other apps, and files from the attach button. */
    lateinit var incomingShares: IncomingShares
        private set

    /** Reminders the phone tool set, kept in a file until they fire (D-M9-2). */
    lateinit var reminders: Reminders
        private set

    /** Tasks the schedule tool made, on WorkManager (D-M9-3). */
    lateinit var scheduledTasks: ScheduledTasks
        private set

    /** Installs and removes Python for Settings > Python, the tool picker and the chat's install card (M8). */
    lateinit var python: PythonSetup
        private set

    /** The phone's photos for the composer's gallery sheet, with one thumbnail cache for the app's life (D-085). */
    val gallerySource: GallerySource by lazy {
        GallerySource(MediaStorePhotoLibrary(contentResolver), MediaThumbnails(contentResolver))
    }

    override fun onCreate() {
        super.onCreate()
        // read_document's PDF reading needs PdfBox's font and glyph tables from the assets (D-051).
        PDFBoxResourceLoader.init(this)
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
        mcpServers = McpServerStore(this, secrets, File(filesDir, "mcp-tools"))
        linkedFolder = LinkedFolder(this)
        // In files/, not the cache, because Android may clear the cache while chips wait (D-GAP-1).
        attachmentDrafts = AttachmentDrafts(File(filesDir, "waiting-attachments"))
        // Read before anything is staged, so a share arriving now is not taken for a leftover.
        val leftoverAttachments = attachmentDrafts.restore()
        incomingShares = IncomingShares(contentResolver, attachmentDrafts, applicationScope)
        skillImporter = SkillImporter(skillLibrary, SkillDownloader(httpClient))
        catalog = ModelCatalog(File(cacheDir, "openrouter-models.json"), httpClient)
        backgroundModel = BackgroundModel(database, settings, secrets, httpClient, catalog)
        val memoryExtractor = MemoryExtractor(
            database = database,
            backgroundModel = backgroundModel,
            reviewMode = { settings.snapshot.value.reviewExtractedMemories },
            clock = System::currentTimeMillis,
        )
        reminders = Reminders(this, ReminderBook(File(filesDir, "reminders.json")))
        scheduledTasks = ScheduledTasks(this, ScheduleBook(File(filesDir, "scheduled-tasks.json")))
        val permissions = RuntimePermissions(this, visibleActivity)
        val threadCompactor = ThreadCompactor(database, backgroundModel, catalog, clock = System::currentTimeMillis)
        runner = AgentRunner(
            this,
            database,
            settings,
            secrets,
            httpClient,
            catalog,
            applicationScope,
            memoryExtractor,
            threadCompactor,
            skillLibrary,
            AndroidFileDestinations(this, visibleActivity, linkedFolder),
            backgroundModel,
            AndroidPhone(this, permissions, visibleActivity, reminders),
            taskSchedulerFor = { threadId -> ThreadTaskScheduler(threadId, scheduledTasks, permissions) },
            mcpServers = mcpServers,
        )
        balances = AccountBalances(secrets, httpClient, UsdRates(httpClient))
        val pythonFolder = CodeRuntimes.pythonFolder(this)
        python = PythonSetup(pythonFolder, PyodideInstaller(pythonFolder, httpClient), applicationScope)
        applicationScope.launch {
            python.refresh()
        }
        applicationScope.launch {
            // A run cannot survive a killed process; mark what it left half-done.
            database.messageDao().closeInterrupted()
            database.stepDao().stopInterrupted()
            database.subagentDao().stopInterrupted()
        }
        applicationScope.launch {
            // Incognito threads go a day after their last message; the thread list checks again (D-PRJ-2).
            runner.deleteExpiredIncognitoThreads()
        }
        applicationScope.launch {
            catalog.refreshIfStale()
        }
        applicationScope.launch {
            restoreNamesCutByOldVersion()
        }
        applicationScope.launch(Dispatchers.IO) {
            leftoverAttachments.forEach { folder -> folder.deleteRecursively() }
            // Versions up to 0.7.0 staged in the cache and lost the chips at every start.
            File(cacheDir, "incoming").deleteRecursively()
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
