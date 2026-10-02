package app.jonaki.core.modelcatalog

import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Model lists for the model picker and prices for the cost display (D-027).
 * OpenRouter's list is downloaded and cached in a file for a day. Other
 * services use [BuiltInModels], joined by the ids their own GET /models
 * answered when the service has one (D-MCP-5).
 */
class ModelCatalog(
    private val cacheFile: File,
    private val httpClient: OkHttpClient,
    private val openRouterModelsUrl: String = "https://openrouter.ai/api/v1/models",
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    private var openRouterModels: List<ModelInfo> = readCache()

    /** Ids from each service's own list, read from its cache file on first use. */
    private val listedIds = ConcurrentHashMap<String, List<String>>()

    /** Downloads OpenRouter's list when the cache is missing or older than a day; failures keep the old list. */
    suspend fun refreshIfStale() {
        val age = clock() - cacheFile.lastModified()
        if (cacheFile.exists() && age in 0 until REFRESH_AFTER_MILLIS) {
            return
        }
        val body = download() ?: return
        val parsed = OpenRouterModels.parse(body)
        if (parsed.isEmpty()) {
            return
        }
        withContext(Dispatchers.IO) {
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeText(body)
            cacheFile.setLastModified(clock())
        }
        openRouterModels = parsed
    }

    /**
     * Downloads [serviceKey]'s model list from `<baseUrl>/models`, sending
     * [apiKey] as a bearer token when there is one. Listing is free on every
     * service that offers it. A failure keeps the last list.
     */
    suspend fun refreshServiceModels(serviceKey: String, baseUrl: String, apiKey: String?) {
        val requestBuilder = Request.Builder().url(baseUrl.trimEnd('/') + "/models")
        if (apiKey != null) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }
        val body = download(requestBuilder.build()) ?: return
        val ids = ServiceModelList.parseIds(body)
        if (ids.isEmpty()) {
            return
        }
        withContext(Dispatchers.IO) {
            val file = serviceListFile(serviceKey)
            file.parentFile?.mkdirs()
            file.writeText(body)
        }
        listedIds[serviceKey] = ids
    }

    fun models(serviceKey: String): List<ModelInfo> {
        if (serviceKey == OpenRouterModels.SERVICE_KEY) {
            return openRouterModels
        }
        val builtIn = BuiltInModels.all.filter { model -> model.serviceKey == serviceKey }
        val builtInIds = builtIn.map { model -> model.modelId }.toSet()
        // A listed model the app has no row for is offered without a price, like an id typed by hand.
        val listedOnly = listedIdsOf(serviceKey)
            .filter { id -> id !in builtInIds }
            .map { id -> ModelInfo(serviceKey, id, id, contextWindowTokens = null, inputUsdPerMillion = null, outputUsdPerMillion = null) }
        return builtIn + listedOnly
    }

    fun find(serviceKey: String, modelId: String): ModelInfo? =
        models(serviceKey).firstOrNull { model -> model.modelId == modelId }

    fun find(modelKey: String): ModelInfo? = find(ModelKey.serviceOf(modelKey), ModelKey.modelOf(modelKey))

    /** Models whose id or name contains [query], ignoring case; all of them for a blank query. */
    fun search(serviceKey: String, query: String): List<ModelInfo> {
        val needle = query.trim()
        return models(serviceKey).filter { model ->
            needle.isEmpty() ||
                model.modelId.contains(needle, ignoreCase = true) ||
                model.displayName.contains(needle, ignoreCase = true)
        }
    }

    private suspend fun download(
        request: Request = Request.Builder().url(openRouterModelsUrl).build(),
    ): String? = withContext(Dispatchers.IO) {
        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (networkError: IOException) {
            null
        }
    }

    private fun listedIdsOf(serviceKey: String): List<String> =
        listedIds.getOrPut(serviceKey) {
            val file = serviceListFile(serviceKey)
            if (file.exists()) ServiceModelList.parseIds(runCatching { file.readText() }.getOrDefault("")) else emptyList()
        }

    private fun serviceListFile(serviceKey: String): File = File(cacheFile.parentFile, "models-$serviceKey.json")

    private fun readCache(): List<ModelInfo> {
        if (!cacheFile.exists()) {
            return emptyList()
        }
        return runCatching { OpenRouterModels.parse(cacheFile.readText()) }.getOrDefault(emptyList())
    }

    private companion object {
        const val REFRESH_AFTER_MILLIS = 24 * 60 * 60 * 1000L
    }
}
