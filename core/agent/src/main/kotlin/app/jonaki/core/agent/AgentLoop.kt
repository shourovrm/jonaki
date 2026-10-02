package app.jonaki.core.agent

import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.model.ToolCall
import app.jonaki.core.providerapi.ChatProvider
import app.jonaki.core.providerapi.ChatRequest
import app.jonaki.core.providerapi.StreamEvent
import app.jonaki.core.providerapi.ThinkingLevel
import app.jonaki.core.providerapi.ToolDefinition
import app.jonaki.core.providerapi.Usage
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

data class AgentSettings(
    val model: String,
    val systemPrompt: String,
    /** Model turns that may call tools for one user message (D-005). */
    val stepBudget: Int = 15,
    val maxOutputTokens: Int? = null,
    /** Null leaves the model's own default (D-057). */
    val thinkingLevel: ThinkingLevel? = null,
)

/**
 * Runs one user message to its answer: ask the model, run the tools it
 * calls, send the results back, and repeat until it answers in text. Every
 * step goes to the [recorder] as it happens. Cancelling the calling coroutine
 * is the Stop button: the partial answer is recorded, then the cancellation
 * continues to the caller.
 */
class AgentLoop(
    private val provider: ChatProvider,
    tools: List<Tool>,
    private val toolContext: ToolContext,
    private val permissionBroker: PermissionBroker,
    private val recorder: StepRecorder,
    private val settings: AgentSettings,
) {
    private val toolsByName: Map<String, Tool> = tools.associateBy { tool -> tool.name }

    // Sorted like the system prompt, so the request bytes stay stable for the prompt cache.
    private val toolDefinitions: List<ToolDefinition> = tools.sortedBy { tool -> tool.name }.map { tool ->
        ToolDefinition(name = tool.name, description = tool.promptLine, parameterSchema = tool.parameterSchema)
    }

    suspend fun run(history: List<Message>): RunOutcome {
        val conversation = history.toMutableList()
        var toolTurnsUsed = 0
        while (true) {
            val budgetUsedUp = toolDefinitions.isNotEmpty() && toolTurnsUsed >= settings.stepBudget
            if (budgetUsedUp) {
                conversation += Message(Role.USER, BUDGET_NOTICE)
            }
            val toolsForThisTurn = if (budgetUsedUp) emptyList() else toolDefinitions

            val turn = streamOneTurn(conversation, toolsForThisTurn)
            if (turn is TurnResult.Failed) {
                return finish(RunOutcome.ProviderFailed(turn.message, turn.retryable))
            }
            val answered = turn as TurnResult.Answered
            conversation += answered.message
            recorder.record(AgentEvent.AssistantMessage(answered.message, answered.usage))

            if (budgetUsedUp) {
                // Tool calls are ignored here: the model was offered no tools for this turn.
                return finish(RunOutcome.BudgetReached(answered.message.text))
            }
            if (answered.message.toolCalls.isEmpty()) {
                return finish(RunOutcome.Completed(answered.message.text))
            }

            toolTurnsUsed += 1
            for (toolCall in answered.message.toolCalls) {
                conversation += runToolCall(toolCall)
            }
        }
    }

    private suspend fun finish(outcome: RunOutcome): RunOutcome {
        recorder.record(AgentEvent.RunFinished(outcome))
        return outcome
    }

    private sealed interface TurnResult {
        data class Answered(val message: Message, val usage: Usage?) : TurnResult

        data class Failed(val message: String, val retryable: Boolean) : TurnResult
    }

    private suspend fun streamOneTurn(conversation: List<Message>, tools: List<ToolDefinition>): TurnResult {
        val request = ChatRequest(
            model = settings.model,
            systemPrompt = settings.systemPrompt,
            messages = conversation.toList(),
            tools = tools,
            maxOutputTokens = settings.maxOutputTokens,
            thinkingLevel = settings.thinkingLevel,
        )
        val text = StringBuilder()
        val toolCalls = mutableListOf<ToolCall>()
        var finished: StreamEvent.Finished? = null
        var failed: StreamEvent.Failed? = null
        try {
            provider.stream(request).collect { event ->
                when (event) {
                    is StreamEvent.TextDelta -> {
                        text.append(event.text)
                        recorder.record(AgentEvent.TextDelta(event.text))
                    }
                    is StreamEvent.ReasoningDelta -> recorder.record(AgentEvent.ReasoningDelta(event.text))
                    is StreamEvent.ToolCallReady -> toolCalls += event.toolCall
                    is StreamEvent.Finished -> finished = event
                    is StreamEvent.Failed -> failed = event
                }
            }
        } catch (cancellation: CancellationException) {
            recordStop(text.toString())
            throw cancellation
        }

        val failure = failed
        if (failure != null) {
            return TurnResult.Failed(failure.message, failure.retryable)
        }
        val finish = finished
            ?: return TurnResult.Failed("the model's reply stopped before it finished", retryable = true)
        val message = Message(role = Role.ASSISTANT, text = text.toString(), toolCalls = toolCalls.toList())
        return TurnResult.Answered(message, finish.usage)
    }

    private suspend fun recordStop(partialText: String) {
        // The coroutine is already cancelled; NonCancellable lets the last saves finish.
        withContext(NonCancellable) {
            if (partialText.isNotEmpty()) {
                recorder.record(AgentEvent.AssistantMessage(Message(Role.ASSISTANT, partialText), usage = null))
            }
            recorder.record(AgentEvent.RunFinished(RunOutcome.Stopped(partialText)))
        }
    }

    private suspend fun runToolCall(toolCall: ToolCall): Message {
        recorder.record(AgentEvent.ToolStarted(toolCall))
        val output = try {
            outputFor(toolCall)
        } catch (cancellation: CancellationException) {
            recordStop(partialText = "")
            throw cancellation
        }
        val message = Message(role = Role.TOOL, text = output.text, toolCallId = toolCall.id)
        recorder.record(AgentEvent.ToolFinished(toolCall, output, message))
        return message
    }

    private suspend fun outputFor(toolCall: ToolCall): ToolOutput {
        val tool = toolsByName[toolCall.toolName]
            ?: return ToolOutput.error(
                "there is no tool named ${toolCall.toolName}",
                "Available tools: ${toolsByName.keys.sorted().joinToString(", ")}.",
            )
        val arguments = parseArguments(toolCall.argumentsJson)
            ?: return ToolOutput.error(
                "the arguments for ${tool.name} are not a JSON object",
                "Call ${tool.name} again with arguments that match its schema.",
            )
        if (!permissionBroker.mayRun(tool, toolCall)) {
            return ToolOutput.error(
                "the user denied ${tool.name}",
                "Do not retry it; continue without it or ask the user what to do instead.",
            )
        }
        return runWithTimeLimit(tool, arguments)
    }

    private suspend fun runWithTimeLimit(tool: Tool, arguments: JsonObject): ToolOutput {
        val output = try {
            withTimeoutOrNull(tool.timeLimit) { tool.run(arguments, toolContext) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            return ToolOutput.error(
                "${tool.name} failed with ${exception::class.simpleName}: ${exception.message}",
                "Check the arguments, or try a different approach.",
            )
        }
        return output ?: ToolOutput.error(
            "${tool.name} did not finish within its time limit of ${tool.timeLimit}",
            "Try a smaller request, or a different tool.",
        )
    }

    private fun parseArguments(argumentsJson: String): JsonObject? {
        // Some models send an empty string for a call without arguments.
        if (argumentsJson.isBlank()) {
            return JsonObject(emptyMap())
        }
        return try {
            Json.parseToJsonElement(argumentsJson).jsonObject
        } catch (exception: SerializationException) {
            null
        } catch (exception: IllegalArgumentException) {
            null
        }
    }

    private companion object {
        const val BUDGET_NOTICE =
            "[The step budget for this message is used up. Answer now with what you have found, " +
                "without calling tools, and say what is still missing.]"
    }
}
