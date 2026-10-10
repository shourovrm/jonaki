package app.jonaki.settings

import app.jonaki.core.modelcatalog.ModelKey

/** Image services the user can add as cards in Settings > Models > Image generation. */
enum class ImageService(
    /** Stable id used in model keys, for example "openrouter". Same as the chat service's key where the account is the same. */
    val key: String,
    val displayName: String,
    /**
     * The saved key. A service with the same account as a chat service names
     * the same [SecretName], so one key serves both and removing it in one
     * place removes it in the other (like Ollama Cloud and Ollama web search).
     */
    val secret: SecretName,
    /**
     * Image models offered in the picker when the service has no list the app
     * can read. OpenRouter's picker loads its own public list, so it has none.
     */
    val suggestedModels: List<SuggestedImageModel> = emptyList(),
) {
    OPENROUTER("openrouter", "OpenRouter", SecretName.OPENROUTER),

    // Google's model list (GET /models) has no field that marks image-output models,
    // so the ids below are the ones named on https://ai.google.dev/gemini-api/docs/image-generation
    // and https://ai.google.dev/gemini-api/docs/batch-api (checked 2026-10-10); any other id can be typed.
    GEMINI(
        "gemini",
        "Gemini",
        SecretName.GEMINI,
        suggestedModels = listOf(
            SuggestedImageModel("gemini-2.5-flash-image", "Gemini 2.5 Flash Image"),
            SuggestedImageModel("gemini-3.1-flash-image", "Gemini 3.1 Flash Image"),
            SuggestedImageModel("gemini-3.1-flash-lite-image", "Gemini 3.1 Flash Lite Image"),
            SuggestedImageModel("gemini-3-pro-image", "Gemini 3 Pro Image"),
            SuggestedImageModel("gemini-3-pro-image-preview", "Gemini 3 Pro Image (preview)"),
            SuggestedImageModel("gemini-nano-banana-2.1", "Gemini Nano Banana 2.1"),
        ),
    ),
    ;

    companion object {
        fun byKey(key: String): ImageService? = entries.firstOrNull { service -> service.key == key }
    }
}

data class SuggestedImageModel(val id: String, val name: String)

/** What [ImageModels] is saved as in preferences: three plain texts. */
data class StoredImageModels(
    /** Service keys, one per line, in the order added. */
    val servicesText: String,
    /** "service:modelId", one per line. */
    val modelKeysText: String,
    val defaultModelKey: String?,
)

/**
 * The image services and models the user added (like [ChatModels]): services
 * in the order added, any number of models per service, and one starred model
 * across all services that generate_image uses when the agent names none.
 * Every change returns a new value.
 */
data class ImageModels(
    val addedServices: List<ImageService> = emptyList(),
    /** Model ids per service, without the service prefix. */
    val modelsByService: Map<ImageService, List<String>> = emptyMap(),
    /** "service:modelId" of the starred model; null when no model is added. */
    val defaultModelKey: String? = null,
) {
    /** Every model as "service:modelId", in card order. */
    val allModelKeys: List<String>
        get() = addedServices.flatMap { service ->
            modelsByService[service].orEmpty().map { modelId -> ModelKey.of(service.key, modelId) }
        }

    /**
     * The models generate_image may offer: those of services with a saved key.
     * The tool is offered when this is not empty.
     */
    fun usableModelKeys(hasKey: (ImageService) -> Boolean): List<String> =
        addedServices.filter(hasKey).flatMap { service ->
            modelsByService[service].orEmpty().map { modelId -> ModelKey.of(service.key, modelId) }
        }

    fun addService(service: ImageService): ImageModels {
        if (service in addedServices) {
            return this
        }
        return copy(addedServices = addedServices + service)
    }

    /** Drops the service and its models; its key stays, since a chat service may use it. */
    fun removeService(service: ImageService): ImageModels =
        copy(addedServices = addedServices - service, modelsByService = modelsByService - service).withValidDefault()

    /** Adds a model; a blank or already-listed id changes nothing. The first model added is starred. */
    fun addModel(service: ImageService, modelId: String): ImageModels {
        val trimmed = modelId.trim()
        val existing = modelsByService[service].orEmpty()
        if (trimmed.isEmpty() || trimmed in existing) {
            return addService(service)
        }
        val added = addService(service).copy(modelsByService = modelsByService + (service to existing + trimmed))
        return added.withValidDefault()
    }

    fun removeModel(modelKey: String): ImageModels {
        val service = ImageService.byKey(ModelKey.serviceOf(modelKey)) ?: return this
        val remainingIds = modelsByService[service].orEmpty() - ModelKey.modelOf(modelKey)
        return copy(modelsByService = modelsByService + (service to remainingIds)).withValidDefault()
    }

    /** Stars [modelKey]; a key that is not listed changes nothing. */
    fun setDefault(modelKey: String): ImageModels {
        if (modelKey !in allModelKeys) {
            return this
        }
        return copy(defaultModelKey = modelKey)
    }

    /** Keeps the star on a listed model: the current one if still listed, else the first, else none. */
    private fun withValidDefault(): ImageModels {
        val keys = allModelKeys
        if (defaultModelKey in keys) {
            return this
        }
        return copy(defaultModelKey = keys.firstOrNull())
    }

    companion object {
        fun toStored(models: ImageModels): StoredImageModels = StoredImageModels(
            servicesText = models.addedServices.joinToString("\n") { service -> service.key },
            // Model ids hold slashes, colons and dots, so they are stored one key per line, like chat models.
            modelKeysText = models.allModelKeys.joinToString("\n"),
            defaultModelKey = models.defaultModelKey,
        )

        /**
         * Reads what [toStored] wrote. A null [servicesText] and [modelKeysText]
         * mean the new format was never saved; then the first version's values
         * count: [legacyModelsText] was one OpenRouter model id per line
         * (`image_models`) and [legacyDefaultModel] one of those ids
         * (`image_default_model`). They become models of the OpenRouter
         * service with the same star. Once the new format exists it wins, even
         * when empty, so models the user removed do not come back.
         */
        fun fromStored(
            servicesText: String?,
            modelKeysText: String?,
            defaultModelKey: String?,
            legacyModelsText: String?,
            legacyDefaultModel: String?,
        ): ImageModels {
            if (servicesText == null && modelKeysText == null) {
                return fromLegacy(legacyModelsText.orEmpty(), legacyDefaultModel)
            }
            var models = ImageModels()
            for (line in nonBlankLines(servicesText.orEmpty())) {
                ImageService.byKey(line)?.let { service -> models = models.addService(service) }
            }
            for (key in nonBlankLines(modelKeysText.orEmpty())) {
                val service = ImageService.byKey(ModelKey.serviceOf(key)) ?: continue
                models = models.addModel(service, ModelKey.modelOf(key))
            }
            return models.starred(defaultModelKey)
        }

        private fun fromLegacy(modelsText: String, defaultModelId: String?): ImageModels {
            var models = ImageModels()
            for (modelId in nonBlankLines(modelsText)) {
                models = models.addModel(ImageService.OPENROUTER, modelId)
            }
            return models.starred(defaultModelId?.let { modelId -> ModelKey.of(ImageService.OPENROUTER.key, modelId) })
        }

        private fun nonBlankLines(text: String): List<String> =
            text.lines().map { line -> line.trim() }.filter { line -> line.isNotEmpty() }.distinct()
    }

    /** The star on [modelKey] when it is listed; otherwise the star addModel already placed. */
    private fun starred(modelKey: String?): ImageModels = if (modelKey == null) this else setDefault(modelKey)
}
