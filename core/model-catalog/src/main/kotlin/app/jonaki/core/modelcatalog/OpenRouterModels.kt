package app.jonaki.core.modelcatalog

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** Reads OpenRouter's public model list (GET /api/v1/models, no key needed). */
object OpenRouterModels {
    const val SERVICE_KEY = "openrouter"

    private const val TOKENS_PER_MILLION = 1_000_000.0

    fun parse(json: String): List<ModelInfo> {
        val root = Json.parseToJsonElement(json) as? JsonObject ?: return emptyList()
        val data = root["data"] as? JsonArray ?: return emptyList()
        return data.mapNotNull { element -> modelFrom(element as? JsonObject) }
    }

    private fun modelFrom(model: JsonObject?): ModelInfo? {
        if (model == null) return null
        val id = model.text("id") ?: return null
        val pricing = model["pricing"] as? JsonObject
        return ModelInfo(
            serviceKey = SERVICE_KEY,
            modelId = id,
            displayName = model.text("name") ?: id,
            contextWindowTokens = (model["context_length"] as? JsonPrimitive)?.intOrNull,
            inputUsdPerMillion = pricing?.perMillion("prompt"),
            outputUsdPerMillion = pricing?.perMillion("completion"),
            cachedInputUsdPerMillion = pricing?.perMillion("input_cache_read"),
        )
    }

    /** Prices arrive as dollars per token in text; a negative price means "varies" (routers). */
    private fun JsonObject.perMillion(key: String): Double? {
        val perToken = text(key)?.toDoubleOrNull() ?: return null
        if (perToken < 0) return null
        return perToken * TOKENS_PER_MILLION
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
