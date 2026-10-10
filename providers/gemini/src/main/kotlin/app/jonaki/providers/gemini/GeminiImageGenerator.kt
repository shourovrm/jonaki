package app.jonaki.providers.gemini

import app.jonaki.core.toolapi.ImageFailure
import app.jonaki.core.toolapi.ImageGenerator
import app.jonaki.core.toolapi.ImageOutcome
import app.jonaki.core.toolapi.ImageReference
import app.jonaki.core.toolapi.ImageRequest
import java.io.IOException
import java.io.InterruptedIOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Makes pictures with the Gemini API's generateContent (not streamed). The
 * key is read at call time, so a key removed in Settings stops the next call.
 *
 * Documentation checked on 2026-10-10: https://ai.google.dev/api/generate-content
 * (response fields, finish reasons, prompt feedback) and
 * https://ai.google.dev/gemini-api/docs/interactions ("generateContent ...
 * remains fully supported", though Google now calls it legacy and recommends
 * the Interactions API for new work). The pictures come back in
 * candidates[].content.parts[].inlineData {mimeType, data}. The answer has no
 * price, only usageMetadata token counts, so the cost is always null here.
 */
class GeminiImageGenerator(
    private val readApiKey: () -> String?,
    private val httpClient: OkHttpClient,
    private val baseUrl: String = GeminiProvider.DEFAULT_BASE_URL,
) : ImageGenerator {
    override suspend fun generate(request: ImageRequest): ImageOutcome {
        val apiKey = readApiKey()
        if (apiKey.isNullOrBlank()) {
            return ImageOutcome.Failed(ImageFailure.KEY_PROBLEM, "no Gemini key is saved")
        }
        val modelId = request.modelId.trim().removePrefix("models/")
        val httpRequest = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/models/" + modelId + ":generateContent")
            .header("x-goog-api-key", apiKey)
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
        private const val MAX_TEXT_CHARACTERS_IN_MESSAGE = 300

        /**
         * The reference pictures as `inlineData` parts, then the prompt; both
         * output kinds (an image model refuses an image-only list on some
         * models); and the aspect ratio and size only when set.
         *
         * Not confirmed against Google's pages (D-166): `imageConfig.aspectRatio`,
         * and `imageConfig.imageSize` ("1K", "2K", "4K"), which is the app's
         * reading of the documentation. The `inlineData` form of a picture in
         * `parts` is the documented way to send an image to generateContent.
         */
        fun requestBody(request: ImageRequest): JsonObject = buildJsonObject {
            putJsonArray("contents") {
                add(
                    buildJsonObject {
                        putJsonArray("parts") {
                            for (reference in request.references) {
                                add(inlineDataPart(reference))
                            }
                            add(buildJsonObject { put("text", request.prompt) })
                        }
                    },
                )
            }
            putJsonObject("generationConfig") {
                put("responseModalities", buildJsonArray { add(JsonPrimitive("TEXT")); add(JsonPrimitive("IMAGE")) })
                if (request.aspectRatio != null || request.resolution != null) {
                    putJsonObject("imageConfig") {
                        request.aspectRatio?.let { aspectRatio -> put("aspectRatio", aspectRatio) }
                        request.resolution?.let { size -> put("imageSize", size) }
                    }
                }
            }
        }

        private fun inlineDataPart(reference: ImageReference): JsonObject = buildJsonObject {
            putJsonObject("inlineData") {
                put("mimeType", reference.mediaType)
                put("data", Base64.getEncoder().encodeToString(reference.bytes))
            }
        }

        /**
         * The outcome for an HTTP status and body. Unknown fields are ignored;
         * anything that is not a picture becomes a failure with Google's own
         * words where there are some.
         */
        fun answerFrom(httpCode: Int, body: String): ImageOutcome {
            val root = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            val errorObject = root?.get("error") as? JsonObject
            if (httpCode !in 200..299 || errorObject != null) {
                val said = errorObject?.text("message") ?: body.trim().take(MAX_TEXT_CHARACTERS_IN_MESSAGE).ifEmpty { "no text" }
                val status = errorObject?.text("status").orEmpty()
                return ImageOutcome.Failed(failureKindFor(httpCode, status, said), "HTTP $httpCode: $said")
            }
            if (root == null) {
                return ImageOutcome.Failed(ImageFailure.NO_IMAGE, "the answer was not readable JSON")
            }
            blockedPromptIn(root)?.let { return it }
            val candidate = (root["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject
            val parts = ((candidate?.get("content") as? JsonObject)?.get("parts") as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
            val picture = lastPictureIn(parts)
            if (picture == null) {
                return noPicture(candidate, parts)
            }
            return successFrom(picture, root["usageMetadata"] as? JsonObject)
        }

        private class Picture(val bytes: ByteArray, val mediaType: String)

        /** A thinking model may return draft pictures marked `thought`; the last one that is not a draft is the answer. */
        private fun lastPictureIn(parts: List<JsonObject>): Picture? {
            for (part in parts.asReversed()) {
                if ((part["thought"] as? JsonPrimitive)?.booleanOrNull == true) continue
                val inline = (part["inlineData"] ?: part["inline_data"]) as? JsonObject ?: continue
                val encoded = inline.text("data")
                if (encoded.isNullOrBlank()) continue
                val bytes = try {
                    Base64.getMimeDecoder().decode(encoded)
                } catch (notBase64: IllegalArgumentException) {
                    continue
                }
                if (bytes.isEmpty()) continue
                return Picture(bytes, inline.text("mimeType") ?: inline.text("mime_type").orEmpty())
            }
            return null
        }

        private fun successFrom(picture: Picture, usage: JsonObject?): ImageOutcome.Success {
            val thinkingTokens = usage?.int("thoughtsTokenCount") ?: 0
            return ImageOutcome.Success(
                bytes = picture.bytes,
                mediaType = picture.mediaType,
                // The answer carries no price, and an invented figure would be wrong.
                costUsd = null,
                inputTokens = usage?.int("promptTokenCount") ?: 0,
                // Thinking tokens are billed as output.
                outputTokens = (usage?.int("candidatesTokenCount") ?: 0) + thinkingTokens,
            )
        }

        private fun blockedPromptIn(root: JsonObject): ImageOutcome.Failed? {
            val feedback = root["promptFeedback"] as? JsonObject ?: return null
            val reason = feedback.text("blockReason") ?: return null
            val message = feedback.text("blockReasonMessage")
            val said = if (message == null) "prompt blocked ($reason)" else "prompt blocked ($reason): $message"
            return ImageOutcome.Failed(ImageFailure.BLOCKED, said)
        }

        private val blockingFinishReasons = setOf(
            "SAFETY", "IMAGE_SAFETY", "PROHIBITED_CONTENT", "IMAGE_PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "RECITATION", "IMAGE_RECITATION",
        )

        private fun noPicture(candidate: JsonObject?, parts: List<JsonObject>): ImageOutcome.Failed {
            val finishReason = candidate?.text("finishReason")
            if (finishReason != null && finishReason in blockingFinishReasons) {
                return ImageOutcome.Failed(ImageFailure.BLOCKED, "the answer was stopped ($finishReason)")
            }
            val modelText = parts.mapNotNull { part -> part.text("text") }.joinToString(" ").trim()
            val said = when {
                modelText.isNotEmpty() -> "the model answered with text only: ${modelText.take(MAX_TEXT_CHARACTERS_IN_MESSAGE)}"
                finishReason != null -> "the answer had no image ($finishReason)"
                else -> "the answer had no image"
            }
            return ImageOutcome.Failed(ImageFailure.NO_IMAGE, said)
        }

        private val blockedWords = Regex("safety|policy|blocked|prohibited|not allowed", RegexOption.IGNORE_CASE)

        /**
         * Google reports a wrong key as 400 INVALID_ARGUMENT "API key not
         * valid", a missing permission as 403, and a used-up quota as 429
         * RESOURCE_EXHAUSTED.
         */
        private fun failureKindFor(httpCode: Int, status: String, said: String): ImageFailure = when {
            httpCode == 401 || httpCode == 403 || status == "UNAUTHENTICATED" || status == "PERMISSION_DENIED" -> ImageFailure.KEY_PROBLEM
            said.contains("API key not valid", ignoreCase = true) -> ImageFailure.KEY_PROBLEM
            httpCode == 429 || status == "RESOURCE_EXHAUSTED" -> ImageFailure.OUT_OF_CREDIT
            httpCode == 408 || httpCode == 504 || status == "DEADLINE_EXCEEDED" -> ImageFailure.TIMED_OUT
            blockedWords.containsMatchIn(said) -> ImageFailure.BLOCKED
            else -> ImageFailure.OTHER
        }

        private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

        private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
    }
}
