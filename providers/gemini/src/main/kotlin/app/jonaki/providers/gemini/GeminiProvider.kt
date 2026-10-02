package app.jonaki.providers.gemini

import app.jonaki.core.providerapi.ChatProvider
import app.jonaki.core.providerapi.ChatRequest
import app.jonaki.core.providerapi.ServerSentEventReader
import app.jonaki.core.providerapi.StreamEvent
import app.jonaki.core.providerapi.isRetryableHttpStatus
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** Streams replies from the native Gemini API (D-010) and summarises YouTube videos (D-012). */
class GeminiProvider(
    private val apiKey: String,
    private val httpClient: OkHttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : ChatProvider {
    override val id: String = "gemini"

    override fun stream(request: ChatRequest): Flow<StreamEvent> = flow {
        val body = GeminiRequestBody.build(request).toString()
        val httpRequest = postRequest("models/${request.model}:streamGenerateContent?alt=sse", body)
        val call = httpClient.newCall(httpRequest)
        // Stop's cancellation must abort the blocking read, not wait for the next chunk.
        val cancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            val response = try {
                call.execute()
            } catch (networkError: IOException) {
                currentCoroutineContext().ensureActive()
                emit(StreamEvent.Failed("Could not reach Gemini: ${networkError.message}", retryable = true))
                return@flow
            }
            response.use {
                if (!response.isSuccessful) {
                    emit(failureFromErrorResponse(response))
                    return@flow
                }
                val assembler = GeminiStreamAssembler()
                val reader = ServerSentEventReader(response.body!!.charStream().buffered())
                try {
                    while (true) {
                        val payload = reader.nextData() ?: break
                        assembler.accept(payload).forEach { event -> emit(event) }
                    }
                    assembler.finish().forEach { event -> emit(event) }
                } catch (networkError: IOException) {
                    currentCoroutineContext().ensureActive()
                    emit(StreamEvent.Failed("The connection to Gemini broke: ${networkError.message}", retryable = true))
                }
            }
        } finally {
            cancelHandle?.dispose()
        }
    }.flowOn(Dispatchers.IO)

    /** Sends a public YouTube URL to Gemini, which watches the video itself (spike S-3). */
    suspend fun summarizeVideo(request: VideoSummaryRequest): VideoSummaryOutcome {
        val body = GeminiVideoRequestBody.build(request).toString()
        val httpRequest = postRequest("models/${request.model}:generateContent", body)
        val response = try {
            httpClient.newCall(httpRequest).executeCancellable()
        } catch (networkError: IOException) {
            return VideoSummaryOutcome.Failed("Could not reach Gemini: ${networkError.message}", retryable = true)
        }
        return response.use {
            val bodyText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val failure = failureFromBody(response.code, bodyText)
                VideoSummaryOutcome.Failed(failure.message, failure.retryable)
            } else {
                GeminiVideoResponse.parse(bodyText)
            }
        }
    }

    private fun postRequest(path: String, body: String): Request = Request.Builder()
        .url(baseUrl.trimEnd('/') + "/" + path)
        .header("x-goog-api-key", apiKey)
        .post(body.toRequestBody(jsonMediaType))
        .build()

    private fun failureFromErrorResponse(response: Response): StreamEvent.Failed =
        failureFromBody(response.code, response.body?.string().orEmpty())

    private fun failureFromBody(statusCode: Int, bodyText: String): StreamEvent.Failed {
        val parsed = runCatching { Json.parseToJsonElement(bodyText) as? JsonObject }.getOrNull()
        val error = parsed?.objectOrNull("error")
        if (error != null) return failureFromError(error)
        return StreamEvent.Failed(
            "Gemini answered HTTP $statusCode: ${bodyText.take(300).ifBlank { "no details" }}",
            retryable = isRetryableHttpStatus(statusCode),
        )
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"

        /** gemini-2.5-flash answers 404 for keys created after its retirement (spike S-3). */
        const val DEFAULT_MODEL = "gemini-3.8-flash"

        private val jsonMediaType = "application/json".toMediaType()
    }
}
