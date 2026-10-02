package app.jonaki.providers.openaicompatible

/**
 * One service that speaks the OpenAI chat completions format (D-010). The user
 * picks a preset and adds a key; base URL and model stay editable.
 */
data class ProviderPreset(
    val key: String,
    val displayName: String,
    val baseUrl: String,
    val defaultModel: String,
    val needsApiKey: Boolean = true,
    /** OpenRouter reports each request's cost in USD when asked with `usage.include` (D-027). */
    val reportsCost: Boolean = false,
)

object ProviderPresets {
    val openRouter = ProviderPreset(
        key = "openrouter",
        displayName = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "z-ai/glm-5.3-flash",
        reportsCost = true,
    )

    val deepSeek = ProviderPreset(
        key = "deepseek",
        displayName = "DeepSeek",
        baseUrl = "https://api.deepseek.com",
        defaultModel = "deepseek-flash",
    )

    // The rows below come from the author's pi setup or the services' documentation
    // and are checked against the live service in M10.
    val glm = ProviderPreset(
        key = "glm",
        displayName = "GLM (Z.ai)",
        baseUrl = "https://api.z.ai/api/paas/v4",
        defaultModel = "glm-5.3-flash",
    )

    val xiaomiMimo = ProviderPreset(
        key = "mimo",
        displayName = "Xiaomi MiMo",
        baseUrl = "https://api.xiaomimimo.com/v1",
        defaultModel = "mimo-v2.6-flash",
    )

    val ollamaCloud = ProviderPreset(
        key = "ollama-cloud",
        displayName = "Ollama Cloud",
        baseUrl = "https://ollama.com/v1",
        defaultModel = "gpt-oss:120b",
    )

    val localOllama = ProviderPreset(
        key = "ollama-local",
        displayName = "Ollama on my network",
        baseUrl = "http://localhost:11434/v1",
        defaultModel = "llama3.2",
        needsApiKey = false,
    )

    val openAi = ProviderPreset(
        key = "openai",
        displayName = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-5-mini",
    )

    val all: List<ProviderPreset> = listOf(openRouter, deepSeek, glm, xiaomiMimo, ollamaCloud, localOllama, openAi)
}
