package app.jonaki.core.modelcatalog

import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** One model of OpenRouter's image model list. The list carries no prices. */
data class ImageModelInfo(
    /** For example "black-forest-labs/flux.2-klein-4b". */
    val id: String,
    val name: String,
    /** The aspect ratios the model takes, for example "16:9"; empty when the list names none. */
    val aspectRatios: List<String> = emptyList(),
)

/** What one model charges, from its endpoints answer. */
data class ImageModelPrice(
    /** What is billed, for example "output_image". */
    val billable: String,
    /** What the price counts, for example "megapixel" or "image". */
    val unit: String,
    val costUsd: Double,
) {
    /** "$0.014 per megapixel". */
    fun describe(): String {
        val amount = String.format(Locale.ENGLISH, "%.4f", costUsd).trimEnd('0').trimEnd('.')
        return "$$amount per $unit"
    }
}

sealed interface ImageModelListResult {
    data class Loaded(val models: List<ImageModelInfo>) : ImageModelListResult

    /** [reason] is short and plain, for example "HTTP 503" or "no connection". */
    data class Failed(val reason: String) : ImageModelListResult
}

/**
 * Reads OpenRouter's public image model list (GET /api/v1/images/models) and
 * a model's prices (GET /api/v1/images/models/{author}/{slug}/endpoints).
 * Neither needs a key. Unknown fields are ignored.
 */
object OpenRouterImageModels {
    const val BASE_URL = "https://openrouter.ai/api/v1"

    fun parse(json: String): List<ImageModelInfo> {
        val data = rootOf(json)?.get("data") as? JsonArray ?: return emptyList()
        return data.mapNotNull { element -> modelFrom(element as? JsonObject) }
    }

    /**
     * The lowest price of the model's output images across its endpoints;
     * null when the answer lists none.
     */
    fun parsePrice(json: String): ImageModelPrice? {
        val endpoints = rootOf(json)?.get("endpoints") as? JsonArray ?: return null
        return endpoints
            .filterIsInstance<JsonObject>()
            .mapNotNull { endpoint -> outputPriceOf(endpoint) }
            .minByOrNull { price -> price.costUsd }
    }

    suspend fun fetchList(httpClient: OkHttpClient, baseUrl: String = BASE_URL): ImageModelListResult {
        val body = when (val answer = get(httpClient, "$baseUrl/images/models")) {
            is Answer.Body -> answer.text
            is Answer.Problem -> return ImageModelListResult.Failed(answer.reason)
        }
        val models = parse(body)
        if (models.isEmpty()) {
            return ImageModelListResult.Failed("the list was empty")
        }
        return ImageModelListResult.Loaded(models)
    }

    /** One request per model; null when it fails, since a missing price must not block the screen. */
    suspend fun fetchPrice(httpClient: OkHttpClient, modelId: String, baseUrl: String = BASE_URL): ImageModelPrice? {
        val answer = get(httpClient, "$baseUrl/images/models/$modelId/endpoints") as? Answer.Body ?: return null
        return parsePrice(answer.text)
    }

    private fun modelFrom(model: JsonObject?): ImageModelInfo? {
        if (model == null) return null
        val id = model.text("id") ?: return null
        val parameters = model["supported_parameters"] as? JsonObject
        val aspectRatioParameter = parameters?.get("aspect_ratio") as? JsonObject
        val ratios = (aspectRatioParameter?.get("values") as? JsonArray).orEmpty()
            .mapNotNull { value -> (value as? JsonPrimitive)?.contentOrNull }
        return ImageModelInfo(id = id, name = model.text("name") ?: id, aspectRatios = ratios)
    }

    private fun outputPriceOf(endpoint: JsonObject): ImageModelPrice? {
        val prices = (endpoint["pricing"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
        val output = prices.firstOrNull { price -> price.text("billable") == "output_image" } ?: return null
        val cost = (output["cost_usd"] as? JsonPrimitive)?.doubleOrNull ?: return null
        return ImageModelPrice("output_image", output.text("unit") ?: "image", cost)
    }

    private fun rootOf(json: String): JsonObject? =
        runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull()

    private sealed interface Answer {
        data class Body(val text: String) : Answer

        data class Problem(val reason: String) : Answer
    }

    private suspend fun get(httpClient: OkHttpClient, url: String): Answer = withContext(Dispatchers.IO) {
        try {
            httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (response.isSuccessful) {
                    Answer.Body(response.body?.string().orEmpty())
                } else {
                    Answer.Problem("HTTP ${response.code}")
                }
            }
        } catch (networkError: IOException) {
            Answer.Problem("no connection")
        }
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
