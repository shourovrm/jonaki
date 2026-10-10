package app.jonaki.core.modelcatalog

import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * OpenRouter's image model list kept in one cache file for a day, like
 * [VideoModelList]. A run reads [cachedModels] and never waits for a
 * download; [load] fills or renews the cache in the background. The raw
 * answer is saved, so a later version of the parser reads old files too.
 */
class ImageModelList(
    private val cacheFile: File,
    private val httpClient: OkHttpClient,
    private val listUrl: String = "${OpenRouterImageModels.BASE_URL}/images/models",
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** The list in the cache file whatever its age, without any request; empty when there is none. */
    fun cachedModels(): List<ImageModelInfo> {
        if (!cacheFile.isFile) {
            return emptyList()
        }
        return runCatching { OpenRouterImageModels.parse(cacheFile.readText()) }.getOrDefault(emptyList())
    }

    /**
     * The list from the cache when it is less than a day old, else from the
     * network (and then saved). When the download fails, an older cache still
     * counts; [ImageModelListResult.Failed] means there is nothing to show.
     */
    suspend fun load(): ImageModelListResult {
        if (isFresh()) {
            val models = cachedModels()
            if (models.isNotEmpty()) {
                return ImageModelListResult.Loaded(models)
            }
        }
        val download = download()
        if (download is Download.Body) {
            val models = OpenRouterImageModels.parse(download.text)
            if (models.isNotEmpty()) {
                save(download.text)
                return ImageModelListResult.Loaded(models)
            }
        }
        val old = cachedModels()
        if (old.isNotEmpty()) {
            return ImageModelListResult.Loaded(old)
        }
        return ImageModelListResult.Failed((download as? Download.Problem)?.reason ?: "the list was empty")
    }

    private fun isFresh(): Boolean {
        val age = clock() - cacheFile.lastModified()
        return cacheFile.isFile && age in 0 until CACHE_MILLIS
    }

    private suspend fun save(text: String) = withContext(Dispatchers.IO) {
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText(text)
        cacheFile.setLastModified(clock())
    }

    private sealed interface Download {
        data class Body(val text: String) : Download

        data class Problem(val reason: String) : Download
    }

    private suspend fun download(): Download = withContext(Dispatchers.IO) {
        try {
            httpClient.newCall(Request.Builder().url(listUrl).build()).execute().use { response ->
                if (response.isSuccessful) Download.Body(response.body?.string().orEmpty()) else Download.Problem("HTTP ${response.code}")
            }
        } catch (networkError: IOException) {
            Download.Problem("no connection")
        }
    }

    private companion object {
        const val CACHE_MILLIS = 24 * 60 * 60 * 1000L
    }
}
