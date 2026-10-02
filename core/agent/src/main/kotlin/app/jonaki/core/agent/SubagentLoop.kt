package app.jonaki.core.agent

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
 * but within its step and cost budget, with the 3-minute approval rule
 * through its [SubagentGate], and with request_tool answered here. Tool
 * calls run one after another. Everything it does goes into [progress] as
 * it happens, so a subagent stopped from outside still has its partial work.
 */
internal class SubagentLoop(
    private val subagentId: String,
    private val model: SubagentModel,
    /** Built once per subagent; never changes between its requests (D-005). */
    private val systemPrompt: String,
    private val toolbox: SubagentToolbox,
    private val toolContext: ToolContext,
    private val gate: SubagentGate,
    private val recorder: SubagentRecorder,
    private val limits: SubagentLimits,
    private val progress: SubagentProgress,
) {
    suspend fun run(taskMessage: String): SubagentOutcome {
        val conversation = mutableListOf(Message(Role.USER, taskMessage))
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
            for (toolCall in answered.message.toolCalls) {
                conversation += runToolCall(toolCall)
            }
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

    private suspend fun runToolCall(toolCall: ToolCall): Message {
        if (progress.toolSteps >= limits.maxToolSteps) {
            // Calls beyond the budget in the same turn get a result, which every provider requires.
            return Message(Role.TOOL, "Not run: the step limit of ${limits.maxToolSteps} is used up.", toolCallId = toolCall.id)
        }
        // Saved under the subagent's id, because two subagents' providers may hand out the same call ids.
        val stepCall = toolCall.copy(id = "$subagentId/${toolCall.id}")
        progress.countToolStep()
        recorder.stepStarted(subagentId, stepCall)
        val result = resultOf(toolCall, stepCall)
        recorder.stepFinished(subagentId, stepCall, result.output, result.status)
        progress.addToolResult(toolCall.toolName, result.output.text)
        // The model keeps its own id, so the result matches its call.
        return Message(Role.TOOL, result.output.text, toolCallId = toolCall.id)
    }

    private class StepResult(val output: ToolOutput, val status: SubagentStepStatus)

    private suspend fun resultOf(toolCall: ToolCall, stepCall: ToolCall): StepResult {
        val arguments = parseToolArguments(toolCall.argumentsJson)
            ?: return failed(
                ToolOutput.error(
                    "the arguments for ${toolCall.toolName} are not a JSON object",
                    "Call ${toolCall.toolName} again with arguments that match its schema.",
                ),
            )
        if (toolCall.toolName == RequestTool.NAME) {
            return requestTool(arguments, stepCall)
        }
        val tool = toolbox.active(toolCall.toolName)
            ?: return failed(
                ToolOutput.error(
                    "you have no tool named ${toolCall.toolName}",
                    "Your tools: ${toolbox.activeNames.joinToString(", ")}. request_tool can add: " +
                        "${toolbox.requestableNames.joinToString(", ").ifEmpty { "nothing" }}.",
                ),
            )
        return when (gate.check(tool, stepCall)) {
            GateAnswer.ALLOWED -> {
                val output = runToolWithTimeLimit(tool, arguments, toolContext.forCall(stepCall.id))
                StepResult(output, if (output.isError) SubagentStepStatus.FAILED else SubagentStepStatus.DONE)
            }
            GateAnswer.DENIED -> StepResult(
                ToolOutput.error("the user denied ${tool.name}", "Do not retry it; continue without it, or stop and report."),
                SubagentStepStatus.DENIED,
            )
            GateAnswer.SKIPPED -> skipped("${tool.name}: ${describeCall(arguments)}")
        }
    }

    private suspend fun requestTool(arguments: JsonObject, stepCall: ToolCall): StepResult {
        val name = arguments.stringArgument("name")?.trim().orEmpty()
        val reason = arguments.stringArgument("reason")?.trim().orEmpty()
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
        return when (gate.grant(tool, stepCall, reason)) {
            GateAnswer.ALLOWED -> {
                toolbox.activate(tool)
                StepResult(ToolOutput.success("Granted: $name is one of your tools from your next step."), SubagentStepStatus.DONE)
            }
            GateAnswer.DENIED -> StepResult(
                ToolOutput.error("the user refused $name", "Continue without it, or stop and report what you could not do."),
                SubagentStepStatus.DENIED,
            )
            GateAnswer.SKIPPED -> skipped("request_tool $name: $reason")
        }
    }

    private fun skipped(part: String): StepResult {
        progress.addSkipped(part)
        val output = ToolOutput.error(
            "nobody answered the approval within 3 minutes, so this part is skipped",
            "Continue with the other parts of the task, or stop and report what you have. " +
                "The skipped part is listed in your result.",
        )
        return StepResult(output, SubagentStepStatus.SKIPPED)
    }

    private fun failed(output: ToolOutput): StepResult = StepResult(output, SubagentStepStatus.FAILED)

    /** A short name for a skipped call: its path, link or query, else its arguments. */
    private fun describeCall(arguments: JsonObject): String {
        for (key in listOf("path", "url", "query", "action")) {
            val value = arguments.stringArgument(key)?.trim()
            if (!value.isNullOrEmpty()) {
                return value
            }
        }
        return arguments.toString().take(DESCRIPTION_CHARACTERS)
    }

    private fun stepLimitNotice(): String =
        "[Your step limit of ${limits.maxToolSteps} tool steps is used up. Answer now with what you have, " +
            "without calling tools, and say what is still missing.]"

    private companion object {
        const val DESCRIPTION_CHARACTERS = 80

        const val ROUNDING_TOLERANCE_USD = 1e-9
    }
}
