package app.jonaki.providers.localllama

import app.jonaki.core.model.ToolCall
import app.jonaki.core.providerapi.FinishReason
import app.jonaki.core.providerapi.StreamEvent
import app.jonaki.core.providerapi.Usage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Turns the JNI layer's output into the same [StreamEvent]s the
 * OpenAI-compatible provider emits. The native parser reports the whole
 * reply so far after each token; this sends only what is new. Tool calls are
 * emitted once, from the final parse, like the other providers do.
 */
class LocalReplyAssembler(private val newToolCallId: () -> String) {
    private var sentContent = ""
    private var sentReasoning = ""

    /** The new text and reasoning in a snapshot of the reply so far. */
    fun snapshot(content: String, reasoning: String): List<StreamEvent> {
        val events = mutableListOf<StreamEvent>()
        val newReasoning = addedPart(sentReasoning, reasoning)
        if (newReasoning != null) {
            sentReasoning = reasoning
            events += StreamEvent.ReasoningDelta(newReasoning)
        }
        val newContent = addedPart(sentContent, content)
        if (newContent != null) {
            sentContent = content
            events += StreamEvent.TextDelta(newContent)
        }
        return events
    }

    /** The closing events from the native result JSON; empty for a cancelled turn. */
    fun finish(resultJson: String): List<StreamEvent> {
        val result = Json.parseToJsonElement(resultJson).jsonObject
        val finish = result.text("finish")
        if (finish == "cancelled") {
            return emptyList()
        }
        if (finish == "error") {
            val message = result.text("error") ?: "llama.cpp failed without a message"
            return listOf(StreamEvent.Failed(message, retryable = false))
        }
        val events = snapshot(result.text("content").orEmpty(), result.text("reasoning").orEmpty()).toMutableList()
        val usage = Usage(
            inputTokens = result.number("prompt_tokens"),
            outputTokens = result.number("completion_tokens"),
            cachedInputTokens = result.number("cached_tokens"),
            // The model runs on the phone, so a turn costs nothing.
            costUsd = 0.0,
        )
        if (finish == "length") {
            // A tool call cut off by the limit has incomplete arguments, so none are run.
            return events + StreamEvent.Finished(FinishReason.LENGTH, usage)
        }
        val toolCalls = toolCallsOf(result)
        events += toolCalls.map { toolCall -> StreamEvent.ToolCallReady(toolCall) }
        val reason = if (toolCalls.isEmpty()) FinishReason.STOP else FinishReason.TOOL_CALLS
        return events + StreamEvent.Finished(reason, usage)
    }

    private fun toolCallsOf(result: JsonObject): List<ToolCall> {
        val calls = result["tool_calls"] as? JsonArray ?: return emptyList()
        return calls.map { element ->
            val call = element.jsonObject
            val id = call.text("id").orEmpty().ifEmpty { newToolCallId() }
            val arguments = call.text("arguments").orEmpty().ifBlank { "{}" }
            ToolCall(id, call.text("name").orEmpty(), arguments)
        }
    }

    /**
     * The part of [now] after [sent], or null when nothing was added. When
     * the parser revises earlier text (rare, mid-structure), nothing is sent
     * until the reply grows past what was already shown again.
     */
    private fun addedPart(sent: String, now: String): String? {
        if (now.length <= sent.length || !now.startsWith(sent)) {
            return null
        }
        return now.substring(sent.length)
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.number(key: String): Int = (this[key] as? JsonPrimitive)?.intOrNull ?: 0
}
