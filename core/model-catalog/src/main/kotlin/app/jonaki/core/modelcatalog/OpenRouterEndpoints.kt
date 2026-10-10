package app.jonaki.core.modelcatalog

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** One provider that serves an OpenRouter model, as listed by GET /api/v1/models/{author}/{slug}/endpoints. */
data class ProviderEndpoint(
    /** Display name, for example "DeepInfra". */
    val providerName: String,
    /** The slug to send in the request's provider.order, for example "deepinfra/fp4". */
    val tag: String,
    /** For example "fp4"; null when OpenRouter does not know it. */
    val quantization: String?,
    val inputUsdPerMillion: Double?,
    val outputUsdPerMillion: Double?,
    val contextLength: Int?,
) {
    /** Input plus output price per million tokens; null when either is unknown. */
    val combinedUsdPerMillion: Double?
        get() {
            val input = inputUsdPerMillion ?: return null
            val output = outputUsdPerMillion ?: return null
            return input + output
        }

    /**
     * The tag, when it names a variant such as "parasail/fast" or "fireworks/us".
     * A tag whose suffix only repeats the quantization ("deepinfra/fp4") carries no variant.
     */
    val variantTag: String?
        get() {
            val suffix = tag.substringAfter('/', missingDelimiterValue = "")
            if (suffix.isEmpty() || suffix == quantization) {
                return null
            }
            return tag
        }
}

/** Reads the providers of one OpenRouter model (public, no key needed). */
object OpenRouterEndpoints {
    private const val TOKENS_PER_MILLION = 1_000_000.0
    private const val UNKNOWN_QUANTIZATION = "unknown"

    /**
     * The model's providers, cheapest first (input plus output price), each tag once.
     * Providers without a known price come last. Returns an empty list for text that is not the expected JSON.
     */
    fun parse(json: String): List<ProviderEndpoint> {
        val root = runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return emptyList()
        val data = root["data"] as? JsonObject ?: return emptyList()
        val endpoints = data["endpoints"] as? JsonArray ?: return emptyList()
        return endpoints
            .mapNotNull { element -> endpointFrom(element as? JsonObject) }
            .sortedBy { endpoint -> endpoint.combinedUsdPerMillion ?: Double.MAX_VALUE }
            // Sorted first, so that when a tag is listed twice the cheaper entry is the one kept.
            .distinctBy { endpoint -> endpoint.tag }
    }

    /**
     * Downloads and parses the providers of [modelId] ("author/slug"). Throws [IOException]
     * on a network failure or a non-success answer, so that the caller can show a retry.
     */
    suspend fun fetch(
        httpClient: OkHttpClient,
        modelId: String,
        baseUrl: String = "https://openrouter.ai/api/v1",
    ): List<ProviderEndpoint> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("${baseUrl.trimEnd('/')}/models/$modelId/endpoints").build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("OpenRouter answered HTTP ${response.code}")
            }
            parse(response.body?.string().orEmpty())
        }
    }

    private fun endpointFrom(endpoint: JsonObject?): ProviderEndpoint? {
        if (endpoint == null) return null
        val tag = endpoint.text("tag") ?: return null
        val pricing = endpoint["pricing"] as? JsonObject
        return ProviderEndpoint(
            providerName = endpoint.text("provider_name") ?: tag,
            tag = tag,
            quantization = endpoint.text("quantization")?.takeIf { it != UNKNOWN_QUANTIZATION },
            inputUsdPerMillion = pricing?.perMillion("prompt"),
            outputUsdPerMillion = pricing?.perMillion("completion"),
            contextLength = (endpoint["context_length"] as? JsonPrimitive)?.intOrNull,
        )
    }

    private fun JsonObject.perMillion(key: String): Double? {
        val perToken = text(key)?.toDoubleOrNull() ?: return null
        if (perToken < 0) return null
        return perToken * TOKENS_PER_MILLION
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}
