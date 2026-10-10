package app.jonaki

import android.app.Application
import app.jonaki.core.modelcatalog.ModelCatalog
import app.jonaki.core.modelcatalog.OpenRouterEndpointCache
import app.jonaki.core.modelcatalog.OpenRouterImagePrices
import app.jonaki.core.modelcatalog.VideoModelList
import app.jonaki.core.storage.JonakiDatabase
import app.jonaki.files.AndroidFileDestinations
import app.jonaki.files.AttachmentDrafts
import app.jonaki.files.MediaStorePhotoLibrary
import app.jonaki.files.ThreadDrafts
import app.jonaki.files.ThreadImageChoices
import app.jonaki.files.MediaThumbnails
import app.jonaki.feature.gallery.GallerySource
import app.jonaki.files.IncomingShares
import app.jonaki.files.LinkedFolder
import app.jonaki.localmodels.LocalModels
import app.jonaki.files.VisibleActivity
import app.jonaki.guard.FactScreen
import app.jonaki.guard.GuardFactory
import app.jonaki.guard.GuardRecorder
import app.jonaki.memory.MemoryExport
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
import app.jonaki.run.LocalModelRuntime
import app.jonaki.run.PythonSetup
import app.jonaki.runtimes.pyodide.PyodideInstaller
import app.jonaki.run.ThreadCompactor
import app.jonaki.run.ThreadTitles
import app.jonaki.core.model.Role
import app.jonaki.core.skills.BuiltInSkill
import app.jonaki.core.skills.SkillLibrary
import app.jonaki.core.skills.SkillDownloader
import app.jonaki.core.skills.SkillProposals
import app.jonaki.skills.AssetSkills
import app.jonaki.skills.SkillImporter
import app.jonaki.skills.SkillProposalReview
import app.jonaki.settings.AccountBalances
import app.jonaki.settings.AppSettings
import app.jonaki.settings.LocalModelTools
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

    /** Runs the downloaded GGUF models with llama.cpp (D-133). */
    lateinit var localModelRuntime: LocalModelRuntime
        private set

    /** The tools a local model is offered, for Settings > Local models (D-133). */
    lateinit var localModelTools: LocalModelTools
        private set

    /** The cheapest set-up model, for memory extraction and compaction (D-036). */
    lateinit var backgroundModel: BackgroundModel
        private set

    lateinit var memoryExport: MemoryExport
        private set

    /** Each OpenRouter model's providers and prices for the Providers sheet, kept for a day. */
    val providerEndpoints: OpenRouterEndpointCache by lazy {
        OpenRouterEndpointCache(httpClient, File(cacheDir, "openrouter-endpoints"))
    }

    /** OpenRouter's image model prices, kept in one cache file for a day (shared by the picker and Settings). */
    val imagePrices: OpenRouterImagePrices by lazy {
        OpenRouterImagePrices(httpClient, File(cacheDir, "openrouter-image-prices.json"))
    }

    /** The one client every call shares; also used for OpenRouter's public image model list. */
    lateinit var httpClient: OkHttpClient
        private set

    /** Downloads and the share sheet, for the chat's Save and Share under a generated image. */
    lateinit var fileDestinations: AndroidFileDestinations
        private set

    /** OpenRouter's video model list (durations, resolutions, prices), kept in one cache file for a day. */
    val videoModelList: VideoModelList by lazy {
        VideoModelList(File(cacheDir, "openrouter-video-models.json"), httpClient)
    }

    /** Screens a fact saved after outside content, for the memory tool and for extraction. */
    lateinit var factScreen: FactScreen
        private set

    /** The skill library in files/skills/, which read_file can read as /skills/ (D-037). */
    lateinit var skillLibrary: SkillLibrary
        private set

    /** Skills the agent proposed, waiting for the user to add or discard them; kept outside the library. */
    lateinit var skillProposals: SkillProposals
        private set

    /** What the user's Add and Discard do to a proposal. */
    lateinit var skillProposalReview: SkillProposalReview
        private set

    /** Adds skills from links and picked files (D-041). */
    lateinit var skillImporter: SkillImporter
        private set

    /** Account balances for the settings cards (D-031); call refreshAll() when Settings opens. */
    lateinit var balances: AccountBalances
        private set

    /** The activity on screen, for pickers and the share sheet that share_file opens (D-045). */
    val visibleActivity = VisibleActivity()

    /** Asks for runtime permissions in the visible window; also used by Settings > Permissions (D-124). */
    lateinit var runtimePermissions: RuntimePermissions
        private set

    /** MCP servers from Settings, for the mcp tool (D-104). */
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

    /** Text typed in each thread's message box and not yet sent. */
    lateinit var threadDrafts: ThreadDrafts
        private set

    /** The image model each thread picked in its model sheet; a thread without an entry follows the starred default. */
    lateinit var threadImageChoices: ThreadImageChoices
        private set

    /** True when the launching intent was a share from another app, which picks its own screen. */
    var launchedWithShare = false

    /** Reminders the phone tool set, kept in a file until they fire (D-097). */
    lateinit var reminders: Reminders
        private set

    /** Tasks the schedule tool made, on WorkManager (D-098). */
    lateinit var scheduledTasks: ScheduledTasks
        private set

    /** Installs and removes Python for Settings > Python, the tool picker and the chat's install card (M8). */
    lateinit var python: PythonSetup
        private set

    /** Settings > Local models and its download worker (D-133). */
    lateinit var localModels: LocalModels
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
        httpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            // Streaming replies can pause while a model thinks; the agent loop's own limits apply on top.
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
        mcpServers = McpServerStore(this, secrets, File(filesDir, "mcp-tools"))
        linkedFolder = LinkedFolder(this)
        // In files/, not the cache, because Android may clear the cache while chips wait (D-112).
        attachmentDrafts = AttachmentDrafts(File(filesDir, "waiting-attachments"))
        // Read before anything is staged, so a share arriving now is not taken for a leftover.
        val leftoverAttachments = attachmentDrafts.restore()
        threadDrafts = ThreadDrafts(File(filesDir, "thread-drafts.json"))
        threadImageChoices = ThreadImageChoices(File(filesDir, "thread-image-models.json"))
        incomingShares = IncomingShares(contentResolver, attachmentDrafts, applicationScope)
        skillProposals = SkillProposals(File(filesDir, "skill-proposals"), skillExists = { name -> skillLibrary.readText(name) != null })
        skillProposalReview = SkillProposalReview(skillLibrary, skillProposals)
        skillImporter = SkillImporter(skillLibrary, SkillDownloader(httpClient))
        catalog = ModelCatalog(File(cacheDir, "openrouter-models.json"), httpClient)
        localModels = LocalModels(this, httpClient, applicationScope)
        localModelRuntime = LocalModelRuntime(localModels.store)
        backgroundModel = BackgroundModel(database, settings, secrets, httpClient, catalog, localModelRuntime)
        factScreen = FactScreen(
            guard = { GuardFactory(settings, secrets, httpClient).create() },
            recorderFor = { threadId -> GuardRecorder(threadId, database.stepDao()::appendGuardNote, backgroundModel::saveUsage) },
        )
        val memoryExtractor = MemoryExtractor(
            database = database,
            backgroundModel = backgroundModel,
            reviewMode = { settings.snapshot.value.reviewExtractedMemories },
            clock = System::currentTimeMillis,
            proposeGlobalFacts = { settings.snapshot.value.suggestFactsForAllThreads },
            holdFactsAfterOutsideContent = { settings.snapshot.value.holdFactsAfterOutsideContent },
            looksPlanted = factScreen::looksPlanted,
        )
        reminders = Reminders(this, ReminderBook(File(filesDir, "reminders.json"))) { settings.snapshot.value.reminderPolicy }
        scheduledTasks = ScheduledTasks(this, ScheduleBook(File(filesDir, "scheduled-tasks.json")))
        runtimePermissions = RuntimePermissions(this, visibleActivity, settings::recordPermissionRefusal)
        fileDestinations = AndroidFileDestinations(this, visibleActivity, linkedFolder)
        memoryExport = MemoryExport(database, fileDestinations, File(cacheDir, "memory-export"))
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
            fileDestinations,
            backgroundModel,
            AndroidPhone(this, runtimePermissions, visibleActivity, reminders),
            taskSchedulerFor = { threadId -> ThreadTaskScheduler(threadId, scheduledTasks, runtimePermissions) },
            mcpServers = mcpServers,
            localRuntime = localModelRuntime,
            skillProposals = skillProposals,
            isSkillProposalOn = { settings.snapshot.value.suggestSkills },
            factScreen = factScreen,
            threadImageChoices = threadImageChoices,
            videoModelList = videoModelList,
        )
        localModelTools = LocalModelTools(settings, runner::toolsForLocalPromptCosts)
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
            // Incognito threads go a day after their last message; the thread list checks again (D-111).
            runner.deleteExpiredIncognitoThreads()
        }
        applicationScope.launch {
            catalog.refreshIfStale()
        }
        applicationScope.launch {
            restoreNamesCutByOldVersion()
        }
        applicationScope.launch(Dispatchers.IO) {
            // A draft whose thread was deleted while the app was not watching would wait forever.
            val existingThreadIds = database.threadDao().observeAll().first().map { thread -> thread.id }.toSet()
            threadDrafts.pruneTo(existingThreadIds)
            threadImageChoices.pruneTo(existingThreadIds)
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

    /**
     * A loaded local model holds hundreds of megabytes to gigabytes, so it
     * goes when Android asks for memory back; the next local turn loads it again.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (LocalModelRuntime.shouldUnload(level)) {
            applicationScope.launch {
                localModelRuntime.unload()
            }
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
