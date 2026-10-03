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
    /** Where this service takes the thinking level; NONE means it offers no such setting (D-057). */
    val thinkingField: ThinkingField = ThinkingField.NONE,
    /** The service answers GET <baseUrl>/models in the OpenAI format, so the model picker can list its ids (D-105). */
    val listsModels: Boolean = false,
)

enum class ThinkingField {
    NONE,

    /** OpenRouter's `reasoning` object. */
    OPENROUTER,

    /** OpenAI's `reasoning_effort` text. */
    OPENAI,
}

object ProviderPresets {
    val openRouter = ProviderPreset(
        key = "openrouter",
        displayName = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "z-ai/glm-5.3-flash",
        reportsCost = true,
        thinkingField = ThinkingField.OPENROUTER,
    )

    val deepSeek = ProviderPreset(
        key = "deepseek",
        displayName = "DeepSeek",
        baseUrl = "https://api.deepseek.com",
        defaultModel = "deepseek-flash",
    )

    // Base URLs below were checked against each service's documentation on
    // 2026-10-03 (sources in D-106). Z.ai documents no model list.
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
        // Undocumented, but GET /v1/models answers 401 without a key where an unknown path answers 404.
        listsModels = true,
    )

    val ollamaCloud = ProviderPreset(
        key = "ollama-cloud",
        displayName = "Ollama Cloud",
        baseUrl = "https://ollama.com/v1",
        defaultModel = "gpt-oss:120b",
        listsModels = true,
    )

    val localOllama = ProviderPreset(
        key = "ollama-local",
        displayName = "Ollama on my network",
        baseUrl = "http://localhost:11434/v1",
        defaultModel = "llama3.2",
        needsApiKey = false,
        listsModels = true,
    )

    val openAi = ProviderPreset(
        key = "openai",
        displayName = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-5-mini",
        thinkingField = ThinkingField.OPENAI,
        listsModels = true,
    )

    val miniMax = ProviderPreset(
        key = "minimax",
        displayName = "MiniMax",
        baseUrl = "https://api.minimax.io/v1",
        defaultModel = "MiniMax-M3",
        listsModels = true,
    )

    /** Alibaba Model Studio's international (Singapore) endpoint in OpenAI-compatible mode. */
    val qwen = ProviderPreset(
        key = "qwen",
        displayName = "Qwen",
        baseUrl = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
        defaultModel = "qwen3.8-flash",
        listsModels = true,
    )

    val all: List<ProviderPreset> = listOf(
        openRouter,
        deepSeek,
        glm,
        xiaomiMimo,
        ollamaCloud,
        localOllama,
        openAi,
        miniMax,
        qwen,
    )
}
