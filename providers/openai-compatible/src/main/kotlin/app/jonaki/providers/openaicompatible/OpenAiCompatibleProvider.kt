package app.jonaki.providers.openaicompatible

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

/** Streams chat completions from any service in the OpenAI format (D-010). */
class OpenAiCompatibleProvider(
    private val preset: ProviderPreset,
    private val apiKey: String?,
    private val httpClient: OkHttpClient,
    private val baseUrl: String = preset.baseUrl,
) : ChatProvider {
    override val id: String = "openai-compatible:${preset.key}"

    override fun stream(request: ChatRequest): Flow<StreamEvent> = flow {
        val call = httpClient.newCall(httpRequestFor(request))
        // Stop's cancellation must abort the blocking read, not wait for the next chunk.
        val cancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            val response = try {
                call.execute()
            } catch (networkError: IOException) {
                currentCoroutineContext().ensureActive()
                emit(StreamEvent.Failed("Could not reach ${preset.displayName}: ${networkError.message}", retryable = true))
                return@flow
            }
            response.use {
                if (!response.isSuccessful) {
                    emit(failureFromErrorResponse(response))
                    return@flow
                }
                val assembler = ChatCompletionStreamAssembler()
                val reader = ServerSentEventReader(response.body!!.charStream().buffered())
                try {
                    while (true) {
                        val payload = reader.nextData() ?: break
                        assembler.accept(payload).forEach { event -> emit(event) }
                    }
                    assembler.finish().forEach { event -> emit(event) }
                } catch (networkError: IOException) {
                    currentCoroutineContext().ensureActive()
                    emit(StreamEvent.Failed("The connection to ${preset.displayName} broke: ${networkError.message}", retryable = true))
                }
            }
        } finally {
            cancelHandle?.dispose()
        }
    }.flowOn(Dispatchers.IO)

    private fun httpRequestFor(request: ChatRequest): Request {
        val body = ChatCompletionRequestBody.build(request).toString()
        val builder = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/chat/completions")
            .post(body.toRequestBody(jsonMediaType))
            .header("Accept", "text/event-stream")
        if (!apiKey.isNullOrBlank()) builder.header("Authorization", "Bearer $apiKey")
        if (preset.key == ProviderPresets.openRouter.key) {
            // OpenRouter shows these on its activity page so the user can tell Jonaki's calls apart.
            builder.header("HTTP-Referer", "https://github.com/shourovrm/jonaki")
            builder.header("X-Title", "Jonaki")
        }
        return builder.build()
    }

    private fun failureFromErrorResponse(response: Response): StreamEvent.Failed {
        val bodyText = response.body?.string().orEmpty()
        val serviceMessage = errorMessageFrom(bodyText)
        val message = "${preset.displayName} answered HTTP ${response.code}: $serviceMessage"
        return StreamEvent.Failed(message, retryable = isRetryableHttpStatus(response.code))
    }

    private fun errorMessageFrom(bodyText: String): String {
        val parsed = runCatching { Json.parseToJsonElement(bodyText) as? JsonObject }.getOrNull()
        val error = parsed?.get("error")
        if (error is JsonObject) {
            error.stringOrNull("message")?.let { return it }
        }
        return bodyText.take(300).ifBlank { "no details" }
    }

    private companion object {
        val jsonMediaType = "application/json".toMediaType()
    }
}
