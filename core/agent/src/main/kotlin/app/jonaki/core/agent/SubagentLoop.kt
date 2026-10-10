package app.jonaki.core.agent

import app.jonaki.core.guardapi.Guard
import app.jonaki.core.guardapi.GuardCallContext
import app.jonaki.core.guardapi.NoGuard
import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.model.ToolCall
import app.jonaki.core.providerapi.ChatRequest
import app.jonaki.core.providerapi.ToolDefinition
import app.jonaki.core.providerapi.Usage
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import app.jonaki.core.toolapi.stringArgument
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * The tools one subagent has: those it started with, and those of the
 * thread it may still ask for with request_tool.
 */
internal class SubagentToolbox(startTools: List<Tool>, requestableTools: List<Tool>) {
    private val active = startTools.associateBy { tool -> tool.name }.toMutableMap()
    private val requestable = requestableTools.associateBy { tool -> tool.name }

    fun active(name: String): Tool? = active[name]

    fun requestable(name: String): Tool? = requestable[name]?.takeIf { name !in active }

    fun activate(tool: Tool) {
        active[tool.name] = tool
    }

    val activeNames: List<String>
        get() = active.keys.sorted()

    val requestableNames: List<String>
        get() = requestable.keys.filter { name -> name !in active }.sorted()

    /** The same bytes on every request until a tool is granted. */
    fun definitions(): List<ToolDefinition> = ToolDefinitions.of(active.values)
}

/**
 * Runs one subagent's task to its answer (M7): like the thread's agent loop,
 * but within its step and cost budget, with no approval cards (see
 * [SubagentPermissions]), and with request_tool answered here. Its
 * read-only calls of one turn run side by side, like the thread agent's
 * (D-080). Everything it does goes into [progress] as it happens, so a
 * subagent stopped from outside still has its partial work.
 */
internal class SubagentLoop(
    private val subagentId: String,
    private val model: SubagentModel,
    /** Built once per subagent; never changes between its requests (D-005). */
    private val systemPrompt: String,
    private val toolbox: SubagentToolbox,
    private val toolContext: ToolContext,
    private val recorder: SubagentRecorder,
    private val limits: SubagentLimits,
    private val progress: SubagentProgress,
    timer: WaitTimer,
    /** Screens the outside results this subagent reads, as for the thread's agent. */
    private val guard: Guard = NoGuard,
    /** Settings > Guardrails "Ask before sending out"; the address rule follows it. Read before every call. */
    private val asksAfterOutsideContent: () -> Boolean = { true },
    /**
     * True once the thread that started this subagent has read outside content.
     * Its task was then written by a model that may have been steered, so the
     * task's addresses do not count as known.
     */
    private val threadReadOutsideContent: () -> Boolean = { false },
) {
    private val scheduler = ToolCallScheduler(timer)

    // Text a contacted address may appear in: the task (when trusted) and every result this subagent read.
    private val textsWithKnownAddresses = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val readOutsideContent = java.util.concurrent.atomic.AtomicBoolean(false)

    suspend fun run(taskMessage: String): SubagentOutcome {
        val conversation = mutableListOf(Message(Role.USER, taskMessage))
        if (!threadReadOutsideContent()) {
            textsWithKnownAddresses += taskMessage
        }
        var stepLimitNoticeSent = false
        var retried = false
        while (true) {
            val stepsUsedUp = progress.toolSteps >= limits.maxToolSteps
            if (stepsUsedUp && !stepLimitNoticeSent) {
                conversation += Message(Role.USER, stepLimitNotice())
                stepLimitNoticeSent = true
            }
            val tools = if (stepsUsedUp) emptyList() else toolbox.definitions()
            val turn = streamTurn(model.provider, request(conversation, tools), onTextDelta = {}, onReasoningDelta = {})
            if (turn is TurnResult.Failed) {
                if (turn.retryable && !retried) {
                    retried = true
                    delay(limits.retryDelay)
                    continue
                }
                return progress.stoppedEarly(SubagentStop.FAILED, failure = turn.message)
            }
            val answered = turn as TurnResult.Answered
            // One retry per turn, as for the thread's agent (D-026).
            retried = false
            recordUsage(answered.usage)
            conversation += answered.message
            recordText(answered.message.text)

            if (stepsUsedUp) {
                return progress.finished(SubagentStop.STEP_LIMIT, answered.message.text)
            }
            if (answered.message.toolCalls.isEmpty()) {
                return progress.finished(SubagentStop.COMPLETED, answered.message.text)
            }
            if (costCapReached()) {
                return progress.stoppedEarly(SubagentStop.COST_LIMIT)
            }
            conversation += runToolCalls(answered.message.toolCalls)
        }
    }

    private fun request(conversation: List<Message>, tools: List<ToolDefinition>): ChatRequest = ChatRequest(
        model = model.modelId,
        systemPrompt = systemPrompt,
        messages = model.imageMessages?.prepare(conversation) ?: conversation.toList(),
        tools = tools,
        thinkingLevel = model.thinkingLevel,
    )

    private suspend fun recordUsage(usage: Usage?) {
        if (usage == null) {
            return
        }
        val cost = model.priceOf(usage)
        progress.addCost(cost)
        recorder.modelCallFinished(subagentId, model.key, usage, cost)
    }

    private suspend fun recordText(text: String) {
        if (text.isBlank()) {
            return
        }
        progress.addText(text)
        recorder.textWritten(subagentId, text)
    }

    /** Only known costs count; a model without a price has only the step limit. */
    private fun costCapReached(): Boolean {
        val spent = progress.costUsd ?: return false
        // Sums of dollar amounts carry rounding errors; 0.01 + 0.08 + 0.01 is just under 0.10.
        return spent >= limits.costCapUsd - ROUNDING_TOLERANCE_USD
    }

    private suspend fun runToolCalls(toolCalls: List<ToolCall>): List<Message> {
        val (toRun, notRun) = splitByStepBudget(toolCalls)
        val results = scheduler.runAll(
            toolCalls = toRun,
            runsAlongsideOthers = { toolCall -> ToolCallScheduler.readsOnly(toolbox.active(toolCall.toolName), toolCall) },
            run = ::runToolCall,
            inCallOrder = { _, _ -> },
        )
        // Calls beyond the budget in the same turn get a result, which every provider requires.
        val notRunResults = notRun.map { toolCall ->
            Message(Role.TOOL, "Not run: the step limit of ${limits.maxToolSteps} is used up.", toolCallId = toolCall.id)
        }
        return results + notRunResults
    }

    /**
     * The calls that fit the steps left, and the rest. Decided before any
     * call starts, so that calls running side by side cannot both take the
     * last step. A notes call costs no step, so it always runs; its own
     * ceiling is checked when it runs.
     */
    private fun splitByStepBudget(toolCalls: List<ToolCall>): Pair<List<ToolCall>, List<ToolCall>> {
        var stepsLeft = (limits.maxToolSteps - progress.toolSteps).coerceAtLeast(0)
        val toRun = mutableListOf<ToolCall>()
        val notRun = mutableListOf<ToolCall>()
        for (toolCall in toolCalls) {
            if (toolCall.toolName == NotesTool.NAME) {
                toRun += toolCall
            } else if (stepsLeft > 0) {
                stepsLeft -= 1
                toRun += toolCall
            } else {
                notRun += toolCall
            }
        }
        return toRun to notRun
    }

    private suspend fun runToolCall(toolCall: ToolCall): Message {
        // Saved under the subagent's id, because two subagents' providers may hand out the same call ids.
        val stepCall = toolCall.copy(id = "$subagentId/${toolCall.id}")
        val overNotesCeiling = countAndCheckNotesCeiling(toolCall)
        recorder.stepStarted(subagentId, stepCall)
        val result = if (overNotesCeiling) notesCeilingReached() else resultOf(toolCall, stepCall)
        recorder.stepFinished(subagentId, stepCall, result.output, result.status)
        progress.addToolResult(toolCall.toolName, result.output.text)
        // The model keeps its own id, so the result matches its call.
        return Message(Role.TOOL, result.textForModel, toolCallId = toolCall.id)
    }

    /**
     * Counts the call: a step, or for the notes board a note call, which
     * costs no step. True when this is a notes call beyond the ceiling.
     */
    private fun countAndCheckNotesCeiling(toolCall: ToolCall): Boolean {
        if (toolCall.toolName != NotesTool.NAME) {
            progress.countToolStep()
            return false
        }
        return progress.countNotesCall() > limits.maxNotesCalls
    }

    private fun notesCeilingReached(): StepResult = failed(
        ToolOutput.error(
            "you have used the notes board ${limits.maxNotesCalls} times",
            "Continue with your task using your other tools, or answer now with what you have.",
        ),
    )

    /** [output] is what the card and the progress show; [textForModel] is the same text, wrapped when it is outside content. */
    private class StepResult(
        val output: ToolOutput,
        val status: SubagentStepStatus,
        val textForModel: String = output.text,
    )

    private suspend fun resultOf(toolCall: ToolCall, stepCall: ToolCall): StepResult {
        val arguments = parseToolArguments(toolCall.argumentsJson)
            ?: return failed(
                ToolOutput.error(
                    "the arguments for ${toolCall.toolName} are not a JSON object",
                    "Call ${toolCall.toolName} again with arguments that match its schema.",
                ),
            )
        if (toolCall.toolName == RequestTool.NAME) {
            return requestTool(arguments)
        }
        val tool = toolbox.active(toolCall.toolName)
            ?: return failed(
                ToolOutput.error(
                    "you have no tool named ${toolCall.toolName}",
                    "Your tools: ${toolbox.activeNames.joinToString(", ")}. request_tool can add: " +
                        "${toolbox.requestableNames.joinToString(", ").ifEmpty { "nothing" }}.",
                ),
            )
        if (!SubagentPermissions.mayRun(tool, arguments)) {
            return failed(SubagentPermissions.notAvailable(tool.name))
        }
        val unknownAddress = unknownAddressOf(tool, arguments)
        if (unknownAddress != null) {
            return failed(SubagentPermissions.addressNotKnown(tool.name, unknownAddress))
        }
        val output = runToolWithTimeLimit(tool, arguments, toolContext.forCall(stepCall.id))
        // The wrapper keeps a web page's own instructions from reading as the subagent's task.
        val wrapped = withContext(GuardCallContext(stepCall.id)) { OutsideContent.wrapResult(tool, arguments, output, guard) }
        textsWithKnownAddresses += output.text
        if (wrapped.isOutsideContent) {
            readOutsideContent.set(true)
        }
        return StepResult(
            output,
            if (output.isError) SubagentStepStatus.FAILED else SubagentStepStatus.DONE,
            textForModel = wrapped.textForModel,
        )
    }

    /**
     * The address [tool] would contact when that is not allowed: outside
     * content was read (by the thread or by this subagent) and the address
     * appeared nowhere known. The same rule as the thread's broker applies,
     * with a refusal in place of a card.
     */
    private fun unknownAddressOf(tool: Tool, arguments: JsonObject): String? {
        val ruleApplies = asksAfterOutsideContent() && (threadReadOutsideContent() || readOutsideContent.get())
        if (!ruleApplies) {
            return null
        }
        val address = tool.contactedAddressOf(arguments) ?: return null
        val isKnown = textsWithKnownAddresses.any { text -> WebAddresses.appearsIn(address, text) }
        return if (isKnown) null else address
    }

    /**
     * request_tool: every tool of the thread may be added without a card,
     * because the consent is the delegate call. The tool's calls that leave
     * the app are still refused one by one when they come.
     */
    private fun requestTool(arguments: JsonObject): StepResult {
        val name = arguments.stringArgument("name")?.trim().orEmpty()
        if (name.isEmpty()) {
            return failed(ToolOutput.error("request_tool needs a name", "Call it again with name and reason."))
        }
        if (toolbox.active(name) != null) {
            return StepResult(ToolOutput.success("$name is already one of your tools."), SubagentStepStatus.DONE)
        }
        val tool = toolbox.requestable(name)
            ?: return failed(
                ToolOutput.error(
                    "$name cannot be added",
                    "Tools you can request: ${toolbox.requestableNames.joinToString(", ").ifEmpty { "none" }}.",
                ),
            )
        toolbox.activate(tool)
        return StepResult(ToolOutput.success("Granted: $name is one of your tools from your next step."), SubagentStepStatus.DONE)
    }

    private fun failed(output: ToolOutput): StepResult = StepResult(output, SubagentStepStatus.FAILED)

    private fun stepLimitNotice(): String =
        "[Your step limit of ${limits.maxToolSteps} tool steps is used up. Answer now with what you have, " +
            "without calling tools, and say what is still missing.]"

    private companion object {
        const val ROUNDING_TOLERANCE_USD = 1e-9
    }
}
