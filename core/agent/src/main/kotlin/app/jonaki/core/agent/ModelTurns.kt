package app.jonaki.core.agent

import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.model.ToolCall
import app.jonaki.core.providerapi.ChatProvider
import app.jonaki.core.providerapi.ChatRequest
import app.jonaki.core.providerapi.StreamEvent
import app.jonaki.core.providerapi.Usage
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** One model turn as the thread's agent loop and the subagent loop see it. */
internal sealed interface TurnResult {
    data class Answered(val message: Message, val usage: Usage?) : TurnResult

    data class Failed(val message: String, val retryable: Boolean) : TurnResult
}

/**
 * Streams one model turn. Text and reasoning go to the callbacks as they
 * arrive; cancellation reaches the caller unchanged.
 */
internal suspend fun streamTurn(
    provider: ChatProvider,
    request: ChatRequest,
    onTextDelta: suspend (String) -> Unit,
    onReasoningDelta: suspend (String) -> Unit,
): TurnResult {
    val text = StringBuilder()
    val toolCalls = mutableListOf<ToolCall>()
    var finished: StreamEvent.Finished? = null
    var failed: StreamEvent.Failed? = null
    provider.stream(request).collect { event ->
        when (event) {
            is StreamEvent.TextDelta -> {
                text.append(event.text)
                onTextDelta(event.text)
            }
            is StreamEvent.ReasoningDelta -> onReasoningDelta(event.text)
            is StreamEvent.ToolCallReady -> toolCalls += event.toolCall
            is StreamEvent.Finished -> finished = event
            is StreamEvent.Failed -> failed = event
        }
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

/** Runs [tool] within its time limit; a crash or a timeout becomes an error text for the model. */
internal suspend fun runToolWithTimeLimit(tool: Tool, arguments: JsonObject, context: ToolContext): ToolOutput {
    val output = try {
        withTimeoutOrNull(tool.timeLimit) { tool.run(arguments, context) }
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

/** The call's arguments as a JSON object; null when they are not one. */
internal fun parseToolArguments(argumentsJson: String): JsonObject? {
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
