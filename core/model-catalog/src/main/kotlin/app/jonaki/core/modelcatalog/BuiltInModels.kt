package app.jonaki.core.modelcatalog

/**
 * Models offered for services that have no public price list the app reads.
 * The user can still type any model id, and services with their own model
 * list add their ids without a price (D-105).
 *
 * Rows made with [official] carry the service's own pay-as-you-go prices,
 * read from its price page on 2026-10-03 (sources in D-106); the context
 * window and image input come from OpenRouter's list of the same day when
 * the service's page does not state them. Rows made with [estimate] copy
 * OpenRouter's price for the same model on 2026-10-02. Where a service
 * prices by request size, the row has the smallest tier. Image input is set
 * only where a list shows it; unknown counts as no (D-049).
 */
object BuiltInModels {
    val all: List<ModelInfo> = listOf(
        // UNVERIFIED ids; prices from deepseek/deepseek-v4.1-flash and deepseek-v4-pro-0813.
        estimate("deepseek", "deepseek-flash", "DeepSeek Flash", 1_048_576, 0.30, 1.20, 0.006),
        estimate("deepseek", "deepseek-pro", "DeepSeek Pro", 1_048_576, 0.66, 1.98, 0.022),
        // gemini-3.8-flash checked in spike S-3; prices from google/gemini-3.x on OpenRouter.
        estimate("gemini", "gemini-3.8-flash", "Gemini 3.8 Flash", 1_048_576, 0.75, 3.75, 0.075, acceptsImages = true),
        estimate("gemini", "gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite", 1_048_576, 0.30, 2.50, 0.03, acceptsImages = true),
        estimate("gemini", "gemini-3.1-pro-preview", "Gemini 3.1 Pro Preview", 1_048_576, 2.00, 12.00, 0.20, acceptsImages = true),
        // Z.ai price page; context and modalities from Z.ai's model pages. Z.ai documents no model list.
        official("glm", "glm-5.3-flash", "GLM 5.3 Flash", 1_048_576, 0.15, 0.50, 0.03, acceptsImages = true),
        official("glm", "glm-5.3-flashx", "GLM 5.3 FlashX", 1_048_576, 0.37, 1.25, 0.075, acceptsImages = true),
        official("glm", "glm-5.3", "GLM 5.3", 1_048_576, 1.40, 4.40, 0.26),
        official("glm", "glm-5.2", "GLM 5.2", 1_048_576, 1.40, 4.40, 0.26),
        official("glm", "glm-4.7-flash", "GLM 4.7 Flash", 200_000, 0.0, 0.0, 0.0),
        // Xiaomi MiMo pay-as-you-go page (real-time API).
        official("mimo", "mimo-v2.6-flash", "MiMo V2.6 Flash", 1_050_000, 0.14, 0.28, 0.0028, acceptsImages = true),
        official("mimo", "mimo-v2.6-pro", "MiMo V2.6 Pro", 1_050_000, 0.435, 0.87, 0.0036, acceptsImages = true),
        official("mimo", "mimo-v2.6-pro-ultraspeed", "MiMo V2.6 Pro UltraSpeed", 1_048_576, 4.35, 8.70, 0.036, acceptsImages = true),
        // OpenAI price page, Standard tier.
        official("openai", "gpt-6-luna", "GPT-6 Luna", 1_050_000, 0.10, 0.50, 0.01, acceptsImages = true),
        official("openai", "gpt-6-sol", "GPT-6 Sol", 1_050_000, 2.00, 10.00, 0.20, acceptsImages = true),
        official("openai", "gpt-6.1-sol", "GPT-6.1 Sol", 1_050_000, 2.00, 10.00, 0.10, acceptsImages = true),
        official("openai", "gpt-6-astra", "GPT-6 Astra", 1_050_000, 10.00, 50.00, 1.00, acceptsImages = true),
        official("openai", "gpt-5.6-luna", "GPT-5.6 Luna", 1_050_000, 0.20, 1.20, 0.02, acceptsImages = true),
        official("openai", "gpt-5-mini", "GPT-5 Mini", 400_000, 0.25, 2.00, 0.025, acceptsImages = true),
        official("openai", "gpt-5.4-nano", "GPT-5.4 Nano", 400_000, 0.20, 1.25, 0.02, acceptsImages = true),
        official("openai", "gpt-5.5", "GPT-5.5", 1_050_000, 5.00, 30.00, 0.50, acceptsImages = true),
        // MiniMax pay-as-you-go page; MiniMax-M3 up to 512K input tokens (double above).
        official("minimax", "MiniMax-M3", "MiniMax M3", 1_048_576, 0.30, 1.20, 0.06, acceptsImages = true),
        official("minimax", "MiniMax-M2.7", "MiniMax M2.7", 204_800, 0.30, 1.20, 0.06),
        official("minimax", "MiniMax-M2.7-highspeed", "MiniMax M2.7 Highspeed", 204_800, 0.60, 2.40, 0.06),
        // Alibaba Model Studio, International (Singapore), smallest tier; a cache hit costs 20 % of input (implicit cache).
        official("qwen", "qwen3.8-max", "Qwen3.8 Max", 1_000_000, 2.00, 6.00, 0.40),
        official("qwen", "qwen3.7-plus", "Qwen3.7 Plus", 1_000_000, 0.40, 1.60, 0.08, acceptsImages = true),
        official("qwen", "qwen3.8-flash", "Qwen3.8 Flash", 1_000_000, 0.15, 0.47, 0.03),
        official("qwen", "qwen3.7-flash", "Qwen3.7 Flash", 1_000_000, 0.03, 0.13, 0.006, acceptsImages = true),
        // Ollama Cloud is a subscription, so no per-token price.
        ModelInfo("ollama-cloud", "gpt-oss:120b", "gpt-oss 120B", 131_072, null, null, isEstimate = true),
    )

    private fun official(
        serviceKey: String,
        modelId: String,
        displayName: String,
        contextWindowTokens: Int,
        inputUsdPerMillion: Double,
        outputUsdPerMillion: Double,
        cachedInputUsdPerMillion: Double,
        acceptsImages: Boolean = false,
    ) = estimate(
        serviceKey, modelId, displayName, contextWindowTokens, inputUsdPerMillion, outputUsdPerMillion,
        cachedInputUsdPerMillion, acceptsImages,
    ).copy(isEstimate = false)

    private fun estimate(
        serviceKey: String,
        modelId: String,
        displayName: String,
        contextWindowTokens: Int,
        inputUsdPerMillion: Double,
        outputUsdPerMillion: Double,
        cachedInputUsdPerMillion: Double,
        acceptsImages: Boolean = false,
    ) = ModelInfo(
        serviceKey = serviceKey,
        modelId = modelId,
        displayName = displayName,
        contextWindowTokens = contextWindowTokens,
        inputUsdPerMillion = inputUsdPerMillion,
        outputUsdPerMillion = outputUsdPerMillion,
        cachedInputUsdPerMillion = cachedInputUsdPerMillion,
        isEstimate = true,
        acceptsImages = acceptsImages,
    )
}
