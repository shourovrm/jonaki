package app.jonaki.core.modelcatalog

/**
 * OpenRouter lists some models twice: once as itself and once with the
 * suffix ":batch". The second works only through OpenRouter's Batch API,
 * which the app does not use; a chat request to it fails with HTTP 404.
 */
object BatchModels {
    private const val BATCH_SUFFIX = ":batch"

    /** [modelId] is OpenRouter's own id, for example "anthropic/claude-haiku-5.5:batch". */
    fun isBatchId(modelId: String): Boolean = modelId.endsWith(BATCH_SUFFIX)

    /**
     * A "service:modelId" key of a batch model. Other services keep their ids
     * (an Ollama tag may end in anything), so only OpenRouter's keys count.
     */
    fun isBatchKey(modelKey: String): Boolean =
        ModelKey.serviceOf(modelKey) == OpenRouterModels.SERVICE_KEY && isBatchId(ModelKey.modelOf(modelKey))
}
