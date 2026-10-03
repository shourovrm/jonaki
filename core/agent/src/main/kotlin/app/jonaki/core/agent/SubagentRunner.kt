package app.jonaki.core.agent

import app.jonaki.core.toolapi.SubagentLauncher
import app.jonaki.core.toolapi.SubagentLimitSettings
import app.jonaki.core.toolapi.SubagentModelInfo
import app.jonaki.core.toolapi.SubagentReport
import app.jonaki.core.toolapi.SubagentTask
import app.jonaki.core.toolapi.SubagentTypeInfo
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ViewedImages
import java.io.IOException
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Starts the subagents of one delegate call (M7, D-015): each gets only its
 * task, its type's tools and its own model, and they run in parallel. The
 * app builds one runner per run of the thread's agent.
 *
 * @param threadTools the thread's tools as a model that takes images would
 *   have them (view_image included), without delegate; each subagent's list
 *   is cut from it by its type and its own model.
 * @param memorySection the run's memory section, read-only for subagents.
 * @param skillSection the run's skill list, shown to writers and workers only.
 */
class SubagentRunner(
    private val threadTools: List<Tool>,
    private val broker: PermissionBroker,
    private val subagentModels: SubagentModels,
    private val recorder: SubagentRecorder,
    private val parentAsker: ParentAsker,
    private val memorySection: String,
    private val skillSection: String,
    private val now: () -> ZonedDateTime,
    /** The user's limits when the run started; fixed for the run (D-138). */
    override val limitSettings: SubagentLimitSettings = SubagentLimitSettings(),
    private val limits: SubagentLimits = SubagentLimits.from(limitSettings),
    /** Types the user made; one named like a built-in type is left out. */
    customTypes: List<AgentType> = emptyList(),
    private val timer: WaitTimer = WaitTimer.REAL,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : SubagentLauncher {
    private val givableTools: List<Tool> = threadTools.filter { tool -> tool.name !in AgentTypes.NEVER_GIVEN }

    private val types: List<AgentType> = AgentTypes.ALL + customTypes.filter { type -> AgentTypes.byName(type.name) == null }

    override val agentTypes: List<SubagentTypeInfo> = types.map { type -> SubagentTypeInfo(type.name, type.description) }

    override val models: List<SubagentModelInfo>
        get() = subagentModels.scoped

    override val extraToolNames: List<String> = givableTools.map { tool -> tool.name }.sorted()

    // One runner serves one run; delegate calls of a turn may run in parallel, so the count is atomic.
    private val startedCount = java.util.concurrent.atomic.AtomicInteger(0)

    override val startedThisRun: Int
        get() = startedCount.get()

    /** The work of each running subagent by its id, so that the user can stop one alone (D-126). */
    private val runningWork = ConcurrentHashMap<String, Job>()

    /**
     * Stops one subagent and lets the others of its call go on. It returns
     * what it has, as at the thread's Stop. False when no subagent with
     * [subagentId] is running in this runner.
     */
    fun stop(subagentId: String): Boolean {
        val work = runningWork[subagentId] ?: return false
        work.cancel()
        return true
    }

    override suspend fun launch(tasks: List<SubagentTask>, context: ToolContext): List<SubagentReport> {
        // The delegate tool refuses more; this guards any other caller of the launcher.
        if (tasks.size > limitSettings.perCall) {
            val refusal = "Error: at most ${limitSettings.perCall} subagents run at once, not ${tasks.size}."
            return tasks.map { task -> SubagentReport(task.agentType, refusal) }
        }
        startedCount.addAndGet(tasks.size)
        // The OpenAI-compatible stream gives "" for a call without an id.
        val callId = context.toolCallId?.takeIf { id -> id.isNotBlank() } ?: newId()
        val group = DelegationGroup(
            parentToolCallId = callId,
            folder = "$DELEGATIONS_FOLDER/${folderNameFor(callId)}",
            size = tasks.size,
            notesLock = Mutex(),
        )
        // One subagent that fails must not cancel the others; Stop still cancels all of them.
        return supervisorScope {
            val running = tasks.mapIndexed { index, task -> async { runOne(index, task, group, context) } }
            running.map { subagent -> subagent.await() }
        }
    }

    private class DelegationGroup(
        val parentToolCallId: String,
        /** Relative to the thread folder: the notes board and long answers live here. */
        val folder: String,
        val size: Int,
        val notesLock: Mutex,
    )

    private suspend fun runOne(index: Int, task: SubagentTask, group: DelegationGroup, context: ToolContext): SubagentReport {
        val type = types.firstOrNull { candidate -> candidate.name == task.agentType.trim().lowercase() }
        val label = if (group.size > 1) "${task.agentType} ${index + 1}" else task.agentType
        if (type == null) {
            return SubagentReport(label, "Error: there is no agent type named ${task.agentType}.")
        }
        val model = subagentModels.modelFor(type, task.modelKey)
            ?: return SubagentReport(label, "Error: the model for $label has no saved API key. Pick another model.")
        val subagentId = newId()
        val progress = SubagentProgress()
        val outcome = try {
            recorder.subagentStarted(SubagentStart(subagentId, group.parentToolCallId, index, type.name, task.task, model.key))
            val loop = buildLoop(subagentId, label, type, task, model, group, context, progress)
            runStoppable(subagentId, progress) {
                withTimeoutOrNull(limits.timeLimit) { loop.run(PromptBuilder("").userMessageWithContext(task.task, now())) }
                    ?: progress.stoppedEarly(SubagentStop.TIME_LIMIT)
            }
        } catch (cancellation: CancellationException) {
            // Stop: the card must not stay "running"; NonCancellable lets the save finish.
            withContext(NonCancellable) { finish(subagentId, label, progress.stoppedEarly(SubagentStop.STOPPED), group, context) }
            throw cancellation
        } catch (exception: Exception) {
            progress.stoppedEarly(SubagentStop.FAILED, failure = "${exception::class.simpleName}: ${exception.message}")
        }
        // Saving waits for a lock, which a Stop at that moment must not interrupt.
        val answerText = withContext(NonCancellable) { finish(subagentId, label, outcome, group, context) }
        return SubagentReport(label, answerText)
    }

    /**
     * Runs [work] as its own job, which [stop] can cancel without touching
     * the other subagents or the thread's run. When the whole run is stopped
     * the cancellation goes on up, so the caller saves it as before.
     */
    private suspend fun runStoppable(
        subagentId: String,
        progress: SubagentProgress,
        work: suspend () -> SubagentOutcome,
    ): SubagentOutcome = coroutineScope {
        val job = async { work() }
        runningWork[subagentId] = job
        try {
            job.await()
        } catch (cancellation: CancellationException) {
            // Still active here means only this subagent was stopped.
            ensureActive()
            progress.stoppedEarly(SubagentStop.STOPPED)
        } finally {
            runningWork.remove(subagentId)
        }
    }

    /** Caps the answer, saves the subagent's end, and returns what goes back to the thread's agent. */
    private suspend fun finish(
        subagentId: String,
        label: String,
        outcome: SubagentOutcome,
        group: DelegationGroup,
        context: ToolContext,
    ): String {
        val fullText = SubagentPrompt.resultText(outcome, limits)
        val answerText = cappedAnswer(fullText, "${group.folder}/${label.replace(' ', '-')}.md", context)
        try {
            recorder.subagentFinished(subagentId, outcome, answerText)
        } catch (exception: Exception) {
            // The answer matters more to the run than the card; the card is fixed at the next start.
        }
        return answerText
    }

    /** At most 16 KB; the whole answer is saved beside it, or, if that fails, cut with a note. */
    private fun cappedAnswer(fullText: String, answerPath: String, context: ToolContext): String = try {
        context.outputLimiter.limitInto(fullText, MAX_ANSWER_CHARACTERS, answerPath)
    } catch (exception: IOException) {
        fullText.take(MAX_ANSWER_CHARACTERS) +
            "\n\n[Answer cut at 16 KB; saving the full answer to $answerPath failed: ${exception.message}]"
    }

    private fun buildLoop(
        subagentId: String,
        label: String,
        type: AgentType,
        task: SubagentTask,
        model: SubagentModel,
        group: DelegationGroup,
        context: ToolContext,
        progress: SubagentProgress,
    ): SubagentLoop {
        // view_image follows the subagent's model, so a text-only thread can hand images to a vision model.
        val usableTools = givableTools.filter { tool -> tool.name != ViewedImages.TOOL_NAME || model.acceptsImages }
        val typeTools = if (type.usesEveryThreadTool) usableTools else usableTools.filter { tool -> tool.name in type.defaultTools }
        val extraTools = usableTools.filter { tool -> tool.name in task.extraTools }
        val threadToolsAtStart = (typeTools + extraTools).distinctBy { tool -> tool.name }
        val askParent = AskParentTool(parentAsker, label, group.parentToolCallId, onCost = { cost ->
            progress.addCost(cost)
            recorder.askCostAdded(subagentId, cost)
        })
        val helpers = mutableListOf<Tool>(RequestTool(), askParent)
        if (group.size > 1) {
            helpers += NotesTool(context.threadFolder, "${group.folder}/notes.md", label, group.notesLock)
        }
        val startTools = threadToolsAtStart + helpers
        return SubagentLoop(
            subagentId = subagentId,
            model = model,
            systemPrompt = SubagentPrompt.systemPrompt(type, startTools, limits, model.hasKnownPrice, memorySection, skillSection),
            toolbox = SubagentToolbox(startTools, requestableTools = usableTools - threadToolsAtStart.toSet()),
            toolContext = ToolContext(
                context.threadFolder,
                context.httpClient,
                context.skillLibraryFolder,
                projectFolder = context.projectFolder,
            ),
            gate = SubagentGate(broker, label, timer),
            recorder = recorder,
            limits = limits,
            progress = progress,
            timer = timer,
        )
    }

    companion object {
        /**
         * A short folder name that stays the same for one call. Call ids come
         * from the provider, and Gemini 3's carry a thought signature of
         * hundreds of characters, over the file system's 255-byte name limit.
         */
        private fun folderNameFor(callId: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(callId.toByteArray())
            return digest.take(FOLDER_NAME_BYTES).joinToString("") { byte -> "%02x".format(byte) }
        }

        /** The notes board of the delegate call [callId], relative to the thread folder (D-060). */
        fun notesBoardPath(callId: String): String = "$DELEGATIONS_FOLDER/${folderNameFor(callId)}/notes.md"

        /** The built-in types as the delegate tool lists them. */
        val AGENT_TYPES: List<SubagentTypeInfo> = AgentTypes.ALL.map { type -> SubagentTypeInfo(type.name, type.description) }

        /** Relative to the thread folder. */
        const val DELEGATIONS_FOLDER = "work/delegations"

        /** The user's ruling: 16 KB per answer, the rest saved beside it (D-060). */
        const val MAX_ANSWER_CHARACTERS = 16 * 1024

        /** 6 bytes, 12 hex characters: enough that two calls of one thread never share a folder. */
        private const val FOLDER_NAME_BYTES = 6
    }
}
