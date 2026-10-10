package app.jonaki.providers.openaicompatible

import app.jonaki.core.toolapi.VideoCheckOutcome
import app.jonaki.core.toolapi.VideoDownloadOutcome
import app.jonaki.core.toolapi.VideoFailure
import app.jonaki.core.toolapi.VideoGenerator
import app.jonaki.core.toolapi.VideoRequest
import app.jonaki.core.toolapi.VideoStartOutcome
import app.jonaki.core.toolapi.await
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Makes videos through OpenRouter's /videos: POST starts a job (HTTP 202),
 * GET /videos/{id} reports it, and the finished file is at a link in
 * `unsigned_urls` that needs the same key (it is not presigned). The key is
 * read at call time, so a key removed in Settings stops the next call.
 *
 * The answers were read from OpenRouter's documentation and no paid request
 * has been recorded, so every field is read defensively and anything
 * unexpected becomes a failure that carries the service's own text.
 */
class OpenRouterVideoGenerator(
    private val readApiKey: () -> String?,
    private val httpClient: OkHttpClient,
    private val baseUrl: String = ProviderPresets.openRouter.baseUrl,
) : VideoGenerator {
    override suspend fun start(request: VideoRequest): VideoStartOutcome {
        val apiKey = readApiKey()
        if (apiKey.isNullOrBlank()) {
            return VideoStartOutcome.Failed(VideoFailure.KEY_PROBLEM, "no OpenRouter key is saved")
        }
        val httpRequest = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/videos")
            .header("Authorization", "Bearer $apiKey")
            .post(requestBody(request).toString().toRequestBody(JSON_TYPE))
            .build()
        return try {
            val answer = quickClient().newCall(httpRequest).await().use { response ->
                HttpAnswer(response.code, response.body?.string().orEmpty())
            }
            startOutcomeFrom(answer.code, answer.body)
        } catch (timeout: InterruptedIOException) {
            VideoStartOutcome.Failed(VideoFailure.TIMED_OUT, "no answer within $QUICK_CALL_SECONDS seconds")
        } catch (networkError: IOException) {
            VideoStartOutcome.Failed(VideoFailure.OTHER, "no connection: ${networkError.message}")
        }
    }

    override suspend fun check(serviceKey: String, jobId: String): VideoCheckOutcome {
        val apiKey = readApiKey()
        if (apiKey.isNullOrBlank()) {
            return VideoCheckOutcome.Failed(VideoFailure.KEY_PROBLEM, "no OpenRouter key is saved")
        }
        val httpRequest = Request.Builder()
            // The id is one path segment, whatever characters the service used in it.
            .url(baseUrl.toHttpUrl().newBuilder().addPathSegment("videos").addPathSegment(jobId).build())
            .header("Authorization", "Bearer $apiKey")
            .build()
        return try {
            val answer = quickClient().newCall(httpRequest).await().use { response ->
                HttpAnswer(response.code, response.body?.string().orEmpty())
            }
            checkOutcomeFrom(answer.code, answer.body)
        } catch (timeout: InterruptedIOException) {
            VideoCheckOutcome.Failed(VideoFailure.TIMED_OUT, "no answer within $QUICK_CALL_SECONDS seconds")
        } catch (networkError: IOException) {
            VideoCheckOutcome.Failed(VideoFailure.OTHER, "no connection: ${networkError.message}")
        }
    }

    override suspend fun download(serviceKey: String, contentUrl: String, target: File): VideoDownloadOutcome {
        val apiKey = readApiKey()
        if (apiKey.isNullOrBlank()) {
            return VideoDownloadOutcome.Failed(VideoFailure.KEY_PROBLEM, "no OpenRouter key is saved")
        }
        val url = resolveContentUrl(baseUrl, contentUrl)
            ?: return VideoDownloadOutcome.Failed(VideoFailure.OTHER, "the file link was not readable: ${contentUrl.take(MAX_BODY_CHARACTERS_IN_ERROR)}")
        // The file link on the service's own host is not presigned and needs the key; a link to any other host never gets it.
        val requestBuilder = Request.Builder().url(url)
        if (url.host == baseUrl.toHttpUrl().host) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }
        val httpRequest = requestBuilder.build()
        val client = httpClient.newBuilder().callTimeout(DOWNLOAD_CALL_SECONDS, TimeUnit.SECONDS).build()
        return try {
            client.newCall(httpRequest).await().use { response ->
                if (!response.isSuccessful) {
                    val answer = response.body?.string().orEmpty()
                    val failure = failureFor(response.code, answer)
                    return@use VideoDownloadOutcome.Failed(failure.first, failure.second)
                }
                val body = response.body ?: return@use VideoDownloadOutcome.Failed(VideoFailure.OTHER, "the download had no content")
                val mediaType = response.header("Content-Type").orEmpty().substringBefore(';').trim()
                val size = withContext(Dispatchers.IO) { streamToFile(body.byteStream(), target) }
                VideoDownloadOutcome.Saved(mediaType, size)
            }
        } catch (timeout: InterruptedIOException) {
            target.delete()
            VideoDownloadOutcome.Failed(VideoFailure.TIMED_OUT, "the download did not finish within $DOWNLOAD_CALL_SECONDS seconds")
        } catch (networkError: IOException) {
            target.delete()
            VideoDownloadOutcome.Failed(VideoFailure.OTHER, "the download broke off: ${networkError.message}")
        } catch (stopped: kotlinx.coroutines.CancellationException) {
            target.delete()
            throw stopped
        }
    }

    /** Copies in blocks and checks for a Stop between them, so that a cancelled download ends at once. */
    private suspend fun streamToFile(input: java.io.InputStream, target: File): Long {
        var copied = 0L
        target.parentFile?.mkdirs()
        input.use { source ->
            target.outputStream().use { output ->
                val buffer = ByteArray(COPY_BLOCK_BYTES)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = source.read(buffer)
                    if (count < 0) {
                        break
                    }
                    output.write(buffer, 0, count)
                    copied += count
                }
            }
        }
        return copied
    }

    /** Shorter than the tool's own limit, so a slow answer is a clear time-out and not a killed tool. */
    private fun quickClient(): OkHttpClient = httpClient.newBuilder().callTimeout(QUICK_CALL_SECONDS, TimeUnit.SECONDS).build()

    private class HttpAnswer(val code: Int, val body: String)

    companion object {
        private val JSON_TYPE = "application/json".toMediaType()
        private const val QUICK_CALL_SECONDS = 60L
        private const val DOWNLOAD_CALL_SECONDS = 300L
        private const val COPY_BLOCK_BYTES = 64 * 1024
        private const val MAX_BODY_CHARACTERS_IN_ERROR = 300

        /** Fields at the top level: model and prompt always, the other options only when the agent chose them. */
        fun requestBody(request: VideoRequest): JsonObject = buildJsonObject {
            put("model", request.modelId)
            put("prompt", request.prompt)
            request.durationSeconds?.let { seconds -> put("duration", seconds) }
            request.resolution?.let { resolution -> put("resolution", resolution) }
            request.aspectRatio?.let { aspectRatio -> put("aspect_ratio", aspectRatio) }
            request.withAudio?.let { withAudio -> put("generate_audio", withAudio) }
        }

        /** The job id of an accepted start; anything else becomes a failure with the service's own text. */
        fun startOutcomeFrom(httpCode: Int, body: String): VideoStartOutcome {
            val root = jsonObjectOrNull(body)
            if (httpCode !in 200..299) {
                val failure = failureFor(httpCode, body)
                return VideoStartOutcome.Failed(failure.first, failure.second)
            }
            if (root == null) {
                return VideoStartOutcome.Failed(VideoFailure.OTHER, "the answer was not readable JSON: ${body.trim().take(MAX_BODY_CHARACTERS_IN_ERROR)}")
            }
            val jobId = root.text("id")
            if (jobId.isNullOrBlank()) {
                // A 200 can still carry an error object, for example from a company behind OpenRouter.
                val said = errorMessageIn(root) ?: "the answer had no job id"
                return VideoStartOutcome.Failed(failureKindFor(if (errorMessageIn(root) == null) 200 else 400, said), said)
            }
            return VideoStartOutcome.Started(jobId)
        }

        /** What a poll answered: working, done with its file links, ended without a video, or an error. */
        fun checkOutcomeFrom(httpCode: Int, body: String): VideoCheckOutcome {
            val root = jsonObjectOrNull(body)
            if (httpCode !in 200..299) {
                val failure = failureFor(httpCode, body)
                return VideoCheckOutcome.Failed(failure.first, failure.second)
            }
            if (root == null) {
                return VideoCheckOutcome.Failed(VideoFailure.OTHER, "the answer was not readable JSON: ${body.trim().take(MAX_BODY_CHARACTERS_IN_ERROR)}")
            }
            val status = root.text("status")?.lowercase().orEmpty()
            return when (status) {
                "completed" -> completedFrom(root)
                "failed", "cancelled", "canceled", "expired" ->
                    VideoCheckOutcome.JobEnded(status, errorMessageIn(root) ?: "the service gave no reason")
                "pending", "in_progress" -> VideoCheckOutcome.Working(status)
                else -> {
                    val error = errorMessageIn(root)
                    if (error != null) {
                        VideoCheckOutcome.Failed(failureKindFor(400, error), error)
                    } else {
                        // An unknown word is treated as still working; the tool's own time limit ends the wait.
                        VideoCheckOutcome.Working(status.ifEmpty { "unknown" })
                    }
                }
            }
        }

        private fun completedFrom(root: JsonObject): VideoCheckOutcome {
            val urls = (root["unsigned_urls"] as? JsonArray).orEmpty().mapNotNull { element -> (element as? JsonPrimitive)?.contentOrNull }
            if (urls.isEmpty()) {
                return VideoCheckOutcome.JobEnded("completed", "the service says the job is done but lists no video file")
            }
            val usage = root["usage"] as? JsonObject
            return VideoCheckOutcome.Completed(urls, (usage?.get("cost") as? JsonPrimitive)?.doubleOrNull)
        }

        /** A full link stays as it is; a path such as `/api/v1/videos/{id}/content?index=0` is joined to the service's host. */
        fun resolveContentUrl(baseUrl: String, contentUrl: String): okhttp3.HttpUrl? {
            val base = runCatching { baseUrl.toHttpUrl() }.getOrNull() ?: return null
            return base.resolve(contentUrl.trim())
        }

        private fun jsonObjectOrNull(body: String): JsonObject? =
            runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()

        private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

        /** `{"error": {"message": "…", "code": 402}}`, or `{"error": "…"}`. */
        private fun errorMessageIn(root: JsonObject): String? {
            val error = root["error"] ?: return null
            if (error is JsonObject) {
                return error.text("message")
            }
            return (error as? JsonPrimitive)?.contentOrNull
        }

        /** The company OpenRouter passed the request to, from `error.metadata.provider_name`. */
        private fun providerNameIn(root: JsonObject): String? {
            val metadata = (root["error"] as? JsonObject)?.get("metadata") as? JsonObject
            return metadata?.text("provider_name")?.ifBlank { null }
        }

        /** The kind and the text of a non-success answer; the text names the company behind OpenRouter when it says so. */
        private fun failureFor(httpCode: Int, body: String): Pair<VideoFailure, String> {
            val root = jsonObjectOrNull(body)
            val said = root?.let(::errorMessageIn) ?: body.trim().take(MAX_BODY_CHARACTERS_IN_ERROR).ifEmpty { "no text" }
            val from = root?.let(::providerNameIn)?.let { providerName -> " from $providerName" }.orEmpty()
            return failureKindFor(httpCode, said) to "HTTP $httpCode$from: $said"
        }

        private val blockedWords = Regex("moderat|safety|policy|flagged|blocked|prohibited|not allowed", RegexOption.IGNORE_CASE)

        private fun failureKindFor(httpCode: Int, said: String): VideoFailure = when {
            httpCode == 401 -> VideoFailure.KEY_PROBLEM
            httpCode == 402 -> VideoFailure.OUT_OF_CREDIT
            // OpenRouter answers 402 when the user's own credit is used up, so 429 is a limit further up:
            // its own, or the quota of the company that serves the model.
            httpCode == 429 -> VideoFailure.SERVICE_LIMIT
            httpCode == 408 || httpCode == 504 -> VideoFailure.TIMED_OUT
            blockedWords.containsMatchIn(said) -> VideoFailure.BLOCKED
            httpCode == 403 -> VideoFailure.KEY_PROBLEM
            else -> VideoFailure.OTHER
        }
    }
}
