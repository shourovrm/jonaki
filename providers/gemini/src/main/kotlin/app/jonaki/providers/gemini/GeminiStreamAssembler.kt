package app.jonaki.providers.gemini

import app.jonaki.core.model.ToolCall
import app.jonaki.core.providerapi.FinishReason
import app.jonaki.core.providerapi.StreamEvent
import app.jonaki.core.providerapi.Usage
import app.jonaki.core.providerapi.isRetryableHttpStatus
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Turns the chunks of a Gemini `streamGenerateContent` stream into
 * [StreamEvent]s. Unlike the OpenAI format, Gemini sends each function call
 * whole, so it is emitted as soon as it arrives.
 */
class GeminiStreamAssembler {
    private var finishReason: FinishReason? = null
    private var usage: Usage? = null
    private var sawFunctionCall = false
    private var ended = false

    fun accept(payload: String): List<StreamEvent> {
        if (ended) return emptyList()
        val chunk = Json.parseToJsonElement(payload).jsonObject

        val error = chunk.objectOrNull("error")
        if (error != null) {
            ended = true
            return listOf(failureFromError(error))
        }
        val blockReason = chunk.objectOrNull("promptFeedback")?.stringOrNull("blockReason")
        if (blockReason != null) {
            ended = true
            return listOf(StreamEvent.Failed("Gemini refused the request (block reason: $blockReason)", retryable = false))
        }

        chunk.objectOrNull("usageMetadata")?.let { usage = usageFrom(it) }

        val candidate = chunk.arrayOrNull("candidates")?.firstOrNull() as? JsonObject ?: return emptyList()
        val events = mutableListOf<StreamEvent>()
        val parts = candidate.objectOrNull("content")?.arrayOrNull("parts")
        if (parts != null) {
            for (part in parts) {
                events += eventsFromPart(part.jsonObject)
            }
        }
        candidate.stringOrNull("finishReason")?.let { finishReason = finishReasonFrom(it) }
        return events
    }

    fun finish(): List<StreamEvent> {
        if (ended) return emptyList()
        ended = true
        val reason = finishReason
            ?: return listOf(StreamEvent.Failed("The stream ended before Gemini finished its reply", retryable = true))
        // Gemini reports STOP even when the turn ends in function calls.
        val effectiveReason = if (sawFunctionCall && reason == FinishReason.STOP) FinishReason.TOOL_CALLS else reason
        return listOf(StreamEvent.Finished(effectiveReason, usage))
    }

    private fun eventsFromPart(part: JsonObject): List<StreamEvent> {
        val functionCall = part.objectOrNull("functionCall")
        if (functionCall != null) {
            sawFunctionCall = true
            val callId = functionCall.stringOrNull("id") ?: ("gemini_" + UUID.randomUUID().toString().take(12))
            val encodedId = GeminiToolCallId.encode(callId, part.stringOrNull("thoughtSignature"))
            val name = functionCall.stringOrNull("name").orEmpty()
            val arguments = functionCall.objectOrNull("args")?.toString() ?: "{}"
            return listOf(StreamEvent.ToolCallReady(ToolCall(encodedId, name, arguments)))
        }
        val text = part.stringOrNull("text")
        if (text.isNullOrEmpty()) return emptyList()
        if (part.isTrue("thought")) return listOf(StreamEvent.ReasoningDelta(text))
        return listOf(StreamEvent.TextDelta(text))
    }

    private fun usageFrom(metadata: JsonObject): Usage {
        // Thinking tokens are billed as output.
        val outputTokens = (metadata.intOrNull("candidatesTokenCount") ?: 0) + (metadata.intOrNull("thoughtsTokenCount") ?: 0)
        return Usage(
            inputTokens = metadata.intOrNull("promptTokenCount") ?: 0,
            outputTokens = outputTokens,
            cachedInputTokens = metadata.intOrNull("cachedContentTokenCount"),
        )
    }

    private fun finishReasonFrom(reason: String): FinishReason = when (reason) {
        "STOP" -> FinishReason.STOP
        "MAX_TOKENS" -> FinishReason.LENGTH
        else -> FinishReason.OTHER
    }
}

internal fun failureFromError(error: JsonObject): StreamEvent.Failed {
    val statusCode = error.intOrNull("code")
    val message = error.stringOrNull("message") ?: error.toString()
    val retryable = statusCode != null && isRetryableHttpStatus(statusCode)
    val label = listOfNotNull(statusCode?.toString(), error.stringOrNull("status")).joinToString(" ")
    return StreamEvent.Failed("Gemini answered $label: $message", retryable)
}
