package app.jonaki.core.modelcatalog

import java.io.File
import java.io.IOException
import java.security.MessageDigest
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
        baseUrl: String = DEFAULT_BASE_URL,
    ): List<ProviderEndpoint> = parse(fetchText(httpClient, modelId, baseUrl))

    /** The answer's text as OpenRouter sent it, which is what [OpenRouterEndpointCache] keeps. */
    internal suspend fun fetchText(httpClient: OkHttpClient, modelId: String, baseUrl: String): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url("${baseUrl.trimEnd('/')}/models/$modelId/endpoints").build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("OpenRouter answered HTTP ${response.code}")
                }
                response.body?.string().orEmpty()
            }
        }

    const val DEFAULT_BASE_URL = "https://openrouter.ai/api/v1"

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

/**
 * A model's providers and their prices, kept in one file per model for a day
 * (as [ModelCatalog] keeps OpenRouter's model list), so that opening the
 * Providers sheet again shows the list at once and without a request.
 */
class OpenRouterEndpointCache(
    private val httpClient: OkHttpClient,
    private val folder: File,
    private val baseUrl: String = OpenRouterEndpoints.DEFAULT_BASE_URL,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /**
     * The saved list when it is less than a day old, else a fresh download
     * (then saved). When the download fails an older saved list still counts;
     * with nothing saved the [IOException] reaches the caller, which shows a retry.
     */
    suspend fun load(modelId: String): List<ProviderEndpoint> = withContext(Dispatchers.IO) {
        val file = fileFor(modelId)
        val saved = readSaved(file)
        val isFresh = file.isFile && clock() - file.lastModified() < FRESH_FOR_MILLIS
        if (isFresh && saved.isNotEmpty()) {
            return@withContext saved
        }
        val downloaded = try {
            OpenRouterEndpoints.fetchText(httpClient, modelId, baseUrl)
        } catch (failure: IOException) {
            if (saved.isNotEmpty()) {
                return@withContext saved
            }
            throw failure
        }
        val endpoints = OpenRouterEndpoints.parse(downloaded)
        // An empty or unreadable answer is not kept, so the next opening asks again.
        if (endpoints.isNotEmpty()) {
            save(file, downloaded)
        }
        endpoints
    }

    private fun readSaved(file: File): List<ProviderEndpoint> {
        if (!file.isFile) {
            return emptyList()
        }
        return runCatching { OpenRouterEndpoints.parse(file.readText()) }.getOrDefault(emptyList())
    }

    private fun save(file: File, text: String) {
        runCatching {
            folder.mkdirs()
            file.writeText(text)
            // The file's time is the cache's clock, so that tests can move it.
            file.setLastModified(clock())
        }
    }

    /** A model id holds "/" and may hold anything a user typed, so the file is named by its hash. */
    private fun fileFor(modelId: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(modelId.toByteArray())
        val name = digest.take(HASH_BYTES_IN_NAME).joinToString("") { byte -> "%02x".format(byte) }
        return File(folder, "$name.json")
    }

    companion object {
        const val FRESH_FOR_MILLIS = 24L * 60 * 60 * 1000
        private const val HASH_BYTES_IN_NAME = 16
    }
}
