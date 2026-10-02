package app.jonaki.core.modelcatalog

import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Model lists for the model picker and prices for the cost display (D-027).
 * OpenRouter's list is downloaded and cached in a file for a day; other
 * services use [BuiltInModels].
 */
class ModelCatalog(
    private val cacheFile: File,
    private val httpClient: OkHttpClient,
    private val openRouterModelsUrl: String = "https://openrouter.ai/api/v1/models",
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    private var openRouterModels: List<ModelInfo> = readCache()

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

    fun models(serviceKey: String): List<ModelInfo> =
        if (serviceKey == OpenRouterModels.SERVICE_KEY) {
            openRouterModels
        } else {
            BuiltInModels.all.filter { model -> model.serviceKey == serviceKey }
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

    private suspend fun download(): String? = withContext(Dispatchers.IO) {
        try {
            httpClient.newCall(Request.Builder().url(openRouterModelsUrl).build()).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (networkError: IOException) {
            null
        }
    }

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
