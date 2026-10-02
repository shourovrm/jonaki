package app.jonaki.providers.gemini

import app.jonaki.core.providerapi.Usage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

data class VideoSummaryRequest(
    val videoUrl: String,
    val prompt: String,
    val model: String = GeminiProvider.DEFAULT_MODEL,
    val startSeconds: Int? = null,
    val endSeconds: Int? = null,
    /**
     * Asks for fewer tokens per frame. Spike S-3 measured no saving on
     * gemini-3.8-flash; it stays an option in case the service starts honouring it.
     */
    val lowMediaResolution: Boolean = false,
)

sealed interface VideoSummaryOutcome {
    data class Success(val text: String, val usage: Usage?) : VideoSummaryOutcome

    data class Failed(val message: String, val retryable: Boolean) : VideoSummaryOutcome
}

internal object GeminiVideoRequestBody {
    fun build(request: VideoSummaryRequest): JsonObject = buildJsonObject {
        putJsonArray("contents") {
            addJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    addJsonObject {
                        putJsonObject("fileData") { put("fileUri", request.videoUrl) }
                        if (request.startSeconds != null || request.endSeconds != null) {
                            putJsonObject("videoMetadata") {
                                request.startSeconds?.let { put("startOffset", "${it}s") }
                                request.endSeconds?.let { put("endOffset", "${it}s") }
                            }
                        }
                    }
                    addJsonObject { put("text", request.prompt) }
                }
            }
        }
        if (request.lowMediaResolution) {
            putJsonObject("generationConfig") { put("mediaResolution", "MEDIA_RESOLUTION_LOW") }
        }
    }
}

internal object GeminiVideoResponse {
    fun parse(bodyText: String): VideoSummaryOutcome {
        val body = Json.parseToJsonElement(bodyText).jsonObject
        val blockReason = body.objectOrNull("promptFeedback")?.stringOrNull("blockReason")
        if (blockReason != null) {
            return VideoSummaryOutcome.Failed("Gemini refused the video (block reason: $blockReason)", retryable = false)
        }
        val candidate = body.arrayOrNull("candidates")?.firstOrNull() as? JsonObject
        val parts = candidate?.objectOrNull("content")?.arrayOrNull("parts").orEmpty()
        val text = parts
            .map { it.jsonObject }
            .filter { !it.isTrue("thought") }
            .mapNotNull { it.stringOrNull("text") }
            .joinToString("")
        if (text.isBlank()) {
            val reason = candidate?.stringOrNull("finishReason") ?: "no candidate"
            return VideoSummaryOutcome.Failed("Gemini returned no summary text (finish reason: $reason)", retryable = false)
        }
        val usage = body.objectOrNull("usageMetadata")?.let { metadata ->
            Usage(
                inputTokens = metadata.intOrNull("promptTokenCount") ?: 0,
                outputTokens = (metadata.intOrNull("candidatesTokenCount") ?: 0) + (metadata.intOrNull("thoughtsTokenCount") ?: 0),
                cachedInputTokens = metadata.intOrNull("cachedContentTokenCount"),
            )
        }
        return VideoSummaryOutcome.Success(text, usage)
    }
}
