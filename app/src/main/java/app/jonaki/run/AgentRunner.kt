package app.jonaki.run

import app.jonaki.guard.FactScreen
import app.jonaki.guard.GuardFactory
import app.jonaki.guard.GuardRecorder
import app.jonaki.guard.RecordingGuard
import android.os.SystemClock
import app.jonaki.R
import app.jonaki.core.providerapi.ThinkingLevel

import app.jonaki.core.modelcatalog.ThinkingSupport
import app.jonaki.settings.ThinkingLevels
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.jonaki.ToolRegistry
import app.jonaki.ToolServices
import app.jonaki.core.agent.AgentLoop
import app.jonaki.core.agent.ContextBreakdown
import app.jonaki.core.agent.PromptSkill
import app.jonaki.core.model.ImagePart
import app.jonaki.core.model.Message
import app.jonaki.core.toolapi.SubagentLauncher
import app.jonaki.core.toolapi.SubagentLimitSettings
import app.jonaki.core.toolapi.ThreadPaths
import app.jonaki.core.toolapi.SubagentModelInfo
import app.jonaki.core.toolapi.SubagentReport
import app.jonaki.core.toolapi.SubagentTask
import app.jonaki.core.toolapi.SubagentTypeInfo
import app.jonaki.core.agent.AgentSettings
import app.jonaki.core.agent.ApprovalDecision
import app.jonaki.core.agent.ApprovalMode
import app.jonaki.core.agent.ImageMessages
import app.jonaki.core.agent.MemorySection
import app.jonaki.core.agent.ParentAnswer
import app.jonaki.core.agent.ParentAsker
import app.jonaki.core.agent.ParentQuestion
import app.jonaki.core.agent.SubagentRunner
import app.jonaki.core.agent.ToolDefinitions
import app.jonaki.core.providerapi.ToolDefinition
import app.jonaki.core.agent.PromptFact
import app.jonaki.core.agent.ProjectSection
import app.jonaki.core.agent.ProjectFilesSection
import app.jonaki.core.agent.MemoryBudget
import app.jonaki.core.agent.PermissionBroker
import app.jonaki.core.agent.ThreadApprovalState
import app.jonaki.core.agent.PromptBuilder
import app.jonaki.core.agent.RunOutcome
import app.jonaki.core.agent.SkillSection
import app.jonaki.core.agent.InstructionsSection
import app.jonaki.core.agent.PromptPersona
import app.jonaki.settings.AnswerStyles
import app.jonaki.settings.SettingsSnapshot
import app.jonaki.core.model.Role
import app.jonaki.memory.MemoryExtractor
import app.jonaki.memory.PromptFacts
import app.jonaki.memory.RoomMemoryStore
import app.jonaki.memory.ThreadMemory
import app.jonaki.search.RoomChatSearchStore
import app.jonaki.core.modelcatalog.CostCalculator
import app.jonaki.core.modelcatalog.ModelCatalog
import app.jonaki.core.modelcatalog.ModelKey
import app.jonaki.core.providerapi.ChatProvider
import app.jonaki.core.searchapi.SearchBackend
import app.jonaki.core.skills.SkillLibrary
import app.jonaki.core.skills.SkillProposals
import app.jonaki.core.storage.CompactionPlan
import app.jonaki.core.storage.HistoryMapper
import app.jonaki.core.storage.JonakiDatabase
import app.jonaki.core.storage.MessageEntity
import app.jonaki.core.storage.ThreadEntity
import app.jonaki.core.toolapi.ImageGenerator
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.providers.gemini.GeminiProvider
import app.jonaki.providers.gemini.VideoSummaryOutcome
import app.jonaki.providers.gemini.VideoSummaryRequest
import app.jonaki.providers.openaicompatible.OpenRouterImageGenerator
import app.jonaki.providers.openaicompatible.OpenRouterRoute
import app.jonaki.search.exa.ExaSearchBackend
import app.jonaki.search.ollama.OllamaSearchBackend
import app.jonaki.search.tavily.TavilySearchBackend
import app.jonaki.feature.settings.ApprovalRuleChoiceUi
import app.jonaki.settings.AppSettings
import app.jonaki.settings.ApprovalRuleChoices
import app.jonaki.settings.ApprovalModes
import app.jonaki.settings.ChatService
import app.jonaki.settings.CustomSubagents
import app.jonaki.settings.McpServerStore
import app.jonaki.settings.LocalModelToolList
import app.jonaki.settings.SearchService
import app.jonaki.settings.SecretName
import app.jonaki.settings.SecretStore
import app.jonaki.files.ModelImageLoader
import app.jonaki.skills.LibrarySkillProposalSink
import app.jonaki.skills.ThreadSkills
import app.jonaki.tools.phone.Phone
import app.jonaki.tools.schedule.TaskScheduler
import app.jonaki.tools.sharefile.FileDestinations
import app.jonaki.tools.youtubesummarize.VideoAnswer
import app.jonaki.tools.youtubesummarize.VideoSummarizer
import app.jonaki.web.WebViewPageRenderer
import app.jonaki.web.WebViewPdfRenderer
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
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
    /** Answers subagents' ask_parent and saves their model calls' usage (M7). */
    private val backgroundModel: BackgroundModel,
    /** Calendar, reminders, notifications, clipboard and apps for the phone tool (D-020). */
    private val phone: Phone? = null,
    /** The schedule tool's tasks, seen from one thread (plan M9). */
    private val taskSchedulerFor: ((threadId: String) -> TaskScheduler)? = null,
    private val mcpServers: McpServerStore,
    /** GGUF models on the phone and their one provider (D-133). */
    private val localRuntime: LocalModelRuntime,
    /** Where the agent's skill proposals wait for the user; null leaves propose_skill out. */
    private val skillProposals: SkillProposals? = null,
    /** Read for every run: false leaves propose_skill out, so a Settings switch applies from the next message. */
    private val isSkillProposalOn: () -> Boolean = { true },
    /** Screens a fact the memory tool saves after outside content; null holds every such fact. */
    private val factScreen: FactScreen? = null,
) {
    private val runningJobs = mutableMapOf<String, Job>()

    /** The subagent runner of each running thread, so that one subagent can be stopped alone (D-126). */
    private val subagentRunners = ConcurrentHashMap<String, SubagentRunner>()

    /**
     * What the user last asked in each thread, for the guard to compare an
     * action with. Empty after a restart or a retry, and then the guard has
     * nothing to match, so the card is shown.
     */
    private val latestUserText = ConcurrentHashMap<String, String>()

    /** The first-line name given to each new thread, until its first answer has been used to write a short one. */
    private val provisionalTitles = ConcurrentHashMap<String, String>()

    private val threadNamer = ThreadNamer(
        ask = { threadId, threadModelKey, systemPrompt, userText, maxOutputTokens ->
            backgroundModel.complete(threadId, threadModelKey, systemPrompt, userText, maxOutputTokens)
        },
        renameIfUnchanged = { threadId, expectedTitle, newTitle ->
            val threadDao = database.threadDao()
            val unchanged = threadDao.find(threadId)?.title == expectedTitle
            if (unchanged) {
                threadDao.rename(threadId, newTitle)
            }
            unchanged
        },
        logFailure = { message -> android.util.Log.w("ThreadNamer", message) },
    )

    /** Threads the user left while a run was still going; extraction waits for the run's end. */
    private val leftWhileRunning = MutableStateFlow<Set<String>>(emptySet())

    private val running = MutableStateFlow<Set<String>>(emptySet())
    val runningThreadIds: StateFlow<Set<String>> = running.asStateFlow()

    /** Cards waiting per thread, oldest first; parallel subagents can ask at the same time (M7). */
    private val approvals = MutableStateFlow<Map<String, List<PendingApproval>>>(emptyMap())
    val pendingApprovals: StateFlow<Map<String, List<PendingApproval>>> = approvals.asStateFlow()

    /**
     * Messages sent while a thread's run was going, oldest first. They live in
     * memory only: each is saved to the database when the loop takes it.
     */
    private val queues = MutableStateFlow<Map<String, List<QueuedMessage>>>(emptyMap())
    val queuedMessages: StateFlow<Map<String, List<QueuedMessage>>> = queues.asStateFlow()

    /** Queued text a stopped or failed run handed back, waiting for the chat to put it in its field. */
    private val handedBack = MutableStateFlow<Map<String, String>>(emptyMap())
    val handedBackText: StateFlow<Map<String, String>> = handedBack.asStateFlow()

    /**
     * Held while a run starts, while a message is queued and while a run ends,
     * so a message sent at the moment a run ends is either taken by that run's
     * follow-up or handed back, never lost.
     */
    private val runStateLock = Any()

    private val stepCounts = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** Steps started so far in each running thread. */
    val runStepCounts: StateFlow<Map<String, Int>> = stepCounts.asStateFlow()

    private val promptBuilder = PromptBuilder(SystemPrompt.BASE)

    /** One for the app, because it lets only one page render at a time (D-131). */
    private val pageRenderer = WebViewPageRenderer(context)
    private val pdfRenderer = WebViewPdfRenderer(context)

    /**
     * Each running thread's own approval mode name, "" when it follows
     * Settings. The broker reads it before every tool call, so a mode picked
     * in the chat during a run applies from the next call (D-058).
     */
    private val threadApprovalModes = ConcurrentHashMap<String, String>()

    /**
     * The "Allow all in this thread" answer and the outside-content fact of each
     * running thread. The broker reads and writes them during a run, and the chat's
     * approval chip withdraws the answer through the same object.
     */
    private val threadApprovalStates = ConcurrentHashMap<String, ThreadApprovalState>()

    /**
     * Creates a thread and returns its id. A thread made inside a project
     * starts with the project's model when it has one (D-110).
     */
    suspend fun createThread(projectId: String? = null, incognito: Boolean = false): String {
        val now = System.currentTimeMillis()
        val project = projectId?.let { id -> database.projectDao().find(id) }
        val thread = ThreadEntity(
            id = UUID.randomUUID().toString(),
            title = "",
            createdAtMillis = now,
            updatedAtMillis = now,
            webSearchEnabled = !settings.snapshot.value.webSearchOffInNewThreads,
            modelKey = project?.modelKey ?: settings.snapshot.value.chatModels.defaultModelKey,
            projectId = project?.id,
            incognito = incognito,
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

    /** Starts a run, or queues the message when the thread's run is going (it reaches the model at the next safe point). */
    fun send(threadId: String, text: String) {
        if (text.isBlank()) {
            return
        }
        synchronized(runStateLock) {
            if (threadId in running.value) {
                enqueue(threadId, text.trim())
                return
            }
            startRun(threadId) {
                saveUserMessage(threadId, text.trim())
            }
        }
    }

    /** Like [send], for a scheduled task: false when the thread is busy, so the caller can wait and try again. */
    fun sendIfIdle(threadId: String, text: String): Boolean {
        if (text.isBlank()) {
            return false
        }
        synchronized(runStateLock) {
            if (threadId in running.value) {
                return false
            }
            startRun(threadId) {
                saveUserMessage(threadId, text.trim())
            }
            return true
        }
    }

    private fun enqueue(threadId: String, text: String) {
        val queued = QueuedMessage(id = UUID.randomUUID().toString(), text = text)
        queues.update { current -> current + (threadId to current[threadId].orEmpty() + queued) }
    }

    /** The user cancelled one queued message; it is dropped and nothing goes back to the field. */
    fun cancelQueued(threadId: String, queuedId: String) {
        queues.update { current ->
            val remaining = current[threadId].orEmpty().filter { queued -> queued.id != queuedId }
            if (remaining.isEmpty()) current - threadId else current + (threadId to remaining)
        }
    }

    /**
     * Takes one queued message out so the user can edit it in the field; they
     * send it again when done, which puts it at the end of the queue. Null
     * when the agent took the message in the meantime.
     */
    fun takeQueuedForEdit(threadId: String, queuedId: String): String? {
        val before = queues.getAndUpdate { current ->
            val remaining = current[threadId].orEmpty().filter { queued -> queued.id != queuedId }
            if (remaining.isEmpty()) current - threadId else current + (threadId to remaining)
        }
        return before[threadId].orEmpty().firstOrNull { queued -> queued.id == queuedId }?.text
    }

    /** The chat put the handed-back text into its field. */
    fun takeHandedBackText(threadId: String): String? {
        val text = handedBack.value[threadId] ?: return null
        handedBack.update { current -> current - threadId }
        return text
    }

    /**
     * The agent loop's safe point: saves each waiting message as a user
     * message, in order, and returns them for the next request. Saving here,
     * not on send, keeps the chat in the order the model saw.
     */
    private suspend fun deliverQueuedMessages(threadId: String): List<Message> {
        val queuedTexts = takeQueuedText(threadId)
        if (queuedTexts.isNotEmpty()) {
            subagentRunners[threadId]?.userMessageArrived()
        }
        return queuedTexts.map { text -> Message(Role.USER, saveUserMessage(threadId, text)) }
    }

    /** Empties the thread's queue in one step, so no message is taken twice. */
    private fun takeQueuedText(threadId: String): List<String> =
        queues.getAndUpdate { current -> current - threadId }[threadId].orEmpty().map { queued -> queued.text }

    /**
     * Replaces a sent prompt with [text]: the prompt and everything after it
     * are deleted, then the new prompt runs (D-056).
     */
    fun editAndResend(threadId: String, messageId: String, text: String) {
        if (text.isBlank()) {
            return
        }
        synchronized(runStateLock) {
            if (threadId in running.value) {
                return
            }
            startRun(threadId) {
                deleteFromMessage(threadId, messageId)
                saveUserMessage(threadId, text.trim())
            }
        }
    }

    private suspend fun deleteFromMessage(threadId: String, messageId: String) {
        val messageDao = database.messageDao()
        val rows = messageDao.listThread(threadId)
        val edited = rows.firstOrNull { row -> row.id == messageId && row.role == Role.USER.name } ?: return
        val removed = rows.filter { row -> row.position >= edited.position }
        val toolCallIds = removed.flatMap { row -> HistoryMapper.toolCallsFromJson(row.toolCallsJson) }.map { call -> call.id }
        if (toolCallIds.isNotEmpty()) {
            // Subagents' steps first: the query finds them through their subagent rows.
            database.stepDao().deleteOfSubagentsUnder(toolCallIds)
            database.subagentDao().deleteUnder(toolCallIds)
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
        synchronized(runStateLock) {
            if (threadId in running.value) {
                return
            }
            startRun(threadId) {}
        }
    }

    /** The thread's folder, made if missing; attachments move into its inbox/ before a message is sent. */
    fun threadFolder(threadId: String): java.io.File = ThreadFolders.create(context, threadId)

    suspend fun deleteThread(threadId: String) {
        // Waits for the run to end, so its queue is handed back before it is cleared below.
        runningJobs[threadId]?.cancelAndJoin()
        queues.update { current -> current - threadId }
        handedBack.update { current -> current - threadId }
        database.threadDao().delete(threadId)
        ThreadFolders.delete(context, threadId)
    }

    private val incognitoCleanup = IncognitoCleanup(
        listIncognito = { database.threadDao().listIncognitoActivity() },
        isRunning = { threadId -> threadId in running.value },
        deleteThread = ::deleteThread,
        clock = System::currentTimeMillis,
    )

    /** Deletes incognito threads whose last message is a day old (D-111). */
    suspend fun deleteExpiredIncognitoThreads() {
        incognitoCleanup.deleteExpired()
    }

    /** "Keep as a regular thread": the thread stays, and only later messages reach memory (D-111). */
    suspend fun keepIncognitoThread(threadId: String) {
        database.threadDao().keepIncognito(threadId)
    }

    /** A thinking level picked for one thread in the model sheet; null follows the model (D-057). */
    suspend fun setThreadThinking(threadId: String, level: ThinkingLevel?) {
        database.threadDao().setThinkingLevel(threadId, level?.name)
    }

    /** A thread's own approval mode; null follows the default in Settings (D-058). */
    suspend fun setThreadApprovalMode(threadId: String, mode: ApprovalMode?) {
        database.threadDao().setApprovalMode(threadId, mode?.name)
        threadApprovalModes[threadId] = mode?.name.orEmpty()
    }

    /** The mode a thread's tools ask with: its own, else the default from Settings. */
    /**
     * The chip's "withdraw" row: every tool asks again by the mode, from the next call. A
     * running thread's state is changed in place; an idle thread's row is written directly.
     */
    suspend fun withdrawAllowAll(threadId: String) {
        val liveState = threadApprovalStates[threadId]
        if (liveState != null) {
            liveState.withdrawAllowAll()
            return
        }
        database.threadDao().setAllowAllInThread(threadId, false)
    }

    fun approvalModeFor(thread: ThreadEntity?): ApprovalMode =
        ApprovalModes.effective(thread?.approvalMode, settings.snapshot.value.defaultApprovalMode)

    private fun currentApprovalMode(threadId: String): ApprovalMode =
        ApprovalModes.effective(threadApprovalModes[threadId], settings.snapshot.value.defaultApprovalMode)

    suspend fun setWebSearchEnabled(threadId: String, enabled: Boolean) {
        database.threadDao().setWebSearchEnabled(threadId, enabled)
    }

    fun stop(threadId: String) {
        runningJobs[threadId]?.cancel()
    }

    /** Stops one subagent of the thread's run; the run and the other subagents go on (D-126). */
    fun stopSubagent(threadId: String, subagentId: String) {
        subagentRunners[threadId]?.stop(subagentId)
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
            if (!ThreadMemory.isOn(thread) || !settings.snapshot.value.saveFactsFromChats) {
                return@launch
            }
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

    /**
     * Its own coroutine, like extraction. One try per thread: the entry is taken
     * out at once, so a failure is not retried after the next answer. An
     * incognito thread keeps its first-line name. A thread on a local model is
     * named like extraction and compaction treat it: the background model is
     * the cheapest cloud model the user set up, else the local one itself.
     */
    private fun launchNaming(threadId: String) {
        val provisionalTitle = provisionalTitles.remove(threadId) ?: return
        scope.launch {
            val thread = database.threadDao().find(threadId) ?: return@launch
            if (thread.incognito) {
                return@launch
            }
            val firstExchange = ThreadNamer.firstExchange(database.messageDao().listThread(threadId)) ?: return@launch
            threadNamer.nameAfterFirstAnswer(
                threadId = threadId,
                threadModelKey = modelKeyFor(thread),
                provisionalTitle = provisionalTitle,
                firstUserMessage = firstExchange.userMessage,
                firstAnswer = firstExchange.answer,
            )
        }
    }

    /** [approvalId] is the waiting call's id, which the card carries. */
    fun answerApproval(threadId: String, approvalId: String, decision: ApprovalDecision) {
        val pending = approvals.value[threadId].orEmpty().firstOrNull { card -> card.toolCall.id == approvalId } ?: return
        withdrawApproval(pending)
        pending.answer.complete(decision)
    }

    private fun addApproval(pending: PendingApproval) {
        approvals.update { current -> current + (pending.threadId to current[pending.threadId].orEmpty() + pending) }
    }

    private fun withdrawApproval(pending: PendingApproval) {
        approvals.update { current ->
            val remaining = current[pending.threadId].orEmpty() - pending
            if (remaining.isEmpty()) current - pending.threadId else current + (pending.threadId to remaining)
        }
    }

    /** Call with [runStateLock] held and the thread idle. */
    private fun startRun(threadId: String, beforeRun: suspend () -> Unit) {
        running.update { current -> current + threadId }
        stepCounts.update { current -> current - threadId }
        AgentService.start(context)
        val job = scope.launch {
            try {
                beforeRun()
                runUntilNothingIsQueued(threadId)
            } finally {
                finishRun(threadId)
            }
        }
        runningJobs[threadId] = job
    }

    /**
     * Runs the thread, then runs again when a message was queued too late for
     * the loop to take it. A run that was stopped or failed does not go on:
     * its queue is handed back in [finishRun].
     */
    private suspend fun runUntilNothingIsQueued(threadId: String) {
        while (true) {
            val outcome = runWithOneRetry(threadId)
            extractMemoryAfterRun(threadId)
            launchCompaction(threadId)
            if (outcome is RunOutcome.Completed) {
                launchNaming(threadId)
            }
            val endedNormally = outcome is RunOutcome.Completed || outcome is RunOutcome.BudgetReached
            if (!endedNormally) {
                return
            }
            val lateMessages = takeQueuedText(threadId)
            if (lateMessages.isEmpty()) {
                return
            }
            for (text in lateMessages) {
                saveUserMessage(threadId, text)
            }
        }
    }

    private fun finishRun(threadId: String) {
        synchronized(runStateLock) {
            approvals.update { current -> current - threadId }
            runningJobs.remove(threadId)
            subagentRunners.remove(threadId)
            val leftInQueue = takeQueuedText(threadId)
            if (leftInQueue.isNotEmpty()) {
                // After Stop or a failure nothing is sent on its own; the user decides.
                handedBack.update { current -> current + (threadId to leftInQueue.joinToString("\n\n")) }
            }
            running.update { current -> current - threadId }
        }
    }

    /** Saves the message as the thread's next row and returns the text the model gets. */
    private suspend fun saveUserMessage(threadId: String, text: String): String {
        val threadDao = database.threadDao()
        val thread = threadDao.find(threadId) ?: return text
        if (thread.title.isBlank()) {
            val firstLineTitle = titleFrom(text)
            threadDao.rename(threadId, firstLineTitle)
            if (firstLineTitle.isNotBlank()) {
                provisionalTitles[threadId] = firstLineTitle
            }
        }
        val messageDao = database.messageDao()
        // The time goes into the message, not the system prompt, so the prompt cache holds (D-005).
        latestUserText[threadId] = text
        val textForModel = promptBuilder.userMessageWithContext(text, ZonedDateTime.now(), settings.snapshot.value.zoneInMessages)
        messageDao.upsert(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                threadId = threadId,
                position = messageDao.nextPosition(threadId),
                role = Role.USER.name,
                text = textForModel,
                toolCallsJson = "[]",
                toolCallId = null,
                isComplete = true,
                createdAtMillis = System.currentTimeMillis(),
            ),
        )
        threadDao.touch(threadId, System.currentTimeMillis())
        return textForModel
    }

    /** The run's final outcome; null when the run could not start. */
    private suspend fun runWithOneRetry(threadId: String): RunOutcome? {
        val firstOutcome = runOnce(threadId) ?: return null
        val shouldRetry = firstOutcome is RunOutcome.ProviderFailed && firstOutcome.retryable
        // Overload errors such as Gemini's 503 usually pass on a second try.
        val finalOutcome = if (shouldRetry) {
            delay(RETRY_DELAY_MILLIS)
            runOnce(threadId) ?: return null
        } else {
            firstOutcome
        }
        if (finalOutcome is RunOutcome.ProviderFailed) {
            saveError(threadId, finalOutcome.message)
        }
        return finalOutcome
    }

    /** Runs the loop once; null when the run could not start (an error row explains why). */
    private suspend fun runOnce(threadId: String): RunOutcome? {
        val thread = database.threadDao().find(threadId) ?: return null
        val snapshot = settings.snapshot.value
        val modelKey = modelKeyFor(thread)
        val service = modelKey?.let { ChatService.byKey(ModelKey.serviceOf(it)) }
        if (modelKey == null || service == null) {
            saveError(threadId, context.getString(R.string.error_no_model))
            return null
        }
        val session = RunSession(
            threadId = threadId,
            database = database,
            clock = System::currentTimeMillis,
            elapsedClock = SystemClock::elapsedRealtime,
            onApprovalNeeded = ::addApproval,
            onApprovalWithdrawn = ::withdrawApproval,
            onStepStarted = {
                stepCounts.update { current -> current + (threadId to (current[threadId] ?: 0) + 1) }
            },
            modelKey = modelKey,
            priceOf = { usage -> CostCalculator.costUsd(usage, catalog.find(modelKey)) },
            subagentsStarted = { subagentRunners[threadId]?.startedThisRun ?: 0 },
        )
        val routing = snapshot.routing.effectiveFor(modelKey)
        val provider = chatProvider(service, routing, onRoutingFallback = session::markRoutingFallback)
        if (provider == null) {
            saveError(threadId, context.getString(R.string.error_no_api_key, service.displayName))
            return null
        }
        // Unknown models count as not taking images (D-049).
        val modelAcceptsImages = catalog.find(modelKey)?.acceptsImages == true
        val threadFolder = ThreadFolders.create(context, threadId)
        val project = projectOf(thread)
        val toolServices = toolServicesFor(thread, modelAcceptsImages, project, threadFolder)
        val threadTools = ToolRegistry.tools(toolServices)
        threadApprovalModes[threadId] = thread.approvalMode.orEmpty()
        val approvalState = ThreadApprovalState(thread.allowAllInThread, thread.readOutsideContent) { allowAll, readOutsideContent ->
            database.threadDao().setApprovalState(threadId, allowAll, readOutsideContent)
        }
        threadApprovalStates[threadId] = approvalState
        // Made per run, so switching the Jev guard on or off applies to the next message.
        val guard = RecordingGuard.around(
            GuardFactory(settings, secrets, httpClient).create(),
            // Saves each answer as a note on the step and each call's cost as a hidden usage row.
            GuardRecorder(threadId, database.stepDao()::appendGuardNote, backgroundModel::saveUsage),
        )
        val permissionBroker = PermissionBroker(
            approvalRequester = session,
            threadState = approvalState,
            approvalMode = { currentApprovalMode(threadId) },
            // Read before every call, so a rule added or removed in Settings applies at once.
            settingsRules = { settings.snapshot.value.approvalRules },
            guard = guard,
            userRequest = { latestUserText[threadId].orEmpty() },
            asksAfterOutsideContent = { settings.snapshot.value.askBeforeSendingOutAfterOutsideContent },
        )
        val memorySection = ThreadMemory.sectionFor(thread) { memorySectionFor(thread, project) }
        val skillSection = skillSectionFor(thread)
        val instructionsSection = instructionsSectionFor(thread, snapshot)
        val projectFilesSection = projectFilesSectionFor(project)
        val imageCache = ThreadFolders.imageCache(context, threadId)
        val threadImageMessages = ImageMessages(ModelImageLoader(threadFolder, imageCache), modelAcceptsImages)
        val thinkingLevel = ThinkingLevels.effective(
            threadLevel = thread.thinkingLevel,
            modelLevel = snapshot.thinkingLevels[modelKey],
            isSupported = ThinkingSupport.isSupported(modelKey, catalog.find(modelKey)),
        )
        // The subagents are built before the request they ask through; the asker reads it only when a question comes.
        var parentRequest = ParentRequest(systemPrompt = "", tools = emptyList(), thinkingLevel, threadImageMessages)
        val subagents = SubagentRunner(
            // As a vision model sees them: each subagent keeps view_image only if its own model takes images.
            threadTools = ToolRegistry.tools(toolServices.copy(modelAcceptsImages = true)),
            subagentModels = AppSubagentModels(
                snapshot = snapshot,
                catalog = catalog,
                threadModelKey = modelKey,
                backgroundModelKey = backgroundModel.modelFor(modelKey),
                providerFor = ::subagentProvider,
                imageMessagesFor = { acceptsImages -> ImageMessages(ModelImageLoader(threadFolder, imageCache), acceptsImages) },
            ),
            recorder = SubagentSession(threadId, database, System::currentTimeMillis) { usageModelKey, usage, cost ->
                backgroundModel.saveUsage(threadId, usageModelKey, usage, cost)
            },
            parentAsker = ParentAsker { question, agentLabel, delegateToolCallId ->
                answerParentQuestion(threadId, modelKey, parentRequest, question, agentLabel, delegateToolCallId)
            },
            memorySection = memorySection,
            skillSection = skillSection,
            now = ZonedDateTime::now,
            zoneInMessages = snapshot.zoneInMessages,
            guard = guard,
            // Read once here, so delegate's prompt text stays the same for the whole run (D-138).
            limitSettings = snapshot.subagentLimits,
            customTypes = CustomSubagents.agentTypesOf(snapshot.customSubagents),
        )
        subagentRunners[threadId] = subagents
        val tools = threadTools + ToolRegistry.delegateTools(subagents, toolServices)
        val systemPrompt = promptBuilder.systemPrompt(
            activeTools = tools,
            memorySection = memorySection,
            skillSection = skillSection,
            instructionsSection = instructionsSection,
            projectFilesSection = projectFilesSection,
        )
        parentRequest = parentRequest.copy(systemPrompt = systemPrompt, tools = ToolDefinitions.of(tools))
        val loop = AgentLoop(
            provider = provider,
            tools = tools,
            toolContext = ToolContext(threadFolder, httpClient, skillLibrary.folder, projectFolder = project?.folder),
            permissionBroker = permissionBroker,
            recorder = session,
            settings = AgentSettings(
                model = ModelKey.modelOf(modelKey),
                thinkingLevel = thinkingLevel,
                systemPrompt = systemPrompt,
            ),
            imageMessages = threadImageMessages,
            takeQueuedMessages = { deliverQueuedMessages(threadId) },
            guard = guard,
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
            threadApprovalStates.remove(threadId)
        }
    }

    /** What the thread's own requests in this run send besides messages; ask_parent repeats it (D-063). */
    private data class ParentRequest(
        val systemPrompt: String,
        val tools: List<ToolDefinition>,
        val thinkingLevel: ThinkingLevel?,
        val imageMessages: ImageMessages,
    )

    /**
     * What the thread's tools need, from the user's keys and the thread's
     * switches. A thread on a local model gets the smaller set (D-133), and
     * read_document only when the thread or its project holds files.
     */
    private fun toolServicesFor(
        thread: ThreadEntity,
        modelAcceptsImages: Boolean,
        project: ThreadProject?,
        threadFolder: java.io.File,
    ): ToolServices {
        val services = allToolServicesFor(thread, modelAcceptsImages, project)
        if (!LocalModelRuntime.isLocal(modelKeyFor(thread))) {
            return services
        }
        val hasFiles = ProjectFolders.holdsFiles(threadFolder) || (project != null && ProjectFolders.holdsFiles(project.folder))
        // Fixed for the whole run, so the system prompt stays the same and llama.cpp can reuse its cache.
        return services.copy(onlyTools = LocalModelToolList.offered(settings.snapshot.value.localModelTools, hasFiles))
    }

    private fun allToolServicesFor(thread: ThreadEntity, modelAcceptsImages: Boolean, project: ThreadProject?): ToolServices = ToolServices(
        searchBackends = searchBackends(settings.snapshot.value.searchOrder),
        videoSummarizer = videoSummarizer(),
        webAccessEnabled = thread.webSearchEnabled,
        memoryStore = ThreadMemory.storeFor(thread) {
            RoomMemoryStore(
                database = database,
                threadId = thread.id,
                projectId = project?.id,
                clock = System::currentTimeMillis,
                holdFactsAfterOutsideContent = { settings.snapshot.value.holdFactsAfterOutsideContent },
                looksPlanted = { factText -> factScreen?.looksPlanted(thread.id, factText) },
            )
        },
        projectName = project?.name,
        // Like memory, earlier chats are not searched from an incognito thread (D-111).
        chatSearchStore = if (ThreadMemory.isOn(thread)) RoomChatSearchStore(database, thread.id) else null,
        // Never in an incognito thread: a proposal outlives the thread and would carry its content into the library.
        skillProposals = if (thread.incognito || !isSkillProposalOn()) null else skillProposals?.let(::LibrarySkillProposalSink),
        fileDestinations = fileDestinations,
        modelAcceptsImages = modelAcceptsImages,
        phone = phone,
        taskScheduler = taskSchedulerFor?.invoke(thread.id),
        mcpServers = mcpServers.forTools(),
        mcpToolListFolder = mcpServers.toolListFolder,
        codeRuntimes = CodeRuntimes.forApp(context),
        pageRenderer = pageRenderer,
        pdfRenderer = pdfRenderer,
        enabledGroups = settings.snapshot.value.enabledToolGroups,
        imageGenerator = imageGeneratorFor(thread.id),
        imageModelIds = settings.snapshot.value.imageModels.modelIds,
        defaultImageModelId = settings.snapshot.value.imageModels.defaultModelId,
    )

    /** Null without a saved OpenRouter key, which leaves generate_image out; each picture's cost is saved on the thread. */
    private fun imageGeneratorFor(threadId: String): ImageGenerator? {
        if (secrets.read(SecretName.OPENROUTER) == null) {
            return null
        }
        val openRouter = OpenRouterImageGenerator({ secrets.read(SecretName.OPENROUTER) }, httpClient)
        return RecordingImageGenerator(openRouter, threadId, backgroundModel::saveUsage)
    }

    /**
     * The tools a local model could be offered, built with the app's keys
     * and services only to measure their prompt text for the Local models
     * page (D-133); none of them runs. Reads saved keys, so call it off the
     * main thread.
     */
    fun toolsForLocalPromptCosts(): List<Tool> {
        val services = ToolServices(
            searchBackends = searchBackends(settings.snapshot.value.searchOrder),
            videoSummarizer = videoSummarizer(),
            webAccessEnabled = true,
            memoryStore = RoomMemoryStore(database, NO_THREAD, null, System::currentTimeMillis),
            fileDestinations = fileDestinations,
            phone = phone,
            taskScheduler = taskSchedulerFor?.invoke(NO_THREAD),
            onlyTools = LocalModelToolList.CHOOSABLE.toSet(),
        )
        return ToolRegistry.tools(services)
    }

    /**
     * Every tool the app can offer, built only to read the actions each declares for
     * the Settings page of "always allow" rules; none of them runs. Reads saved keys,
     * so call it off the main thread.
     */
    fun approvalRuleChoices(): List<ApprovalRuleChoiceUi> {
        val services = ToolServices(
            searchBackends = searchBackends(settings.snapshot.value.searchOrder),
            videoSummarizer = videoSummarizer(),
            webAccessEnabled = true,
            memoryStore = RoomMemoryStore(database, NO_THREAD, null, System::currentTimeMillis),
            fileDestinations = fileDestinations,
            phone = phone,
            taskScheduler = taskSchedulerFor?.invoke(NO_THREAD),
            mcpServers = mcpServers.forTools(),
            mcpToolListFolder = mcpServers.toolListFolder,
            codeRuntimes = CodeRuntimes.forApp(context),
        )
        return ApprovalRuleChoices.of(ToolRegistry.tools(services))
    }

    /**
     * The pieces the thread's next request would send, for the context
     * sheet (D-081): the same tools, sections and history as [runOnce]
     * builds, without its side effects. Null for a thread not yet created.
     */
    suspend fun contextBreakdown(threadId: String): ContextBreakdown? {
        val thread = database.threadDao().find(threadId) ?: return null
        val modelKey = modelKeyFor(thread)
        val modelAcceptsImages = modelKey?.let { key -> catalog.find(key)?.acceptsImages } == true
        val project = projectOf(thread)
        val toolServices = toolServicesFor(thread, modelAcceptsImages, project, ThreadFolders.create(context, threadId))
        val tools = ToolRegistry.tools(toolServices) + ToolRegistry.delegateTools(PromptOnlySubagents(settings.snapshot.value), toolServices)
        // An incognito thread sends no Memory section (D-111).
        val facts = if (ThreadMemory.isOn(thread)) promptFactsOf(thread, project) else emptyList()
        val memory = MemorySection.build(facts, memoryBudgetFor(thread))
        val skills = enabledSkillsOf(thread)
        // The user's instructions sit between the tools and the skills; the sheet counts them with the system prompt.
        val instructions = instructionsSectionFor(thread, settings.snapshot.value)
        val rows = database.messageDao().listThread(threadId)
        val summary = database.compactionDao().latestForThread(threadId)
        val history = CompactionPlan.historyAfter(rows, summary?.summaryText, summary?.upToPosition)
        // Images are counted, not loaded: a placeholder stands for each one the request would carry.
        val withImages = ImageMessages({ ImagePart("image/jpeg", "") }, modelAcceptsImages).prepare(history)
        return ContextBreakdown(
            basePrompt = listOf(SystemPrompt.BASE.trimEnd(), instructions.trim()).filter { part -> part.isNotEmpty() }.joinToString("\n\n"),
            tools = tools,
            // The project's files sit between the skills and the memory; the sheet counts them with the skills.
            skillSection = listOf(SkillSection.build(skills), projectFilesSectionFor(project))
                .filter { part -> part.isNotEmpty() }
                .joinToString("\n\n"),
            skillCount = skills.size,
            memorySection = memory.text,
            factCount = memory.includedIds.size,
            messages = withImages,
            summaryBlock = summary?.let { compaction -> CompactionPlan.summaryBlock(compaction.summaryText) },
            summaryCoversMessages = summary?.let { compaction -> CompactionPlan.coveredMessageCount(rows, compaction.upToPosition) } ?: 0,
        )
    }

    /**
     * Stands in for the subagent runner where only the delegate tool's prompt
     * text is needed, with the same types and limits a run would read.
     */
    private class PromptOnlySubagents(snapshot: SettingsSnapshot) : SubagentLauncher {
        override val agentTypes: List<SubagentTypeInfo> = SubagentRunner.AGENT_TYPES +
            snapshot.customSubagents.map { subagent -> SubagentTypeInfo(subagent.name, subagent.description) }
        override val models: List<SubagentModelInfo> = emptyList()
        override val extraToolNames: List<String> = emptyList()
        override val startedThisRun: Int = 0
        override val limitSettings: SubagentLimitSettings = snapshot.subagentLimits

        override suspend fun launch(tasks: List<SubagentTask>, context: ToolContext): List<SubagentReport> =
            error("PromptOnlySubagents never runs subagents")
    }

    /**
     * A subagent's ask_parent (D-063): one call on the thread's model with
     * the thread's conversation up to the delegate call, and the same system
     * prompt, tool list (not callable), thinking level and images as the
     * thread's requests, so the provider can serve the prefix from its
     * prompt cache. Its cost counts to the thread and to the asking subagent.
     */
    private suspend fun answerParentQuestion(
        threadId: String,
        modelKey: String,
        parentRequest: ParentRequest,
        question: String,
        agentLabel: String,
        delegateToolCallId: String,
    ): ParentAnswer {
        val summary = database.compactionDao().latestForThread(threadId)
        val history = CompactionPlan.historyAfter(
            rows = database.messageDao().listThread(threadId),
            summaryText = summary?.summaryText,
            upToPosition = summary?.upToPosition,
        )
        val conversation = ParentQuestion.conversation(history, delegateToolCallId, agentLabel, question)
        val answer = backgroundModel.completeOn(
            threadId = threadId,
            modelKey = modelKey,
            systemPrompt = parentRequest.systemPrompt,
            messages = parentRequest.imageMessages.prepare(conversation),
            maxOutputTokens = PARENT_ANSWER_TOKENS,
            tools = parentRequest.tools,
            thinkingLevel = parentRequest.thinkingLevel,
        )
        return when (answer) {
            is BackgroundAnswer.Success -> ParentAnswer.Answered(answer.text.trim(), answer.costUsd)
            is BackgroundAnswer.Failed -> ParentAnswer.Failed(answer.message, answer.costUsd)
        }
    }

    /** A provider for a subagent's model; null when its service has no saved key. */
    private fun subagentProvider(modelKey: String): ChatProvider? {
        val service = ChatService.byKey(ModelKey.serviceOf(modelKey)) ?: return null
        val routing = settings.snapshot.value.routing.effectiveFor(modelKey)
        return chatProvider(service, routing, onRoutingFallback = {})
    }

    /**
     * Global, project and thread facts for the system prompt, built once per
     * run so the prompt stays the same for every request of the run (D-005).
     * Facts the memory tool saves during the run reach the next run.
     */
    private suspend fun memorySectionFor(thread: ThreadEntity, project: ThreadProject?): String {
        val section = MemorySection.build(promptFactsOf(thread, project), memoryBudgetFor(thread))
        if (section.includedIds.isNotEmpty()) {
            // Safe for the cache: the section is chosen by use time but written in id order (D-035).
            database.memoryDao().markUsed(section.includedIds, System.currentTimeMillis())
        }
        // The context sheet lists these as the facts the last answer had.
        database.messageDao().latestUserMessageId(thread.id)?.let { messageId ->
            database.messageDao().setPromptFactIds(messageId, PromptFacts.idsText(section.includedIds))
        }
        return section.text
    }

    /**
     * The facts in the memory section of the thread's last run, or of its next
     * request before any run, one line each for the context sheet.
     */
    suspend fun promptFactLines(threadId: String): List<String> {
        val thread = database.threadDao().find(threadId) ?: return emptyList()
        if (!ThreadMemory.isOn(thread)) {
            return emptyList()
        }
        val facts = promptFactsOf(thread, projectOf(thread))
        val lastRunIds = database.messageDao().latestPromptFactIds(threadId)?.let(PromptFacts::idsOf)
        val nextRequestIds = MemorySection.build(facts, memoryBudgetFor(thread)).includedIds
        return PromptFacts.sheetLines(facts, lastRunIds, nextRequestIds)
    }

    /** A model on the phone gets a smaller memory section, as every prompt token costs it time (D-135). */
    private fun memoryBudgetFor(thread: ThreadEntity): MemoryBudget =
        if (LocalModelRuntime.isLocal(modelKeyFor(thread))) MemoryBudget.LOCAL else MemoryBudget.CLOUD

    private suspend fun promptFactsOf(thread: ThreadEntity, project: ThreadProject?): List<PromptFact> =
        database.memoryDao().listVisibleFrom(thread.id, project?.id).map { memory -> PromptFacts.of(memory, ZoneId.systemDefault()) }

    /** The thread's project with its shared folder, or null; a project deleted meanwhile counts as none. */
    private suspend fun projectOf(thread: ThreadEntity): ThreadProject? {
        val projectId = thread.projectId ?: return null
        val project = database.projectDao().find(projectId) ?: return null
        return ThreadProject(project.id, project.name, ProjectFolders.create(context, project.id))
    }

    /** What the project's threads share, read once per run like the skills (D-135). */
    private fun projectFilesSectionFor(project: ThreadProject?): String {
        if (project == null) {
            return ""
        }
        return ProjectFilesSection.build(project.name, ProjectFolders.filePaths(project.folder))
    }

    /** The instructions of the thread's project, read once per run like the skills (D-110). */
    private suspend fun projectSectionFor(thread: ThreadEntity): String {
        val projectId = thread.projectId ?: return ""
        val project = database.projectDao().find(projectId) ?: return ""
        return ProjectSection.build(project.name, project.instructions)
    }

    /**
     * The thread's enabled skills, read from the library once per run, so
     * the prompt stays the same for every request of the run (D-005). A
     * skill edited or imported during a run reaches the next run.
     */
    private suspend fun skillSectionFor(thread: ThreadEntity): String = SkillSection.build(enabledSkillsOf(thread))

    private suspend fun enabledSkillsOf(thread: ThreadEntity): List<PromptSkill> {
        val entries = withContext(Dispatchers.IO) { skillLibrary.list() }
        return ThreadSkills.forPrompt(entries, thread.disabledSkills)
    }

    /**
     * The answer style, the general, project and thread instructions and the
     * thread's persona, read once per run so every request of the run sends the same
     * bytes (D-005, D-107). A persona deleted meanwhile counts as none.
     */
    private suspend fun instructionsSectionFor(thread: ThreadEntity, snapshot: SettingsSnapshot): String {
        val persona = thread.personaId?.let { personaId -> database.personaDao().find(personaId) }
        return InstructionsSection.build(
            answerStyle = AnswerStyles.effective(thread.answerStyle, snapshot.answerStyle),
            generalInstructions = snapshot.customInstructions,
            persona = persona?.let { found -> PromptPersona(found.name, found.instructions) },
            threadInstructions = thread.instructions,
            projectPart = projectSectionFor(thread),
        )
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
    private fun chatProvider(service: ChatService, route: OpenRouterRoute, onRoutingFallback: () -> Unit): ChatProvider? {
        val secret = service.secret
        val key = if (secret == null) null else secrets.read(secret) ?: return null
        return ChatProviders.create(service, key, httpClient, route, onRoutingFallback, localRuntime)
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

        /** The thread id of tools built only for their prompt text; it matches no thread. */
        const val NO_THREAD = ""

        /** A parent's answer to a subagent is a few sentences. */
        const val PARENT_ANSWER_TOKENS = 1_000
    }
}

/** A message sent while the thread's run was going, shown above the composer until the loop takes it. */
data class QueuedMessage(val id: String, val text: String)

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

/** A thread's project as one run needs it (D-135). */
data class ThreadProject(val id: String, val name: String, val folder: java.io.File)

/**
 * The folders the threads of a project share (D-135), outside every thread
 * folder, so deleting a thread never deletes a project file.
 */
object ProjectFolders {
    fun create(context: Context, projectId: String): java.io.File {
        val folder = folderOf(context, projectId)
        folder.mkdirs()
        return folder
    }

    fun folderOf(context: Context, projectId: String): java.io.File = java.io.File(context.filesDir, "projects/$projectId")

    fun delete(context: Context, projectId: String) {
        folderOf(context, projectId).deleteRecursively()
    }

    /** Every file in the folder as the model names it, "/project/..." (ThreadPaths.PROJECT_ROOT). */
    fun filePaths(folder: java.io.File): List<String> =
        folder.walkTopDown()
            .filter { file -> file.isFile }
            .map { file -> ThreadPaths.PROJECT_ROOT + "/" + file.relativeTo(folder).invariantSeparatorsPath }
            .toList()

    /** True when the folder holds at least one file at any depth. */
    fun holdsFiles(folder: java.io.File): Boolean = folder.walkTopDown().any { file -> file.isFile }
}

internal fun Context.startForegroundServiceCompat(intent: Intent) {
    ContextCompat.startForegroundService(this, intent)
}
