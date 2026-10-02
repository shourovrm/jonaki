package app.jonaki.core.modelcatalog

/**
 * Models offered for services that have no public price list the app reads.
 * The user can still type any model id. Prices are estimates copied from
 * OpenRouter's list for the same model on 2026-10-02 (the services' own
 * prices can differ); context windows are from the same list. Model ids are
 * the services' direct-API ids as used in the presets; ids marked UNVERIFIED
 * have not been tried against the live service yet (M10).
 */
object BuiltInModels {
    val all: List<ModelInfo> = listOf(
        // UNVERIFIED ids; prices from deepseek/deepseek-v4.1-flash and deepseek-v4-pro-0813.
        estimate("deepseek", "deepseek-flash", "DeepSeek Flash", 1_048_576, 0.30, 1.20, 0.006),
        estimate("deepseek", "deepseek-pro", "DeepSeek Pro", 1_048_576, 0.66, 1.98, 0.022),
        // gemini-3.8-flash checked in spike S-3; prices from google/gemini-3.x on OpenRouter.
        estimate("gemini", "gemini-3.8-flash", "Gemini 3.8 Flash", 1_048_576, 0.75, 3.75, 0.075),
        estimate("gemini", "gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite", 1_048_576, 0.30, 2.50, 0.03),
        estimate("gemini", "gemini-3.1-pro-preview", "Gemini 3.1 Pro Preview", 1_048_576, 2.00, 12.00, 0.20),
        // UNVERIFIED ids for the direct services below.
        estimate("glm", "glm-5.3-flash", "GLM 5.3 Flash", 1_048_576, 0.15, 0.50, 0.03),
        estimate("glm", "glm-5.3", "GLM 5.3", 1_048_576, 1.40, 4.40, 0.14),
        estimate("mimo", "mimo-v2.6-flash", "MiMo V2.6 Flash", 1_050_000, 0.14, 0.28, 0.0028),
        estimate("mimo", "mimo-v2.6-pro", "MiMo V2.6 Pro", 1_050_000, 0.435, 0.87, 0.0036),
        estimate("openai", "gpt-5-mini", "GPT-5 Mini", 400_000, 0.25, 2.00, 0.025),
        estimate("openai", "gpt-5.4-nano", "GPT-5.4 Nano", 400_000, 0.20, 1.25, 0.02),
        estimate("openai", "gpt-5.5", "GPT-5.5", 1_050_000, 5.00, 30.00, 0.50),
        // Ollama Cloud is a subscription, so no per-token price.
        ModelInfo("ollama-cloud", "gpt-oss:120b", "gpt-oss 120B", 131_072, null, null, isEstimate = true),
    )

    private fun estimate(
        serviceKey: String,
        modelId: String,
        displayName: String,
        contextWindowTokens: Int,
        inputUsdPerMillion: Double,
        outputUsdPerMillion: Double,
        cachedInputUsdPerMillion: Double,
    ) = ModelInfo(
        serviceKey = serviceKey,
        modelId = modelId,
        displayName = displayName,
        contextWindowTokens = contextWindowTokens,
        inputUsdPerMillion = inputUsdPerMillion,
        outputUsdPerMillion = outputUsdPerMillion,
        cachedInputUsdPerMillion = cachedInputUsdPerMillion,
        isEstimate = true,
    )
}
