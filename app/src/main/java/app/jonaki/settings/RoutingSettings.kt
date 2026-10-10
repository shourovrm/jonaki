package app.jonaki.settings

import app.jonaki.providers.openaicompatible.OpenRouterRoute
import app.jonaki.providers.openaicompatible.OpenRouterRouting

/**
 * OpenRouter endpoint routing (D-030): one choice for the service and an
 * optional choice per model that replaces it.
 */
data class RoutingSettings(
    val openRouter: OpenRouterRouting = OpenRouterRouting.PRIVATE_THEN_CHEAPEST,
    /** "service:modelId" to its own routing; a model without an entry follows [openRouter]. */
    val overrides: Map<String, OpenRouterRouting> = emptyMap(),
    /** "service:modelId" to the providers the user chose for it; they replace the routing choice. */
    val pinned: Map<String, PinnedProviders> = emptyMap(),
) {
    fun effectiveFor(modelKey: String): OpenRouterRoute {
        val pinnedForModel = pinned[modelKey]
        return OpenRouterRoute(
            routing = overrides[modelKey] ?: openRouter,
            pinnedProviders = pinnedForModel?.tags.orEmpty(),
            allowFallbacks = pinnedForModel?.allowFallbacks ?: true,
        )
    }

    /** A null or empty [providers] returns the model to its routing choice. */
    fun withPinned(modelKey: String, providers: PinnedProviders?): RoutingSettings =
        if (providers == null || providers.tags.isEmpty()) copy(pinned = pinned - modelKey) else copy(pinned = pinned + (modelKey to providers))

    /** A null [routing] removes the model's own choice. */
    fun withOverride(modelKey: String, routing: OpenRouterRouting?): RoutingSettings =
        if (routing == null) copy(overrides = overrides - modelKey) else copy(overrides = overrides + (modelKey to routing))

    companion object {
        private const val TAG_SEPARATOR = ' '

        /**
         * One "modelKey<TAB>allowFallbacks<TAB>tag tag …" per line, tags in the order they are tried.
         * A tag is a slug such as "deepinfra/fp4" and never holds whitespace, so a space separates
         * tags and a tab separates fields, as a model key holds neither.
         */
        fun pinnedToText(pinned: Map<String, PinnedProviders>): String =
            pinned.entries
                .filter { (_, providers) -> providers.tags.isNotEmpty() }
                .joinToString("\n") { (modelKey, providers) ->
                    "$modelKey\t${providers.allowFallbacks}\t${providers.tags.joinToString(TAG_SEPARATOR.toString())}"
                }

        /** Lines that cannot be read are dropped. */
        fun pinnedFromText(text: String): Map<String, PinnedProviders> =
            text.lines().mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size != 3 || parts[0].isBlank()) return@mapNotNull null
                val allowFallbacks = when (parts[1]) {
                    "true" -> true
                    "false" -> false
                    else -> return@mapNotNull null
                }
                val tags = parts[2].split(TAG_SEPARATOR).filter { tag -> tag.isNotBlank() }
                if (tags.isEmpty()) return@mapNotNull null
                parts[0] to PinnedProviders(tags, allowFallbacks)
            }.toMap()

        /** One "modelKey<TAB>ROUTING" per line; model ids can hold ':' and ',' but not tabs or newlines. */
        fun overridesToText(overrides: Map<String, OpenRouterRouting>): String =
            overrides.entries.joinToString("\n") { (modelKey, routing) -> "$modelKey\t${routing.name}" }

        fun overridesFromText(text: String): Map<String, OpenRouterRouting> =
            text.lines().mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size != 2) return@mapNotNull null
                val routing = OpenRouterRouting.entries.firstOrNull { it.name == parts[1] } ?: return@mapNotNull null
                parts[0] to routing
            }.toMap()
    }
}

/** The providers a user chose for one model, as OpenRouter tags in the order they are tried. */
data class PinnedProviders(
    val tags: List<String>,
    /** May OpenRouter use other providers when these fail. */
    val allowFallbacks: Boolean = true,
)
