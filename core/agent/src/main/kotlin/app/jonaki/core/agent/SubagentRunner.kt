package app.jonaki.core.agent

import app.jonaki.core.toolapi.SubagentLauncher
import app.jonaki.core.toolapi.SubagentModelInfo
import app.jonaki.core.toolapi.SubagentReport
import app.jonaki.core.toolapi.SubagentTask
import app.jonaki.core.toolapi.SubagentTypeInfo
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ViewedImages
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    private val limits: SubagentLimits = SubagentLimits(),
    private val timer: ApprovalTimer = ApprovalTimer.REAL,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : SubagentLauncher {
    private val givableTools: List<Tool> = threadTools.filter { tool -> tool.name !in AgentTypes.NEVER_GIVEN }

    override val agentTypes: List<SubagentTypeInfo> = AgentTypes.ALL.map { type -> SubagentTypeInfo(type.name, type.description) }

    override val models: List<SubagentModelInfo>
        get() = subagentModels.scoped

    override val extraToolNames: List<String> = givableTools.map { tool -> tool.name }.sorted()

    override suspend fun launch(tasks: List<SubagentTask>, context: ToolContext): List<SubagentReport> {
        val groupId = context.toolCallId ?: newId()
        val group = DelegationGroup(
            parentToolCallId = groupId,
            folder = "$DELEGATIONS_FOLDER/${safeFileName(groupId)}",
            size = tasks.size,
            notesLock = Mutex(),
        )
        return coroutineScope {
            tasks.mapIndexed { index, task -> async { runOne(index, task, group, context) } }.awaitAll()
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
        val type = AgentTypes.byName(task.agentType)
        val label = if (group.size > 1) "${task.agentType} ${index + 1}" else task.agentType
        if (type == null) {
            return SubagentReport(label, "Error: there is no agent type named ${task.agentType}.")
        }
        val model = subagentModels.modelFor(type, task.modelKey)
            ?: return SubagentReport(label, "Error: the model for $label has no saved API key. Pick another model.")
        val subagentId = newId()
        recorder.subagentStarted(SubagentStart(subagentId, group.parentToolCallId, index, type.name, task.task, model.key))

        val progress = SubagentProgress()
        val loop = buildLoop(subagentId, label, type, task, model, group, context, progress)
        val outcome = try {
            withTimeoutOrNull(limits.timeLimit) { loop.run(PromptBuilder("").userMessageWithContext(task.task, now())) }
                ?: progress.stoppedEarly(SubagentStop.TIME_LIMIT)
        } catch (cancellation: CancellationException) {
            // Stop: the card must not stay "running"; NonCancellable lets the save finish.
            withContext(NonCancellable) {
                val stopped = progress.stoppedEarly(SubagentStop.STOPPED)
                recorder.subagentFinished(subagentId, stopped, SubagentPrompt.resultText(stopped, limits))
            }
            throw cancellation
        }
        val fullText = SubagentPrompt.resultText(outcome, limits)
        val answerPath = "${group.folder}/${label.replace(' ', '-')}.md"
        val answerText = context.outputLimiter.limitInto(fullText, MAX_ANSWER_CHARACTERS, answerPath)
        recorder.subagentFinished(subagentId, outcome, answerText)
        return SubagentReport(label, answerText)
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
        val helpers = mutableListOf<Tool>(RequestTool(), AskParentTool(parentAsker, label, group.parentToolCallId))
        if (group.size > 1) {
            helpers += NotesTool(context.threadFolder, "${group.folder}/notes.md", label, group.notesLock)
        }
        val startTools = threadToolsAtStart + helpers
        return SubagentLoop(
            subagentId = subagentId,
            model = model,
            systemPrompt = SubagentPrompt.systemPrompt(type, startTools, limits, model.hasKnownPrice, memorySection, skillSection),
            toolbox = SubagentToolbox(startTools, requestableTools = usableTools - threadToolsAtStart.toSet()),
            toolContext = ToolContext(context.threadFolder, context.httpClient, context.skillLibraryFolder),
            gate = SubagentGate(broker, label, timer),
            recorder = recorder,
            limits = limits,
            progress = progress,
        )
    }

    /** Call ids come from the provider; only letters, digits, '-' and '_' go into a folder name. */
    private fun safeFileName(id: String): String = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

    companion object {
        /** Relative to the thread folder. */
        const val DELEGATIONS_FOLDER = "work/delegations"

        /** The user's ruling: 16 KB per answer, the rest saved beside it (D-060). */
        const val MAX_ANSWER_CHARACTERS = 16 * 1024
    }
}
