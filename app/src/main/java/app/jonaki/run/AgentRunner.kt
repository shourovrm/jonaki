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
import app.jonaki.core.agent.ContextBreakdown
import app.jonaki.core.agent.PromptSkill
import app.jonaki.core.model.ImagePart
import app.jonaki.core.toolapi.SubagentLauncher
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
import app.jonaki.core.agent.PermissionBroker
import app.jonaki.core.agent.PromptBuilder
import app.jonaki.core.agent.RunOutcome
import app.jonaki.core.agent.SkillSection
import app.jonaki.core.agent.InstructionsSection
import app.jonaki.core.agent.PromptPersona
import app.jonaki.settings.AnswerStyles
import app.jonaki.settings.SettingsSnapshot
import app.jonaki.core.model.Role
import app.jonaki.memory.MemoryExtractor
import app.jonaki.memory.RoomMemoryStore
import app.jonaki.memory.ThreadMemory
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
import app.jonaki.settings.ApprovalModes
import app.jonaki.settings.ChatService
import app.jonaki.settings.McpServerStore
import app.jonaki.settings.SearchService
import app.jonaki.settings.SecretName
import app.jonaki.settings.SecretStore
import app.jonaki.files.ModelImageLoader
import app.jonaki.skills.ThreadSkills
import app.jonaki.tools.phone.Phone
import app.jonaki.tools.schedule.TaskScheduler
import app.jonaki.tools.sharefile.FileDestinations
import app.jonaki.tools.youtubesummarize.VideoAnswer
import app.jonaki.tools.youtubesummarize.VideoSummarizer
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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
    /** Answers subagents' ask_parent and saves their model calls' usage (M7). */
    private val backgroundModel: BackgroundModel,
    /** Calendar, reminders, notifications, clipboard and apps for the phone tool (D-020). */
    private val phone: Phone? = null,
    /** The schedule tool's tasks, seen from one thread (plan M9). */
    private val taskSchedulerFor: ((threadId: String) -> TaskScheduler)? = null,
    private val mcpServers: McpServerStore,
) {
    private val runningJobs = mutableMapOf<String, Job>()

    /** Threads the user left while a run was still going; extraction waits for the run's end. */
    private val leftWhileRunning = MutableStateFlow<Set<String>>(emptySet())

    private val running = MutableStateFlow<Set<String>>(emptySet())
    val runningThreadIds: StateFlow<Set<String>> = running.asStateFlow()

    /** Cards waiting per thread, oldest first; parallel subagents can ask at the same time (M7). */
    private val approvals = MutableStateFlow<Map<String, List<PendingApproval>>>(emptyMap())
    val pendingApprovals: StateFlow<Map<String, List<PendingApproval>>> = approvals.asStateFlow()

    private val stepCounts = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** Steps started so far in each running thread. */
    val runStepCounts: StateFlow<Map<String, Int>> = stepCounts.asStateFlow()

    private val promptBuilder = PromptBuilder(SystemPrompt.BASE)

    /**
     * Each running thread's own approval mode name, "" when it follows
     * Settings. The broker reads it before every tool call, so a mode picked
     * in the chat during a run applies from the next call (D-058).
     */
    private val threadApprovalModes = ConcurrentHashMap<String, String>()

    /**
     * Creates a thread and returns its id. A thread made inside a project
     * starts with the project's model when it has one (D-PRJ-1).
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

    fun send(threadId: String, text: String) {
        if (threadId in running.value || text.isBlank()) {
            return
        }
        startRun(threadId) {
            saveUserMessage(threadId, text.trim())
        }
    }

    /** Like [send], for a scheduled task: false when the thread is busy, so the caller can wait and try again. */
    fun sendIfIdle(threadId: String, text: String): Boolean {
        if (threadId in running.value || text.isBlank()) {
            return false
        }
        send(threadId, text)
        return true
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

    private val incognitoCleanup = IncognitoCleanup(
        listIncognito = { database.threadDao().listIncognitoActivity() },
        isRunning = { threadId -> threadId in running.value },
        deleteThread = ::deleteThread,
        clock = System::currentTimeMillis,
    )

    /** Deletes incognito threads whose last message is a day old (D-PRJ-2). */
    suspend fun deleteExpiredIncognitoThreads() {
        incognitoCleanup.deleteExpired()
    }

    /** "Keep as a regular thread": the thread stays, and only later messages reach memory (D-PRJ-2). */
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
            if (!ThreadMemory.isOn(thread)) {
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
            onApprovalNeeded = ::addApproval,
            onApprovalWithdrawn = ::withdrawApproval,
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
        val toolServices = toolServicesFor(thread, modelAcceptsImages)
        val threadTools = ToolRegistry.tools(toolServices)
        val allowedForThread = thread.toolsAllowedForThread.split(",").filter { it.isNotBlank() }.toSet()
        threadApprovalModes[threadId] = thread.approvalMode.orEmpty()
        val permissionBroker = PermissionBroker(session, allowedForThread, approvalMode = { currentApprovalMode(threadId) })
        val memorySection = ThreadMemory.sectionFor(thread) { memorySectionFor(threadId) }
        val skillSection = skillSectionFor(thread)
        val instructionsSection = instructionsSectionFor(thread, snapshot)
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
            broker = permissionBroker,
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
        )
        val tools = threadTools + ToolRegistry.delegateTools(subagents, toolServices.enabledGroups)
        val systemPrompt = promptBuilder.systemPrompt(
            activeTools = tools,
            memorySection = memorySection,
            skillSection = skillSection,
            instructionsSection = instructionsSection,
        )
        parentRequest = parentRequest.copy(systemPrompt = systemPrompt, tools = ToolDefinitions.of(tools))
        val loop = AgentLoop(
            provider = provider,
            tools = tools,
            toolContext = ToolContext(threadFolder, httpClient, skillLibrary.folder),
            permissionBroker = permissionBroker,
            recorder = session,
            settings = AgentSettings(
                model = ModelKey.modelOf(modelKey),
                thinkingLevel = thinkingLevel,
                systemPrompt = systemPrompt,
            ),
            imageMessages = threadImageMessages,
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

    /** What the thread's own requests in this run send besides messages; ask_parent repeats it (D-063). */
    private data class ParentRequest(
        val systemPrompt: String,
        val tools: List<ToolDefinition>,
        val thinkingLevel: ThinkingLevel?,
        val imageMessages: ImageMessages,
    )

    /** What the thread's tools need, from the user's keys and the thread's switches. */
    private fun toolServicesFor(thread: ThreadEntity, modelAcceptsImages: Boolean): ToolServices = ToolServices(
        searchBackends = searchBackends(settings.snapshot.value.searchOrder),
        videoSummarizer = videoSummarizer(),
        webAccessEnabled = thread.webSearchEnabled,
        memoryStore = ThreadMemory.storeFor(thread) { RoomMemoryStore(database, thread.id, System::currentTimeMillis) },
        fileDestinations = fileDestinations,
        modelAcceptsImages = modelAcceptsImages,
        phone = phone,
        taskScheduler = taskSchedulerFor?.invoke(thread.id),
        mcpServers = mcpServers.forTools(),
        mcpToolListFolder = mcpServers.toolListFolder,
        codeRuntimes = CodeRuntimes.forApp(context),
        enabledGroups = settings.snapshot.value.enabledToolGroups,
    )

    /**
     * The pieces the thread's next request would send, for the context
     * sheet (D-081): the same tools, sections and history as [runOnce]
     * builds, without its side effects. Null for a thread not yet created.
     */
    suspend fun contextBreakdown(threadId: String): ContextBreakdown? {
        val thread = database.threadDao().find(threadId) ?: return null
        val modelKey = modelKeyFor(thread)
        val modelAcceptsImages = modelKey?.let { key -> catalog.find(key)?.acceptsImages } == true
        val toolServices = toolServicesFor(thread, modelAcceptsImages)
        val tools = ToolRegistry.tools(toolServices) + ToolRegistry.delegateTools(PromptOnlySubagents, toolServices.enabledGroups)
        // An incognito thread sends no Memory section (D-PRJ-2).
        val facts = if (ThreadMemory.isOn(thread)) promptFactsOf(threadId) else emptyList()
        val memory = MemorySection.build(facts)
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
            skillSection = SkillSection.build(skills),
            skillCount = skills.size,
            memorySection = memory.text,
            factCount = memory.includedIds.size,
            messages = withImages,
            summaryBlock = summary?.let { compaction -> CompactionPlan.summaryBlock(compaction.summaryText) },
            summaryCoversMessages = summary?.let { compaction -> CompactionPlan.coveredMessageCount(rows, compaction.upToPosition) } ?: 0,
        )
    }

    /** Stands in for the subagent runner where only the delegate tool's prompt text is needed. */
    private object PromptOnlySubagents : SubagentLauncher {
        override val agentTypes: List<SubagentTypeInfo> = SubagentRunner.AGENT_TYPES
        override val models: List<SubagentModelInfo> = emptyList()
        override val extraToolNames: List<String> = emptyList()

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
     * Global and thread facts for the system prompt, built once per run so
     * the prompt stays the same for every request of the run (D-005). Facts
     * the memory tool saves during the run reach the next run.
     */
    private suspend fun memorySectionFor(threadId: String): String {
        val section = MemorySection.build(promptFactsOf(threadId))
        if (section.includedIds.isNotEmpty()) {
            // Safe for the cache: the section is chosen by use time but written in id order (D-035).
            database.memoryDao().markUsed(section.includedIds, System.currentTimeMillis())
        }
        return section.text
    }

    private suspend fun promptFactsOf(threadId: String): List<PromptFact> =
        database.memoryDao().listVisibleFrom(threadId).map { memory ->
            PromptFact(
                id = memory.id,
                text = memory.text,
                isGlobal = memory.threadId == null,
                pinned = memory.pinned,
                lastUsedAtMillis = memory.lastUsedAtMillis,
            )
        }

    /** The instructions of the thread's project, read once per run like the skills (D-PRJ-1). */
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
     * bytes (D-005, D-STY-1). A persona deleted meanwhile counts as none.
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

        /** A parent's answer to a subagent is a few sentences. */
        const val PARENT_ANSWER_TOKENS = 1_000
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
