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
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Streams chat completions from any service in the OpenAI format (D-010). */
class OpenAiCompatibleProvider(
    private val preset: ProviderPreset,
    private val apiKey: String?,
    private val httpClient: OkHttpClient,
    private val baseUrl: String = preset.baseUrl,
    /** Applies to OpenRouter only (D-030); other services ignore it. */
    private val openRouterRouting: OpenRouterRouting = OpenRouterRouting.AUTOMATIC,
    /** Called when a private-only request found no endpoint and was sent again as cheapest. */
    private val onRoutingFallback: () -> Unit = {},
) : ChatProvider {
    override val id: String = "openai-compatible:${preset.key}"

    private val isOpenRouter: Boolean
        get() = preset.key == ProviderPresets.openRouter.key

    override fun stream(request: ChatRequest): Flow<StreamEvent> = flow {
        val routing = if (isOpenRouter) openRouterRouting else OpenRouterRouting.AUTOMATIC
        val mayFallBack = routing == OpenRouterRouting.PRIVATE_THEN_CHEAPEST
        val rejectedByDataPolicy = streamWith(request, routing, mayFallBack)
        if (rejectedByDataPolicy) {
            // No endpoint for this model promises not to keep prompts, so the user's
            // choice (D-030) is to run on the cheapest endpoint and be told.
            onRoutingFallback()
            streamWith(request, OpenRouterRouting.CHEAPEST, mayFallBack = false)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Streams one request. Returns true, having emitted nothing, when the request
     * was rejected by OpenRouter's data policy filter and [mayFallBack] is set.
     */
    private suspend fun FlowCollector<StreamEvent>.streamWith(
        request: ChatRequest,
        routing: OpenRouterRouting,
        mayFallBack: Boolean,
    ): Boolean {
        val call = httpClient.newCall(httpRequestFor(request, routing))
        // Stop's cancellation must abort the blocking read, not wait for the next chunk.
        val cancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            val response = try {
                call.execute()
            } catch (networkError: IOException) {
                currentCoroutineContext().ensureActive()
                emit(StreamEvent.Failed("Could not reach ${preset.displayName}: ${networkError.message}", retryable = true))
                return false
            }
            response.use {
                if (!response.isSuccessful) {
                    val bodyText = response.body?.string().orEmpty()
                    if (mayFallBack && OpenRouterRouting.isDataPolicyRejection(response.code, bodyText)) {
                        return true
                    }
                    emit(failureFromErrorResponse(response.code, bodyText))
                    return false
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
        return false
    }

    private fun httpRequestFor(request: ChatRequest, routing: OpenRouterRouting): Request {
        val body = ChatCompletionRequestBody.build(request, askForCost = preset.reportsCost, routing = routing).toString()
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

    private fun failureFromErrorResponse(statusCode: Int, bodyText: String): StreamEvent.Failed {
        val serviceMessage = errorMessageFrom(bodyText)
        val message = "${preset.displayName} answered HTTP $statusCode: $serviceMessage"
        return StreamEvent.Failed(message, retryable = isRetryableHttpStatus(statusCode))
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
