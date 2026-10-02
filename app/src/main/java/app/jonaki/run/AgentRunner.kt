package app.jonaki.run

import app.jonaki.core.providerapi.ThinkingLevel
import app.jonaki.core.modelcatalog.ThinkingSupport
import app.jonaki.settings.ThinkingLevels
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.jonaki.ToolRegistry
import app.jonaki.ToolServices
import app.jonaki.core.agent.AgentLoop
import app.jonaki.core.agent.AgentSettings
import app.jonaki.core.agent.ApprovalDecision
import app.jonaki.core.agent.ImageMessages
import app.jonaki.core.agent.MemorySection
import app.jonaki.core.agent.PromptFact
import app.jonaki.core.agent.PermissionBroker
import app.jonaki.core.agent.PromptBuilder
import app.jonaki.core.agent.RunOutcome
import app.jonaki.core.agent.SkillSection
import app.jonaki.core.model.Role
import app.jonaki.memory.MemoryExtractor
import app.jonaki.memory.RoomMemoryStore
import app.jonaki.core.modelcatalog.CostCalculator
import app.jonaki.core.modelcatalog.ModelCatalog
import app.jonaki.core.modelcatalog.ModelKey
import app.jonaki.core.providerapi.ChatProvider
import app.jonaki.core.searchapi.SearchBackend
import app.jonaki.core.skills.SkillLibrary
import app.jonaki.core.storage.CompactionPlan
import app.jonaki.core.storage.HistoryMapper
import app.jonaki.core.storage.JonakiDatabase
import app.jonaki.core.storage.MessageEntity
import app.jonaki.core.storage.ThreadEntity
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.providers.gemini.GeminiProvider
import app.jonaki.providers.gemini.VideoSummaryOutcome
import app.jonaki.providers.gemini.VideoSummaryRequest
import app.jonaki.providers.openaicompatible.OpenRouterRouting
import app.jonaki.search.exa.ExaSearchBackend
import app.jonaki.search.ollama.OllamaSearchBackend
import app.jonaki.search.tavily.TavilySearchBackend
import app.jonaki.settings.AppSettings
import app.jonaki.settings.ChatService
import app.jonaki.settings.McpServerStore
import app.jonaki.settings.SearchService
import app.jonaki.settings.SecretName
import app.jonaki.settings.SecretStore
import app.jonaki.files.ModelImageLoader
import app.jonaki.skills.ThreadSkills
import app.jonaki.tools.sharefile.FileDestinations
import app.jonaki.tools.youtubesummarize.VideoAnswer
import app.jonaki.tools.youtubesummarize.VideoSummarizer
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Starts, stops and answers agent runs, one per thread at a time. Runs live in
 * the application scope and the foreground service keeps the process alive
 * while any run is active (D-005).
 */
class AgentRunner(
    private val context: Context,
    private val database: JonakiDatabase,
    private val settings: AppSettings,
    private val secrets: SecretStore,
    private val httpClient: OkHttpClient,
    private val catalog: ModelCatalog,
    private val scope: CoroutineScope,
    private val memoryExtractor: MemoryExtractor,
    private val threadCompactor: ThreadCompactor,
    private val skillLibrary: SkillLibrary,
    private val fileDestinations: FileDestinations,
    private val mcpServers: McpServerStore,
) {
    private val runningJobs = mutableMapOf<String, Job>()

    /** Threads the user left while a run was still going; extraction waits for the run's end. */
    private val leftWhileRunning = MutableStateFlow<Set<String>>(emptySet())

    private val running = MutableStateFlow<Set<String>>(emptySet())
    val runningThreadIds: StateFlow<Set<String>> = running.asStateFlow()

    private val approvals = MutableStateFlow<Map<String, PendingApproval>>(emptyMap())
    val pendingApprovals: StateFlow<Map<String, PendingApproval>> = approvals.asStateFlow()

    private val stepCounts = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** Steps started so far in each running thread. */
    val runStepCounts: StateFlow<Map<String, Int>> = stepCounts.asStateFlow()

    private val promptBuilder = PromptBuilder(SystemPrompt.BASE)

    /** Creates a thread and returns its id. */
    suspend fun createThread(): String {
        val now = System.currentTimeMillis()
        val thread = ThreadEntity(
            id = UUID.randomUUID().toString(),
            title = "",
            createdAtMillis = now,
            updatedAtMillis = now,
            webSearchEnabled = !settings.snapshot.value.webSearchOffInNewThreads,
            modelKey = settings.snapshot.value.chatModels.defaultModelKey,
        )
        database.threadDao().insert(thread)
        ThreadFolders.create(context, thread.id)
        return thread.id
    }

    /** Switches a thread's model; the next message uses it (D-027). */
    suspend fun setThreadModel(threadId: String, modelKey: String) {
        database.threadDao().setModelKey(threadId, modelKey)
    }

    /** The model a thread uses: its own, or the starred default for threads made before version 2. */
    fun modelKeyFor(thread: ThreadEntity?): String? =
        thread?.modelKey ?: settings.snapshot.value.chatModels.defaultModelKey

    fun send(threadId: String, text: String) {
        if (threadId in running.value || text.isBlank()) {
            return
        }
        startRun(threadId) {
            saveUserMessage(threadId, text.trim())
        }
    }

    /**
     * Replaces a sent prompt with [text]: the prompt and everything after it
     * are deleted, then the new prompt runs (D-056).
     */
    fun editAndResend(threadId: String, messageId: String, text: String) {
        if (threadId in running.value || text.isBlank()) {
            return
        }
        startRun(threadId) {
            deleteFromMessage(threadId, messageId)
            saveUserMessage(threadId, text.trim())
        }
    }

    private suspend fun deleteFromMessage(threadId: String, messageId: String) {
        val messageDao = database.messageDao()
        val rows = messageDao.listThread(threadId)
        val edited = rows.firstOrNull { row -> row.id == messageId && row.role == Role.USER.name } ?: return
        val removed = rows.filter { row -> row.position >= edited.position }
        val toolCallIds = removed.flatMap { row -> HistoryMapper.toolCallsFromJson(row.toolCallsJson) }.map { call -> call.id }
        if (toolCallIds.isNotEmpty()) {
            database.stepDao().deleteAll(toolCallIds)
        }
        database.compactionDao().deleteCoveringFrom(threadId, edited.position)
        messageDao.deleteFrom(threadId, edited.position)
        // Extraction must not skip the new messages that take the removed positions.
        val extractedUpTo = database.threadDao().find(threadId)?.memoryExtractedUpToPosition
        if (extractedUpTo != null && extractedUpTo >= edited.position) {
            database.threadDao().setMemoryExtractedUpTo(threadId, edited.position - 1)
        }
    }

    /** Runs again on the saved history, after a failed answer. */
    fun retry(threadId: String) {
        if (threadId in running.value) {
            return
        }
        startRun(threadId) {}
    }

    /** The thread's folder, made if missing; attachments move into its inbox/ before a message is sent. */
    fun threadFolder(threadId: String): java.io.File = ThreadFolders.create(context, threadId)

    suspend fun deleteThread(threadId: String) {
        stop(threadId)
        database.threadDao().delete(threadId)
        ThreadFolders.delete(context, threadId)
    }

    /** A thinking level picked for one thread in the model sheet; null follows the model (D-057). */
    suspend fun setThreadThinking(threadId: String, level: ThinkingLevel?) {
        database.threadDao().setThinkingLevel(threadId, level?.name)
    }

    suspend fun setWebSearchEnabled(threadId: String, enabled: Boolean) {
        database.threadDao().setWebSearchEnabled(threadId, enabled)
    }

    fun stop(threadId: String) {
        runningJobs[threadId]?.cancel()
    }

    /**
     * The user left a thread's chat: background extraction reads what the
     * thread holds (D-009), now or, while a run is going, when it ends.
     */
    fun threadLeft(threadId: String) {
        if (threadId in running.value) {
            leftWhileRunning.update { current -> current + threadId }
            return
        }
        launchExtraction(threadId, MemoryExtractor.MESSAGES_FOR_EXTRACTION_ON_LEAVE)
    }

    private fun extractMemoryAfterRun(threadId: String) {
        val wasLeft = threadId in leftWhileRunning.value
        leftWhileRunning.update { current -> current - threadId }
        val minimumNewMessages = if (wasLeft) {
            MemoryExtractor.MESSAGES_FOR_EXTRACTION_ON_LEAVE
        } else {
            MemoryExtractor.MESSAGES_PER_EXTRACTION
        }
        launchExtraction(threadId, minimumNewMessages)
    }

    /** Its own coroutine, so the thread stops showing as running while extraction works. */
    private fun launchExtraction(threadId: String, minimumNewMessages: Int) {
        scope.launch {
            val thread = database.threadDao().find(threadId) ?: return@launch
            memoryExtractor.extractIfDue(threadId, modelKeyFor(thread), minimumNewMessages)
        }
    }

    /** Its own coroutine, like extraction, so the next message is not held up by the summary. */
    private fun launchCompaction(threadId: String) {
        scope.launch {
            val thread = database.threadDao().find(threadId) ?: return@launch
            threadCompactor.compactIfDue(threadId, modelKeyFor(thread))
        }
    }

    fun answerApproval(threadId: String, decision: ApprovalDecision) {
        val pending = approvals.value[threadId] ?: return
        approvals.update { current -> current - threadId }
        pending.answer.complete(decision)
    }

    private fun startRun(threadId: String, beforeRun: suspend () -> Unit) {
        running.update { current -> current + threadId }
        stepCounts.update { current -> current - threadId }
        AgentService.start(context)
        val job = scope.launch {
            try {
                beforeRun()
                runWithOneRetry(threadId)
                extractMemoryAfterRun(threadId)
                launchCompaction(threadId)
            } finally {
                approvals.update { current -> current - threadId }
                runningJobs.remove(threadId)
                running.update { current -> current - threadId }
            }
        }
        runningJobs[threadId] = job
    }

    private suspend fun saveUserMessage(threadId: String, text: String) {
        val threadDao = database.threadDao()
        val thread = threadDao.find(threadId) ?: return
        if (thread.title.isBlank()) {
            threadDao.rename(threadId, titleFrom(text))
        }
        val messageDao = database.messageDao()
        messageDao.upsert(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                threadId = threadId,
                position = messageDao.nextPosition(threadId),
                role = Role.USER.name,
                // The time goes into the message, not the system prompt, so the prompt cache holds (D-005).
                text = promptBuilder.userMessageWithContext(text, ZonedDateTime.now()),
                toolCallsJson = "[]",
                toolCallId = null,
                isComplete = true,
                createdAtMillis = System.currentTimeMillis(),
            ),
        )
        threadDao.touch(threadId, System.currentTimeMillis())
    }

    private suspend fun runWithOneRetry(threadId: String) {
        val firstOutcome = runOnce(threadId) ?: return
        val shouldRetry = firstOutcome is RunOutcome.ProviderFailed && firstOutcome.retryable
        // Overload errors such as Gemini's 503 usually pass on a second try.
        val finalOutcome = if (shouldRetry) {
            delay(RETRY_DELAY_MILLIS)
            runOnce(threadId) ?: return
        } else {
            firstOutcome
        }
        if (finalOutcome is RunOutcome.ProviderFailed) {
            saveError(threadId, finalOutcome.message)
        }
    }

    /** Runs the loop once; null when the run could not start (an error row explains why). */
    private suspend fun runOnce(threadId: String): RunOutcome? {
        val thread = database.threadDao().find(threadId) ?: return null
        val snapshot = settings.snapshot.value
        val modelKey = modelKeyFor(thread)
        val service = modelKey?.let { ChatService.byKey(ModelKey.serviceOf(it)) }
        if (modelKey == null || service == null) {
            saveError(threadId, NO_MODEL_ERROR)
            return null
        }
        val session = RunSession(
            threadId = threadId,
            database = database,
            clock = System::currentTimeMillis,
            onApprovalNeeded = { pending -> approvals.update { current -> current + (threadId to pending) } },
            onStepStarted = {
                stepCounts.update { current -> current + (threadId to (current[threadId] ?: 0) + 1) }
            },
            modelKey = modelKey,
            priceOf = { usage -> CostCalculator.costUsd(usage, catalog.find(modelKey)) },
        )
        val routing = snapshot.routing.effectiveFor(modelKey)
        val provider = chatProvider(service, routing, onRoutingFallback = session::markRoutingFallback)
        if (provider == null) {
            saveError(threadId, "No ${service.displayName} API key. Add one in Settings.")
            return null
        }
        // Unknown models count as not taking images (D-049).
        val modelAcceptsImages = catalog.find(modelKey)?.acceptsImages == true
        val threadFolder = ThreadFolders.create(context, threadId)
        val tools = ToolRegistry.tools(
            ToolServices(
                searchBackends = searchBackends(snapshot.searchOrder),
                videoSummarizer = videoSummarizer(),
                webAccessEnabled = thread.webSearchEnabled,
                memoryStore = RoomMemoryStore(database, threadId, System::currentTimeMillis),
                fileDestinations = fileDestinations,
                modelAcceptsImages = modelAcceptsImages,
                mcpServers = mcpServers.forTools(),
                mcpToolListFolder = mcpServers.toolListFolder,
            ),
        )
        val allowedForThread = thread.toolsAllowedForThread.split(",").filter { it.isNotBlank() }.toSet()
        val permissionBroker = PermissionBroker(session, allowedForThread)
        val loop = AgentLoop(
            provider = provider,
            tools = tools,
            toolContext = ToolContext(threadFolder, httpClient, skillLibrary.folder),
            permissionBroker = permissionBroker,
            recorder = session,
            settings = AgentSettings(
                model = ModelKey.modelOf(modelKey),
                thinkingLevel = ThinkingLevels.effective(
                    threadLevel = thread.thinkingLevel,
                    modelLevel = snapshot.thinkingLevels[modelKey],
                    isSupported = ThinkingSupport.isSupported(modelKey, catalog.find(modelKey)),
                ),
                systemPrompt = promptBuilder.systemPrompt(
                    activeTools = tools,
                    memorySection = memorySectionFor(threadId),
                    skillSection = skillSectionFor(thread),
                ),
            ),
            imageMessages = ImageMessages(
                ModelImageLoader(threadFolder, ThreadFolders.imageCache(context, threadId)),
                modelAcceptsImages,
            ),
        )
        val summary = database.compactionDao().latestForThread(threadId)
        val history = CompactionPlan.historyAfter(
            rows = database.messageDao().listThread(threadId),
            summaryText = summary?.summaryText,
            upToPosition = summary?.upToPosition,
        )
        try {
            return loop.run(history)
        } finally {
            database.threadDao().setToolsAllowedForThread(threadId, permissionBroker.toolsAllowedForThread.joinToString(","))
        }
    }

    /**
     * Global and thread facts for the system prompt, built once per run so
     * the prompt stays the same for every request of the run (D-005). Facts
     * the memory tool saves during the run reach the next run.
     */
    private suspend fun memorySectionFor(threadId: String): String {
        val memoryDao = database.memoryDao()
        val facts = memoryDao.listVisibleFrom(threadId).map { memory ->
            PromptFact(
                id = memory.id,
                text = memory.text,
                isGlobal = memory.threadId == null,
                pinned = memory.pinned,
                lastUsedAtMillis = memory.lastUsedAtMillis,
            )
        }
        val section = MemorySection.build(facts)
        if (section.includedIds.isNotEmpty()) {
            // Safe for the cache: the section is chosen by use time but written in id order (D-035).
            memoryDao.markUsed(section.includedIds, System.currentTimeMillis())
        }
        return section.text
    }

    /**
     * The thread's enabled skills, read from the library once per run, so
     * the prompt stays the same for every request of the run (D-005). A
     * skill edited or imported during a run reaches the next run.
     */
    private suspend fun skillSectionFor(thread: ThreadEntity): String {
        val entries = withContext(Dispatchers.IO) { skillLibrary.list() }
        return SkillSection.build(ThreadSkills.forPrompt(entries, thread.disabledSkills))
    }

    /** Switches one skill on or off for one thread; the next run's prompt follows (D-040). */
    suspend fun setSkillEnabled(threadId: String, skillName: String, enabled: Boolean) {
        val thread = database.threadDao().find(threadId) ?: return
        database.threadDao().setDisabledSkills(threadId, ThreadSkills.withSkill(skillName, enabled, thread.disabledSkills))
    }

    private suspend fun saveError(threadId: String, message: String) {
        val messageDao = database.messageDao()
        messageDao.upsert(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                threadId = threadId,
                position = messageDao.nextPosition(threadId),
                role = HistoryMapper.ERROR_ROLE,
                text = message,
                toolCallsJson = "[]",
                toolCallId = null,
                isComplete = true,
                createdAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    /** Null when the service needs a key and none is saved. */
    private fun chatProvider(service: ChatService, routing: OpenRouterRouting, onRoutingFallback: () -> Unit): ChatProvider? {
        val secret = service.secret
        val key = if (secret == null) null else secrets.read(secret) ?: return null
        return ChatProviders.create(service, key, httpClient, routing, onRoutingFallback)
    }

    private fun searchBackends(order: List<SearchService>): List<SearchBackend> =
        order.mapNotNull { service ->
            val key = secrets.read(service.secret) ?: return@mapNotNull null
            when (service) {
                SearchService.TAVILY -> TavilySearchBackend(key, httpClient)
                SearchService.OLLAMA -> OllamaSearchBackend(key, httpClient)
                SearchService.EXA -> ExaSearchBackend(key, httpClient)
            }
        }

    private fun videoSummarizer(): VideoSummarizer? {
        val key = secrets.read(SecretName.GEMINI) ?: return null
        val gemini = GeminiProvider(key, httpClient)
        return VideoSummarizer { request ->
            val outcome = gemini.summarizeVideo(
                VideoSummaryRequest(
                    videoUrl = request.videoUrl,
                    prompt = request.prompt,
                    startSeconds = request.startSeconds,
                    endSeconds = request.endSeconds,
                    lowMediaResolution = request.lowMediaResolution,
                ),
            )
            when (outcome) {
                is VideoSummaryOutcome.Success -> VideoAnswer.Success(outcome.text, outcome.usage?.inputTokens)
                is VideoSummaryOutcome.Failed -> VideoAnswer.Failed(outcome.message, outcome.retryable)
            }
        }
    }

    private fun titleFrom(text: String): String = ThreadTitles.fromMessage(text)

    private companion object {
        const val RETRY_DELAY_MILLIS = 2_000L
        const val NO_MODEL_ERROR = "No model. Add one in Settings."
    }
}

/** Each thread's folder (D-008): inbox/ for files that come in, work/ and artifacts/. */
object ThreadFolders {
    fun create(context: Context, threadId: String): java.io.File {
        val folder = java.io.File(context.filesDir, "threads/$threadId")
        for (child in listOf("inbox", "work", "artifacts")) {
            java.io.File(folder, child).mkdirs()
        }
        return folder
    }

    fun delete(context: Context, threadId: String) {
        java.io.File(context.filesDir, "threads/$threadId").deleteRecursively()
        imageCache(context, threadId).deleteRecursively()
    }

    /**
     * Shrunk copies of the thread's images as sent to the model (D-049).
     * Outside the thread folder, so find_files does not list them.
     */
    fun imageCache(context: Context, threadId: String): java.io.File =
        java.io.File(context.filesDir, "image-cache/$threadId")
}

internal fun Context.startForegroundServiceCompat(intent: Intent) {
    ContextCompat.startForegroundService(this, intent)
}
