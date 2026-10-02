package app.jonaki.run

import app.jonaki.core.providerapi.ChatProvider
import app.jonaki.providers.gemini.GeminiProvider
import app.jonaki.providers.openaicompatible.OpenAiCompatibleProvider
import app.jonaki.providers.openaicompatible.OpenRouterRouting
import app.jonaki.providers.openaicompatible.ProviderPreset
import app.jonaki.providers.openaicompatible.ProviderPresets
import app.jonaki.settings.ChatService
import okhttp3.OkHttpClient

/** Builds the provider module that speaks each chat service's wire format (D-010). */
object ChatProviders {
    /**
     * [apiKey] is null only for a service without a key (Ollama on the user's
     * network). [routing] and [onRoutingFallback] apply to OpenRouter only (D-030).
     */
    fun create(
        service: ChatService,
        apiKey: String?,
        httpClient: OkHttpClient,
        routing: OpenRouterRouting,
        onRoutingFallback: () -> Unit,
    ): ChatProvider {
        if (service == ChatService.GEMINI) {
            return GeminiProvider(apiKey.orEmpty(), httpClient)
        }
        val preset = presetFor(service)
        return OpenAiCompatibleProvider(
            preset = preset,
            apiKey = apiKey,
            httpClient = httpClient,
            openRouterRouting = routing,
            onRoutingFallback = onRoutingFallback,
        )
    }

    /** The model a version 0.1.0 install used when its model field was blank. */
    fun defaultModel(service: ChatService): String {
        if (service == ChatService.GEMINI) {
            return GeminiProvider.DEFAULT_MODEL
        }
        return presetFor(service).defaultModel
    }

    private fun presetFor(service: ChatService): ProviderPreset =
        ProviderPresets.all.first { preset -> preset.key == service.key }
}
