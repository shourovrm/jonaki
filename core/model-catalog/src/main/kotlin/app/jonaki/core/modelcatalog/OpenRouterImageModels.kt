package app.jonaki.core.modelcatalog

import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** One model of OpenRouter's image model list. The list carries no prices. */
data class ImageModelInfo(
    /** For example "black-forest-labs/flux.2-klein-4b". */
    val id: String,
    val name: String,
    /** The aspect ratios the model takes, for example "16:9"; empty when the list names none. */
    val aspectRatios: List<String> = emptyList(),
    /** True when the list says the model's only output format is "svg" (a vector model); false for every other model. */
    val isVector: Boolean = false,
    /** The values of the model's `quality` parameter; null when it does not declare one. */
    val qualityValues: List<String>? = null,
    /** The values of the model's `resolution` parameter; null when it does not declare one. */
    val resolutionValues: List<String>? = null,
    /** The range of the model's `input_references` parameter (reference pictures); null when it does not declare one. */
    val referenceRange: IntRange? = null,
)

/** What one model charges, from its endpoints answer. */
data class ImageModelPrice(
    /** What is billed, for example "output_image". */
    val billable: String,
    /** What the price counts, for example "megapixel" or "image". */
    val unit: String,
    val costUsd: Double,
) {
    /** "$0.014 per megapixel", "$0.007 per image", or "$30 per 1M image tokens" for a price per token. */
    fun describe(): String {
        if (unit == "token") {
            return "${dollars(costUsd * TOKENS_PER_MILLION)} per 1M image tokens"
        }
        return "${dollars(costUsd)} per $unit"
    }

    private fun dollars(amount: Double): String {
        val text = String.format(Locale.ENGLISH, "%.4f", amount).trimEnd('0').trimEnd('.')
        return "$$text"
    }

    private companion object {
        const val TOKENS_PER_MILLION = 1_000_000.0
    }
}

/** What loading one model's price gave. */
sealed interface ImagePriceResult {
    data class Priced(val price: ImageModelPrice) : ImagePriceResult

    /** The request worked and the answer lists no output image price. */
    data object NoPrice : ImagePriceResult

    /** The request failed; this is never cached. */
    data object Failed : ImagePriceResult
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

    /** One request per model; a failure is a result, since a missing price must not block the screen. */
    suspend fun fetchPrice(httpClient: OkHttpClient, modelId: String, baseUrl: String = BASE_URL): ImagePriceResult {
        val answer = get(httpClient, "$baseUrl/images/models/$modelId/endpoints") as? Answer.Body ?: return ImagePriceResult.Failed
        val price = parsePrice(answer.text) ?: return ImagePriceResult.NoPrice
        return ImagePriceResult.Priced(price)
    }

    private fun modelFrom(model: JsonObject?): ImageModelInfo? {
        if (model == null) return null
        val id = model.text("id") ?: return null
        val parameters = model["supported_parameters"] as? JsonObject
        val aspectRatioParameter = parameters?.get("aspect_ratio") as? JsonObject
        val ratios = (aspectRatioParameter?.get("values") as? JsonArray).orEmpty()
            .mapNotNull { value -> (value as? JsonPrimitive)?.contentOrNull }
        return ImageModelInfo(
            id = id,
            name = model.text("name") ?: id,
            aspectRatios = ratios,
            isVector = isVectorModel(parameters),
            qualityValues = enumValues(parameters?.get("quality")),
            resolutionValues = enumValues(parameters?.get("resolution")),
            referenceRange = rangeOf(parameters?.get("input_references")),
        )
    }

    /** `{"type":"enum","values":[…]}`; null when the parameter is absent. */
    private fun enumValues(parameter: JsonElement?): List<String>? {
        val values = (parameter as? JsonObject)?.get("values") as? JsonArray ?: return null
        return values.mapNotNull { value -> (value as? JsonPrimitive)?.contentOrNull }
    }

    /** `{"type":"range","min":0,"max":14}`; null when the parameter is absent or has no usable numbers. */
    private fun rangeOf(parameter: JsonElement?): IntRange? {
        val range = parameter as? JsonObject ?: return null
        val minimum = (range["min"] as? JsonPrimitive)?.intOrNull ?: 0
        val maximum = (range["max"] as? JsonPrimitive)?.intOrNull ?: return null
        return minimum..maximum
    }

    /**
     * `output_format` is `{"type":"enum","values":["svg"]}` for the Recraft
     * vector models; other models have no `output_format` or list raster
     * formats such as "png" and "jpeg" (checked 2026-10-10).
     */
    private fun isVectorModel(parameters: JsonObject?): Boolean {
        val outputFormat = parameters?.get("output_format") as? JsonObject
        val values = (outputFormat?.get("values") as? JsonArray).orEmpty()
            .mapNotNull { value -> (value as? JsonPrimitive)?.contentOrNull }
        return values == listOf("svg")
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

    /**
     * The call is cancelled when the coroutine is, so closing the picker
     * stops a request already in flight. It uses execute() and not enqueue()
     * because OkHttp's dispatcher allows only five calls at once to one
     * host, and the caller sets its own limit.
     */
    private suspend fun get(httpClient: OkHttpClient, url: String): Answer = withContext(Dispatchers.IO) {
        val call = httpClient.newCall(Request.Builder().url(url).build())
        // The handler runs on the thread that cancels, while this thread is blocked in execute().
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            val answer = try {
                call.execute().use { response ->
                    if (response.isSuccessful) {
                        Answer.Body(response.body?.string().orEmpty())
                    } else {
                        Answer.Problem("HTTP ${response.code}")
                    }
                }
            } catch (networkError: IOException) {
                Answer.Problem("no connection")
            }
            continuation.resume(answer)
        }
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
