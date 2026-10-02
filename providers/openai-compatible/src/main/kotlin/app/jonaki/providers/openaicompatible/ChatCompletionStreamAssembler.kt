package app.jonaki.providers.openaicompatible

import app.jonaki.core.model.ToolCall
import app.jonaki.core.providerapi.FinishReason
import app.jonaki.core.providerapi.StreamEvent
import app.jonaki.core.providerapi.Usage
import app.jonaki.core.providerapi.isRetryableHttpStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Turns the data payloads of a chat completions stream into [StreamEvent]s.
 * Tool call arguments arrive in fragments keyed by index, so a call is only
 * emitted once the model has finished its turn.
 */
class ChatCompletionStreamAssembler {
    private class PartialToolCall(var id: String = "", var name: String = "", val arguments: StringBuilder = StringBuilder())

    private val partialToolCalls = sortedMapOf<Int, PartialToolCall>()
    private var finishReason: FinishReason? = null
    private var usage: Usage? = null
    private var ended = false

    /** Accepts one `data` payload and returns the events it completes. */
    fun accept(payload: String): List<StreamEvent> {
        if (ended) return emptyList()
        if (payload == "[DONE]") return finish()

        val chunk = Json.parseToJsonElement(payload).jsonObject
        val error = chunk["error"]
        if (error is JsonObject) {
            ended = true
            return listOf(failureFrom(error))
        }

        chunk.objectOrNull("usage")?.let { usage = usageFrom(it) }

        val events = mutableListOf<StreamEvent>()
        val choice = (chunk["choices"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return events
        choice.objectOrNull("delta")?.let { delta -> events += eventsFromDelta(delta) }
        choice.stringOrNull("finish_reason")?.let { reason -> finishReason = finishReasonFrom(reason) }
        return events
    }

    /** Called when the stream ends; returns the tool calls and the closing event. */
    fun finish(): List<StreamEvent> {
        if (ended) return emptyList()
        ended = true
        val reason = finishReason
            ?: return listOf(StreamEvent.Failed("The stream ended before the model finished its reply", retryable = true))

        val events = mutableListOf<StreamEvent>()
        for (partial in partialToolCalls.values) {
            val arguments = partial.arguments.toString().ifBlank { "{}" }
            events += StreamEvent.ToolCallReady(ToolCall(partial.id, partial.name, arguments))
        }
        events += StreamEvent.Finished(reason, usage)
        return events
    }

    private fun eventsFromDelta(delta: JsonObject): List<StreamEvent> {
        val events = mutableListOf<StreamEvent>()
        // OpenRouter names the field "reasoning"; DeepSeek names it "reasoning_content".
        val reasoning = delta.stringOrNull("reasoning") ?: delta.stringOrNull("reasoning_content")
        if (!reasoning.isNullOrEmpty()) events += StreamEvent.ReasoningDelta(reasoning)

        val content = delta.stringOrNull("content")
        if (!content.isNullOrEmpty()) events += StreamEvent.TextDelta(content)

        val toolCallFragments = delta["tool_calls"] as? JsonArray ?: return events
        for (fragment in toolCallFragments) {
            collectToolCallFragment(fragment.jsonObject)
        }
        return events
    }

    private fun collectToolCallFragment(fragment: JsonObject) {
        val index = (fragment["index"] as? JsonPrimitive)?.intOrNull ?: partialToolCalls.size
        val partial = partialToolCalls.getOrPut(index) { PartialToolCall() }
        fragment.stringOrNull("id")?.let { partial.id = it }
        val function = fragment.objectOrNull("function") ?: return
        function.stringOrNull("name")?.let { partial.name = it }
        function.stringOrNull("arguments")?.let { partial.arguments.append(it) }
    }

    private fun failureFrom(error: JsonObject): StreamEvent.Failed {
        val message = error.stringOrNull("message") ?: error.toString()
        val statusCode = (error["code"] as? JsonPrimitive)?.intOrNull
        val retryable = statusCode != null && isRetryableHttpStatus(statusCode)
        return StreamEvent.Failed(message, retryable)
    }

    private fun usageFrom(usageObject: JsonObject): Usage {
        val cachedTokens = usageObject.objectOrNull("prompt_tokens_details")?.intOrNull("cached_tokens")
        return Usage(
            inputTokens = usageObject.intOrNull("prompt_tokens") ?: 0,
            outputTokens = usageObject.intOrNull("completion_tokens") ?: 0,
            cachedInputTokens = cachedTokens,
            costUsd = (usageObject["cost"] as? JsonPrimitive)?.doubleOrNull,
        )
    }

    private fun finishReasonFrom(reason: String): FinishReason = when (reason) {
        "stop" -> FinishReason.STOP
        "tool_calls", "function_call" -> FinishReason.TOOL_CALLS
        "length" -> FinishReason.LENGTH
        else -> FinishReason.OTHER
    }
}

internal fun JsonObject.objectOrNull(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.stringOrNull(key: String): String? {
    val element: JsonElement? = this[key]
    if (element == null || element is JsonNull) return null
    return (element as? JsonPrimitive)?.content
}

internal fun JsonObject.intOrNull(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
