package app.jonaki.settings

import app.jonaki.providers.openaicompatible.OpenRouterRouting

/**
 * OpenRouter endpoint routing (D-030): one choice for the service and an
 * optional choice per model that replaces it.
 */
data class RoutingSettings(
    val openRouter: OpenRouterRouting = OpenRouterRouting.PRIVATE_THEN_CHEAPEST,
    /** "service:modelId" to its own routing; a model without an entry follows [openRouter]. */
    val overrides: Map<String, OpenRouterRouting> = emptyMap(),
) {
    fun effectiveFor(modelKey: String): OpenRouterRouting = overrides[modelKey] ?: openRouter

    /** A null [routing] removes the model's own choice. */
    fun withOverride(modelKey: String, routing: OpenRouterRouting?): RoutingSettings =
        if (routing == null) copy(overrides = overrides - modelKey) else copy(overrides = overrides + (modelKey to routing))

    companion object {
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
