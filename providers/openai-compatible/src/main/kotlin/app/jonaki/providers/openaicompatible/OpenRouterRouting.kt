package app.jonaki.providers.openaicompatible

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** How OpenRouter picks the endpoint that serves a model (D-030). */
enum class OpenRouterRouting {
    /** Only endpoints that do not keep prompts, cheapest first; falls back to [CHEAPEST] when none exists. */
    PRIVATE_THEN_CHEAPEST,

    /** The cheapest endpoint, whatever its data policy. */
    CHEAPEST,

    /** OpenRouter's own choice: no provider block in the request. */
    AUTOMATIC,
    ;

    /** The request's "provider" object, or null for no block. */
    fun providerBlock(): JsonObject? = when (this) {
        PRIVATE_THEN_CHEAPEST -> buildJsonObject {
            put("data_collection", "deny")
            put("sort", "price")
        }
        CHEAPEST -> buildJsonObject { put("sort", "price") }
        AUTOMATIC -> null
    }

    companion object {
        /**
         * True for OpenRouter's answer when no endpoint of the model meets the data
         * policy: HTTP 404 "No endpoints found matching your data policy …" with
         * failed_routing_step "Filter by Data Policy" (recorded in testdata/openrouter/).
         */
        fun isDataPolicyRejection(statusCode: Int, bodyText: String): Boolean {
            if (statusCode != 404) {
                return false
            }
            val error = runCatching { Json.parseToJsonElement(bodyText) as? JsonObject }.getOrNull()?.get("error") as? JsonObject
            val message = (error?.get("message") as? JsonPrimitive)?.contentOrNull.orEmpty()
            val failedStep = ((error?.get("metadata") as? JsonObject)?.get("failed_routing_step") as? JsonPrimitive)?.contentOrNull
            return failedStep == "Filter by Data Policy" || message.contains("data policy", ignoreCase = true)
        }
    }
}
