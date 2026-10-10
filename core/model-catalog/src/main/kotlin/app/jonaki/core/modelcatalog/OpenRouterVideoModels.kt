package app.jonaki.core.modelcatalog

import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** One model of OpenRouter's video model list. A list the service left out (null) is not checked by anyone. */
data class VideoModelInfo(
    /** For example "google/veo-3.1-lite". */
    val id: String,
    val name: String,
    /** Whole seconds, for example 4, 6, 8; null when the list names none. */
    val supportedDurations: List<Int>?,
    /** For example "480p", "720p"; null when the list names none. */
    val supportedResolutions: List<String>?,
    val supportedAspectRatios: List<String>?,
    /** True when the model makes sound by default, false when it cannot, null when not said. */
    val generatesAudio: Boolean?,
    /** The service's price entries; the key says what the number counts, and the number is written as text. */
    val priceSkus: Map<String, String>,
)

sealed interface VideoModelListResult {
    data class Loaded(val models: List<VideoModelInfo>) : VideoModelListResult

    /** [reason] is short and plain, for example "HTTP 503" or "no connection". */
    data class Failed(val reason: String) : VideoModelListResult
}

/**
 * Reads OpenRouter's public video model list (GET /api/v1/videos/models,
 * no key needed). Prices are inside the list, so one request serves the
 * picker, the added models' rows and the tool. Unknown fields are ignored.
 */
object OpenRouterVideoModels {
    const val LIST_URL = "https://openrouter.ai/api/v1/videos/models"

    fun parse(json: String): List<VideoModelInfo> {
        val root = runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return emptyList()
        val data = root["data"] as? JsonArray ?: return emptyList()
        return data.mapNotNull { element -> modelFrom(element as? JsonObject) }
    }

    private fun modelFrom(model: JsonObject?): VideoModelInfo? {
        if (model == null) return null
        val id = model.text("id") ?: return null
        return VideoModelInfo(
            id = id,
            name = model.text("name") ?: id,
            supportedDurations = (model["supported_durations"] as? JsonArray)?.mapNotNull(::wholeSeconds),
            supportedResolutions = textList(model["supported_resolutions"]),
            supportedAspectRatios = textList(model["supported_aspect_ratios"]),
            generatesAudio = (model["generate_audio"] as? JsonPrimitive)?.booleanOrNull,
            priceSkus = skusFrom(model["pricing_skus"]),
        )
    }

    private fun wholeSeconds(element: JsonElement): Int? {
        val number = (element as? JsonPrimitive)?.doubleOrNull ?: return null
        return if (number == Math.floor(number) && number > 0) number.toInt() else null
    }

    private fun textList(element: JsonElement?): List<String>? =
        (element as? JsonArray)?.mapNotNull { item -> (item as? JsonPrimitive)?.contentOrNull }

    private fun skusFrom(element: JsonElement?): Map<String, String> {
        val skus = element as? JsonObject ?: return emptyMap()
        val entries = mutableMapOf<String, String>()
        for ((key, value) in skus) {
            val text = (value as? JsonPrimitive)?.contentOrNull ?: continue
            entries[key] = text
        }
        return entries
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}

/**
 * The video model list kept in one cache file for a day, like
 * [ModelCatalog] keeps OpenRouter's chat list. A run reads [cachedModels]
 * and never waits for a download; the picker and the background refresh use
 * [load].
 */
class VideoModelList(
    private val cacheFile: File,
    private val httpClient: OkHttpClient,
    private val listUrl: String = OpenRouterVideoModels.LIST_URL,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** The list in the cache file whatever its age, without any request; empty when there is none. */
    fun cachedModels(): List<VideoModelInfo> {
        if (!cacheFile.isFile) {
            return emptyList()
        }
        return runCatching { OpenRouterVideoModels.parse(cacheFile.readText()) }.getOrDefault(emptyList())
    }

    /**
     * The list from the cache when it is less than a day old, else from the
     * network (and then saved). When the download fails, an older cache still
     * counts; [VideoModelListResult.Failed] means there is nothing to show.
     */
    suspend fun load(): VideoModelListResult {
        if (isFresh()) {
            val models = cachedModels()
            if (models.isNotEmpty()) {
                return VideoModelListResult.Loaded(models)
            }
        }
        val download = download()
        if (download is Download.Body) {
            val models = OpenRouterVideoModels.parse(download.text)
            if (models.isNotEmpty()) {
                save(download.text)
                return VideoModelListResult.Loaded(models)
            }
        }
        val old = cachedModels()
        if (old.isNotEmpty()) {
            return VideoModelListResult.Loaded(old)
        }
        return VideoModelListResult.Failed((download as? Download.Problem)?.reason ?: "the list was empty")
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
