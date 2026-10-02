package app.jonaki.core.modelcatalog

import app.jonaki.core.providerapi.Usage

/** What the app knows about one model of one chat service (D-027). */
data class ModelInfo(
    /** The chat service, for example "openrouter" or "gemini". */
    val serviceKey: String,
    /** The id the service expects, for example "z-ai/glm-5.3-flash". */
    val modelId: String,
    val displayName: String,
    /** Null when unknown; the status strip then shows no percentage. */
    val contextWindowTokens: Int?,
    val inputUsdPerMillion: Double?,
    val outputUsdPerMillion: Double?,
    val cachedInputUsdPerMillion: Double? = null,
    /** True for built-in rows whose prices are copied estimates, not the service's own list. */
    val isEstimate: Boolean = false,
    /** OpenRouter lists "reasoning" among the model's parameters (D-057). */
    val supportsThinkingLevel: Boolean = false,
    /** True only when the list says the model takes images; unknown counts as no (D-049). */
    val acceptsImages: Boolean = false,
) {
    /** "service:modelId", the form stored in settings and on each thread. */
    val key: String
        get() = ModelKey.of(serviceKey, modelId)
}

/** Joins and splits "service:modelId"; model ids may themselves contain ':' (Ollama tags). */
object ModelKey {
    fun of(serviceKey: String, modelId: String): String = "$serviceKey:$modelId"

    fun serviceOf(key: String): String = key.substringBefore(':')

    fun modelOf(key: String): String = key.substringAfter(':')
}

/** Prices a model call in US dollars. */
object CostCalculator {
    private const val TOKENS_PER_MILLION = 1_000_000.0

    /**
     * The service's own figure when it reports one (OpenRouter does), otherwise
     * tokens times the model's prices; null when neither is known.
     */
    fun costUsd(usage: Usage, model: ModelInfo?): Double? {
        usage.costUsd?.let { return it }
        val inputRate = model?.inputUsdPerMillion ?: return null
        val outputRate = model.outputUsdPerMillion ?: return null
        val cachedTokens = (usage.cachedInputTokens ?: 0).coerceIn(0, usage.inputTokens)
        val uncachedTokens = usage.inputTokens - cachedTokens
        val cachedRate = model.cachedInputUsdPerMillion ?: inputRate
        val dollars = uncachedTokens * inputRate + cachedTokens * cachedRate + usage.outputTokens * outputRate
        return dollars / TOKENS_PER_MILLION
    }
}
