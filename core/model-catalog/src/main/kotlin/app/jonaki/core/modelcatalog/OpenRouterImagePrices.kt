package app.jonaki.core.modelcatalog

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient

/**
 * The output image price of OpenRouter's image models. The model list has no
 * prices and each price needs its own request, so this loads them a few at a
 * time and keeps every answer in one file for a day. A failed request is
 * reported to the caller and never written to the file.
 */
class OpenRouterImagePrices(
    private val httpClient: OkHttpClient,
    private val cacheFile: File,
    private val baseUrl: String = OpenRouterImageModels.BASE_URL,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxInFlight: Int = DEFAULT_MAX_IN_FLIGHT,
) {
    /** What the file holds for a model: the time it was fetched and its price, null for "no price listed". */
    private class Entry(val fetchedAtMillis: Long, val price: ImageModelPrice?)

    private val writeLock = Mutex()

    /**
     * The prices in the file that are less than a day old, without any
     * request. A model whose answer listed no price maps to null.
     */
    fun cachedPrices(): Map<String, ImageModelPrice?> =
        readEntries().filterValues { entry -> isFresh(entry) }.mapValues { (_, entry) -> entry.price }

    /**
     * Gives every model's result to [onResult], as each one arrives: the
     * fresh ones from the file first, the rest from the network with at most
     * [maxInFlight] requests at a time. Cancelling the caller cancels the
     * requests in flight and starts no further one. [onResult] may be called
     * from several threads.
     */
    suspend fun load(modelIds: List<String>, onResult: (modelId: String, result: ImagePriceResult) -> Unit) {
        val fresh = cachedPrices()
        val missing = mutableListOf<String>()
        for (modelId in modelIds.distinct()) {
            if (modelId in fresh) {
                onResult(modelId, resultOf(fresh.getValue(modelId)))
            } else {
                missing += modelId
            }
        }
        val permits = Semaphore(maxInFlight)
        coroutineScope {
            for (modelId in missing) {
                launch(Dispatchers.IO) {
                    permits.acquire()
                    try {
                        val result = OpenRouterImageModels.fetchPrice(httpClient, modelId, baseUrl)
                        ensureActive()
                        store(modelId, result)
                        onResult(modelId, result)
                    } finally {
                        permits.release()
                    }
                }
            }
        }
    }

    private fun resultOf(price: ImageModelPrice?): ImagePriceResult =
        if (price == null) ImagePriceResult.NoPrice else ImagePriceResult.Priced(price)

    private suspend fun store(modelId: String, result: ImagePriceResult) {
        val price = when (result) {
            ImagePriceResult.Failed -> return
            ImagePriceResult.NoPrice -> null
            is ImagePriceResult.Priced -> result.price
        }
        writeLock.withLock {
            withContext(Dispatchers.IO) {
                val entries = readEntries().filterValues { entry -> isFresh(entry) }.toMutableMap()
                entries[modelId] = Entry(clock(), price)
                writeEntries(entries)
            }
        }
    }

    private fun isFresh(entry: Entry): Boolean {
        val age = clock() - entry.fetchedAtMillis
        return age in 0 until CACHE_MILLIS
    }

    private fun readEntries(): Map<String, Entry> {
        val root = runCatching { Json.parseToJsonElement(cacheFile.readText()) as? JsonObject }.getOrNull() ?: return emptyMap()
        val entries = mutableMapOf<String, Entry>()
        for ((modelId, element) in root) {
            val entry = element as? JsonObject ?: continue
            val fetchedAt = (entry["fetchedAt"] as? JsonPrimitive)?.longOrNull ?: continue
            entries[modelId] = Entry(fetchedAt, priceFrom(entry["price"]))
        }
        return entries
    }

    private fun priceFrom(element: JsonElement?): ImageModelPrice? {
        val price = element as? JsonObject ?: return null
        val unit = (price["unit"] as? JsonPrimitive)?.contentOrNull ?: return null
        val cost = (price["costUsd"] as? JsonPrimitive)?.doubleOrNull ?: return null
        return ImageModelPrice("output_image", unit, cost)
    }

    private fun writeEntries(entries: Map<String, Entry>) {
        val root = buildJsonObject {
            for ((modelId, entry) in entries) {
                put(
                    modelId,
                    buildJsonObject {
                        put("fetchedAt", entry.fetchedAtMillis)
                        if (entry.price == null) {
                            put("price", JsonNull)
                        } else {
                            put(
                                "price",
                                buildJsonObject {
                                    put("unit", entry.price.unit)
                                    put("costUsd", entry.price.costUsd)
                                },
                            )
                        }
                    },
                )
            }
        }
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText(root.toString())
    }

    companion object {
        const val DEFAULT_MAX_IN_FLIGHT = 6
        private const val CACHE_MILLIS = 24 * 60 * 60 * 1000L
    }
}
