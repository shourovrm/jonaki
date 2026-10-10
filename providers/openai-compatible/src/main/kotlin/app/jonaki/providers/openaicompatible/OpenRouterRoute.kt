package app.jonaki.providers.openaicompatible

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Everything the request needs to know about which OpenRouter endpoint serves
 * a model: the general [routing] choice (D-030) or, when [pinnedProviders] is
 * not empty, the user's own list of providers.
 */
data class OpenRouterRoute(
    val routing: OpenRouterRouting = OpenRouterRouting.AUTOMATIC,
    /** Provider tags such as "deepinfra/fp4", in the order they are tried. */
    val pinnedProviders: List<String> = emptyList(),
    /** Only read when [pinnedProviders] is not empty: may OpenRouter use other providers when these fail. */
    val allowFallbacks: Boolean = true,
) {
    val isPinned: Boolean
        get() = pinnedProviders.isNotEmpty()

    /**
     * The request's "provider" object, or null for no block. A pinned list sends
     * only "order" and "allow_fallbacks": OpenRouter does not document how "order"
     * combines with "sort" or "data_collection", so those are never sent with it.
     */
    fun providerBlock(): JsonObject? {
        if (!isPinned) {
            return routing.providerBlock()
        }
        return buildJsonObject {
            put("order", buildJsonArray { pinnedProviders.forEach { tag -> add(tag) } })
            put("allow_fallbacks", allowFallbacks)
        }
    }

    /** True when a data-policy rejection is answered with a second request on the cheapest endpoint. */
    val mayRetryAsCheapest: Boolean
        get() = !isPinned && routing == OpenRouterRouting.PRIVATE_THEN_CHEAPEST

    companion object {
        val Automatic = OpenRouterRoute(OpenRouterRouting.AUTOMATIC)
        val Cheapest = OpenRouterRoute(OpenRouterRouting.CHEAPEST)
    }
}
