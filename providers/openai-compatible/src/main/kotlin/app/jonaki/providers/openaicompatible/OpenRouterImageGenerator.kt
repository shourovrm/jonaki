package app.jonaki.providers.openaicompatible

import app.jonaki.core.toolapi.ImageFailure
import app.jonaki.core.toolapi.ImageGenerator
import app.jonaki.core.toolapi.ImageOutcome
import app.jonaki.core.toolapi.ImageRequest
import java.io.IOException
import java.io.InterruptedIOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Makes pictures through OpenRouter's POST /images (not streamed). The key
 * is read at call time, so a key removed in Settings stops the next call.
 */
class OpenRouterImageGenerator(
    private val readApiKey: () -> String?,
    private val httpClient: OkHttpClient,
    private val baseUrl: String = ProviderPresets.openRouter.baseUrl,
) : ImageGenerator {
    override suspend fun generate(request: ImageRequest): ImageOutcome {
        val apiKey = readApiKey()
        if (apiKey.isNullOrBlank()) {
            return ImageOutcome.Failed(ImageFailure.KEY_PROBLEM, "no OpenRouter key is saved")
        }
        val httpRequest = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/images")
            .header("Authorization", "Bearer $apiKey")
            .post(requestBody(request).toString().toRequestBody(JSON_TYPE))
            .build()
        // Shorter than the tool's 120 s limit, so the answer is a clear time-out and not a cancelled tool.
        val client = httpClient.newBuilder().callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(httpRequest).execute().use { response ->
                    answerFrom(response.code, response.body?.string().orEmpty())
                }
            } catch (timeout: InterruptedIOException) {
                ImageOutcome.Failed(ImageFailure.TIMED_OUT, "no answer within $CALL_TIMEOUT_SECONDS seconds")
            } catch (networkError: IOException) {
                ImageOutcome.Failed(ImageFailure.OTHER, "no connection: ${networkError.message}")
            }
        }
    }

    companion object {
        private val JSON_TYPE = "application/json".toMediaType()
        private const val CALL_TIMEOUT_SECONDS = 110L
        private const val MAX_BODY_CHARACTERS_IN_ERROR = 300

        /** Fields at the top level: model and prompt always, the aspect ratio only when the agent chose one. */
        fun requestBody(request: ImageRequest): JsonObject = buildJsonObject {
            put("model", request.modelId)
            put("prompt", request.prompt)
            request.aspectRatio?.let { aspectRatio -> put("aspect_ratio", aspectRatio) }
        }

        /**
         * The outcome for an HTTP status and body. Unknown fields are ignored;
         * anything that is not a picture becomes a failure with the service's
         * own error text.
         */
        fun answerFrom(httpCode: Int, body: String): ImageOutcome {
            val root = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            val errorText = root?.let(::errorMessageIn)
            if (httpCode !in 200..299) {
                val said = errorText ?: body.trim().take(MAX_BODY_CHARACTERS_IN_ERROR).ifEmpty { "no text" }
                val from = root?.let(::providerNameIn)?.let { providerName -> " from $providerName" }.orEmpty()
                return ImageOutcome.Failed(failureKindFor(httpCode, said), "HTTP $httpCode$from: $said")
            }
            if (root == null) {
                return ImageOutcome.Failed(ImageFailure.NO_IMAGE, "the answer was not readable JSON")
            }
            val first = (root["data"] as? JsonArray)?.firstOrNull() as? JsonObject
            val encoded = (first?.get("b64_json") as? JsonPrimitive)?.contentOrNull
            if (encoded.isNullOrBlank()) {
                // A 200 can still carry an error object, for example from a provider behind OpenRouter.
                val said = errorText ?: "the answer had no image data"
                return ImageOutcome.Failed(if (errorText == null) ImageFailure.NO_IMAGE else failureKindFor(200, said), said)
            }
            val bytes = decodeImageData(encoded)
                ?: return ImageOutcome.Failed(ImageFailure.NO_IMAGE, "the image data was not valid base64")
            val usage = root["usage"] as? JsonObject
            return ImageOutcome.Success(
                bytes = bytes,
                mediaType = (first?.get("media_type") as? JsonPrimitive)?.contentOrNull.orEmpty(),
                costUsd = (usage?.get("cost") as? JsonPrimitive)?.doubleOrNull,
                inputTokens = (usage?.get("prompt_tokens") as? JsonPrimitive)?.intOrNull ?: 0,
                outputTokens = (usage?.get("completion_tokens") as? JsonPrimitive)?.intOrNull ?: 0,
            )
        }

        /**
         * The picture bytes from `b64_json`. Raster models send base64. A
         * vector model's answer has not been recorded yet, so three forms are
         * accepted: base64, a `data:...;base64,` URI, and the SVG itself as
         * plain text (it starts with "<", which base64 never does). Null when
         * the text is none of these.
         */
        private fun decodeImageData(encoded: String): ByteArray? {
            val text = encoded.trim()
            if (text.startsWith("<")) {
                return text.toByteArray(Charsets.UTF_8)
            }
            val base64Text = if (text.startsWith("data:", ignoreCase = true) && text.contains(";base64,")) {
                text.substringAfter(";base64,")
            } else {
                text
            }
            return try {
                Base64.getDecoder().decode(base64Text.filterNot { it.isWhitespace() })
            } catch (notBase64: IllegalArgumentException) {
                null
            }
        }

        /** `{"error": {"message": "…", "code": 402}}`, or `{"error": "…"}`. */
        private fun errorMessageIn(root: JsonObject): String? {
            val error = root["error"] ?: return null
            if (error is JsonObject) {
                return (error["message"] as? JsonPrimitive)?.contentOrNull
            }
            return (error as? JsonPrimitive)?.contentOrNull
        }

        /** The company OpenRouter passed the request to, from `error.metadata.provider_name`. */
        private fun providerNameIn(root: JsonObject): String? {
            val metadata = (root["error"] as? JsonObject)?.get("metadata") as? JsonObject
            return (metadata?.get("provider_name") as? JsonPrimitive)?.contentOrNull?.ifBlank { null }
        }

        private val blockedWords = Regex("moderat|safety|policy|flagged|blocked|prohibited|not allowed", RegexOption.IGNORE_CASE)

        private fun failureKindFor(httpCode: Int, said: String): ImageFailure = when {
            httpCode == 401 -> ImageFailure.KEY_PROBLEM
            httpCode == 402 -> ImageFailure.OUT_OF_CREDIT
            // OpenRouter answers 402 when the user's own credit is used up, so 429 is a limit further up:
            // its own, or the quota of the company that serves the model (seen with Google AI Studio).
            httpCode == 429 -> ImageFailure.SERVICE_LIMIT
            httpCode == 408 || httpCode == 504 -> ImageFailure.TIMED_OUT
            blockedWords.containsMatchIn(said) -> ImageFailure.BLOCKED
            httpCode == 403 -> ImageFailure.KEY_PROBLEM
            else -> ImageFailure.OTHER
        }
    }
}
