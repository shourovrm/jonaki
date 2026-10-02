package app.jonaki.run

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.jonaki.ToolRegistry
import app.jonaki.ToolServices
import app.jonaki.core.agent.AgentLoop
import app.jonaki.core.agent.AgentSettings
import app.jonaki.core.agent.ApprovalDecision
import app.jonaki.core.agent.PermissionBroker
import app.jonaki.core.agent.PromptBuilder
import app.jonaki.core.agent.RunOutcome
import app.jonaki.core.model.Role
import app.jonaki.core.providerapi.ChatProvider
import app.jonaki.core.searchapi.SearchBackend
import app.jonaki.core.storage.HistoryMapper
import app.jonaki.core.storage.JonakiDatabase
import app.jonaki.core.storage.MessageEntity
import app.jonaki.core.storage.ThreadEntity
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.providers.gemini.GeminiProvider
import app.jonaki.providers.gemini.VideoSummaryOutcome
import app.jonaki.providers.gemini.VideoSummaryRequest
import app.jonaki.providers.openaicompatible.OpenAiCompatibleProvider
import app.jonaki.providers.openaicompatible.ProviderPresets
import app.jonaki.search.exa.ExaSearchBackend
import app.jonaki.search.ollama.OllamaSearchBackend
import app.jonaki.search.tavily.TavilySearchBackend
import app.jonaki.settings.AppSettings
import app.jonaki.settings.ChatService
import app.jonaki.settings.SearchService
import app.jonaki.settings.SecretName
import app.jonaki.settings.SecretStore
import app.jonaki.tools.youtubesummarize.VideoAnswer
import app.jonaki.tools.youtubesummarize.VideoSummarizer
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val scope: CoroutineScope,
) {
    private val runningJobs = mutableMapOf<String, Job>()

    private val running = MutableStateFlow<Set<String>>(emptySet())
    val runningThreadIds: StateFlow<Set<String>> = running.asStateFlow()

    private val approvals = MutableStateFlow<Map<String, PendingApproval>>(emptyMap())
    val pendingApprovals: StateFlow<Map<String, PendingApproval>> = approvals.asStateFlow()

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
        )
        database.threadDao().insert(thread)
        ThreadFolders.create(context, thread.id)
        return thread.id
    }

    fun send(threadId: String, text: String) {
        if (threadId in running.value || text.isBlank()) {
            return
        }
        startRun(threadId) {
            saveUserMessage(threadId, text.trim())
        }
    }

    /** Runs again on the saved history, after a failed answer. */
    fun retry(threadId: String) {
        if (threadId in running.value) {
            return
        }
        startRun(threadId) {}
    }

    fun stop(threadId: String) {
        runningJobs[threadId]?.cancel()
    }

    fun answerApproval(threadId: String, decision: ApprovalDecision) {
        val pending = approvals.value[threadId] ?: return
        approvals.update { current -> current - threadId }
        pending.answer.complete(decision)
    }

    private fun startRun(threadId: String, beforeRun: suspend () -> Unit) {
        running.update { current -> current + threadId }
        AgentService.start(context)
        val job = scope.launch {
            try {
                beforeRun()
                runWithOneRetry(threadId)
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
        val provider = chatProvider(snapshot.chatService)
        if (provider == null) {
            saveError(threadId, MISSING_KEY_ERROR)
            return null
        }
        val tools = ToolRegistry.tools(
            ToolServices(
                searchBackends = searchBackends(snapshot.searchOrder),
                videoSummarizer = videoSummarizer(),
                webAccessEnabled = thread.webSearchEnabled,
            ),
        )
        val session = RunSession(threadId, database, System::currentTimeMillis) { pending ->
            approvals.update { current -> current + (threadId to pending) }
        }
        val allowedForThread = thread.toolsAllowedForThread.split(",").filter { it.isNotBlank() }.toSet()
        val permissionBroker = PermissionBroker(session, allowedForThread)
        val loop = AgentLoop(
            provider = provider,
            tools = tools,
            toolContext = ToolContext(ThreadFolders.create(context, threadId), httpClient),
            permissionBroker = permissionBroker,
            recorder = session,
            settings = AgentSettings(
                model = modelFor(snapshot.chatService),
                systemPrompt = promptBuilder.systemPrompt(tools),
            ),
        )
        val history = HistoryMapper.toHistory(database.messageDao().listThread(threadId))
        try {
            return loop.run(history)
        } finally {
            database.threadDao().setToolsAllowedForThread(threadId, permissionBroker.toolsAllowedForThread.joinToString(","))
        }
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

    private fun chatProvider(service: ChatService): ChatProvider? {
        val key = secrets.read(service.secret) ?: return null
        return OpenAiCompatibleProvider(presetFor(service), key, httpClient)
    }

    private fun presetFor(service: ChatService) = when (service) {
        ChatService.OPENROUTER -> ProviderPresets.openRouter
        ChatService.DEEPSEEK -> ProviderPresets.deepSeek
    }

    fun modelFor(service: ChatService): String {
        val chosen = settings.snapshot.value.modelByService[service].orEmpty()
        return chosen.ifBlank { presetFor(service).defaultModel }
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

    private fun titleFrom(text: String): String {
        val firstLine = text.lineSequence().first().trim()
        if (firstLine.length <= TITLE_LENGTH) {
            return firstLine
        }
        return firstLine.take(TITLE_LENGTH).substringBeforeLast(' ').trimEnd() + "…"
    }

    private companion object {
        const val RETRY_DELAY_MILLIS = 2_000L
        const val TITLE_LENGTH = 40
        const val MISSING_KEY_ERROR = "No API key for the chat service. Add one in Settings."
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
    }
}

internal fun Context.startForegroundServiceCompat(intent: Intent) {
    ContextCompat.startForegroundService(this, intent)
}
