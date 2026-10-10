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
    /** True when the service has vector (SVG) models, so it can be added under Vector image generation. */
    val servesVectorModels: Boolean = false,
) {
    OPENROUTER("openrouter", "OpenRouter", SecretName.OPENROUTER, servesVectorModels = true),

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

/** What [ImageModels] is saved as in preferences: five plain texts. */
data class StoredImageModels(
    /** Service keys, one per line, in the order added. */
    val servicesText: String,
    /** "service:modelId", one per line. */
    val modelKeysText: String,
    val defaultModelKey: String?,
    /** The keys of the vector models, one per line; empty when none. */
    val vectorModelKeysText: String = "",
    /** The keys of the services added under Vector image generation, one per line. */
    val vectorServicesText: String = "",
)

/**
 * The image services and models the user added (like [ChatModels]): services
 * in the order added, any number of models per service, and one starred model
 * across all services that generate_image uses when the agent names none.
 * Raster models belong to generate_image and vector (SVG) models to
 * generate_vector_image; [vectorModelKeys] tells them apart. A service is
 * added to each kind on its own ([addedServices] for raster models,
 * [vectorServices] for vector models), so removing it from one section of
 * Settings leaves the other. Every change returns a new value.
 */
data class ImageModels(
    val addedServices: List<ImageService> = emptyList(),
    /** Model ids per service, without the service prefix. */
    val modelsByService: Map<ImageService, List<String>> = emptyMap(),
    /** "service:modelId" of the starred model; null when no model is added. */
    val defaultModelKey: String? = null,
    /**
     * The models known to be vector models, as "service:modelId". The picker
     * sets it when a model is added, from the service's own model list, so
     * that a run never depends on a download. Absent in settings saved before
     * vector models existed, which load as "none".
     */
    val vectorModelKeys: Set<String> = emptySet(),
    /** The services added under Vector image generation, in the order added. */
    val vectorServices: List<ImageService> = emptyList(),
) {
    /** The services of both sections: the raster ones in card order, then the ones added for vector models only. */
    private val servicesOfBothKinds: List<ImageService>
        get() = (addedServices + vectorServices).distinct()

    /** Every model as "service:modelId", in card order. */
    val allModelKeys: List<String>
        get() = servicesOfBothKinds.flatMap { service ->
            modelsByService[service].orEmpty().map { modelId -> ModelKey.of(service.key, modelId) }
        }

    /** Whether [modelKey] makes SVG files: the stored fact, else the name fallback in [looksLikeVectorModel]. */
    fun isVector(modelKey: String): Boolean = modelKey in vectorModelKeys || looksLikeVectorModel(modelKey)

    /** The models of services with a saved key, both kinds. */
    fun usableModelKeys(hasKey: (ImageService) -> Boolean): List<String> =
        servicesOfBothKinds.filter(hasKey).flatMap { service ->
            modelsByService[service].orEmpty().map { modelId -> ModelKey.of(service.key, modelId) }
        }

    /** The models generate_image may offer; the tool is offered when this is not empty. */
    fun usableRasterModelKeys(hasKey: (ImageService) -> Boolean): List<String> =
        usableModelKeys(hasKey).filter { modelKey -> !isVector(modelKey) }

    /** The models generate_vector_image may offer; the first one is its default, since the star belongs to generate_image. */
    fun usableVectorModelKeys(hasKey: (ImageService) -> Boolean): List<String> =
        usableModelKeys(hasKey).filter { modelKey -> isVector(modelKey) }

    fun addService(service: ImageService): ImageModels {
        if (service in addedServices) {
            return this
        }
        return copy(addedServices = addedServices + service)
    }

    /** Drops the service and its raster models; its vector models and its key stay. */
    fun removeService(service: ImageService): ImageModels =
        copy(addedServices = addedServices - service).keepingModelsOf(service) { modelKey -> isVector(modelKey) }

    fun addVectorService(service: ImageService): ImageModels {
        if (service in vectorServices) {
            return this
        }
        return copy(vectorServices = vectorServices + service)
    }

    /** Drops the service from Vector image generation with its vector models; its raster models and its key stay. */
    fun removeVectorService(service: ImageService): ImageModels =
        copy(vectorServices = vectorServices - service).keepingModelsOf(service) { modelKey -> !isVector(modelKey) }

    private fun keepingModelsOf(service: ImageService, keep: (modelKey: String) -> Boolean): ImageModels {
        val keptIds = modelsByService[service].orEmpty().filter { modelId -> keep(ModelKey.of(service.key, modelId)) }
        return copy(modelsByService = modelsByService + (service to keptIds)).withValidDefault()
    }

    /**
     * Adds a model; a blank or already-listed id changes nothing. The first
     * raster model added is starred. [isVector] is what the service's list said
     * about the model; it is stored so that no later run has to ask again.
     * The service is added to the section of the model's kind.
     */
    fun addModel(service: ImageService, modelId: String, isVector: Boolean = false): ImageModels {
        val trimmed = modelId.trim()
        val existing = modelsByService[service].orEmpty()
        if (trimmed.isEmpty()) {
            return addService(service)
        }
        val modelKey = ModelKey.of(service.key, trimmed)
        val vectorKeys = if (isVector) vectorModelKeys + modelKey else vectorModelKeys
        val withService = if (isVector || looksLikeVectorModel(modelKey)) addVectorService(service) else addService(service)
        if (trimmed in existing) {
            return withService.copy(vectorModelKeys = vectorKeys)
        }
        val added = withService.copy(
            modelsByService = modelsByService + (service to existing + trimmed),
            vectorModelKeys = vectorKeys,
        )
        return added.withValidDefault()
    }

    fun removeModel(modelKey: String): ImageModels {
        val service = ImageService.byKey(ModelKey.serviceOf(modelKey)) ?: return this
        val remainingIds = modelsByService[service].orEmpty() - ModelKey.modelOf(modelKey)
        return copy(modelsByService = modelsByService + (service to remainingIds)).withValidDefault()
    }

    /** Stars [modelKey]; a key that is not listed, or a vector model, changes nothing. */
    fun setDefault(modelKey: String): ImageModels {
        if (modelKey !in allModelKeys || isVector(modelKey)) {
            return this
        }
        return copy(defaultModelKey = modelKey)
    }

    /**
     * Makes [modelKey] the vector model used when a call names none. That
     * model is the first vector model in card order, so the model moves to
     * the front of its service's list; a key that is not listed changes nothing.
     */
    fun setVectorDefault(modelKey: String): ImageModels {
        val service = ImageService.byKey(ModelKey.serviceOf(modelKey)) ?: return this
        val modelId = ModelKey.modelOf(modelKey)
        val listed = modelsByService[service].orEmpty()
        if (modelId !in listed) {
            return this
        }
        return copy(modelsByService = modelsByService + (service to listOf(modelId) + (listed - modelId)))
    }

    /**
     * Keeps the star on a listed raster model (the current one if still
     * listed, else the first, else none), since the star is generate_image's
     * default, and drops vector flags of models that are gone.
     */
    private fun withValidDefault(): ImageModels {
        val keys = allModelKeys
        val listedVectorKeys = vectorModelKeys.filter { modelKey -> modelKey in keys }.toSet()
        val rasterKeys = keys.filter { modelKey -> modelKey !in listedVectorKeys && !looksLikeVectorModel(modelKey) }
        val defaultKey = if (defaultModelKey in rasterKeys) defaultModelKey else rasterKeys.firstOrNull()
        return copy(defaultModelKey = defaultKey, vectorModelKeys = listedVectorKeys)
    }

    companion object {
        fun toStored(models: ImageModels): StoredImageModels = StoredImageModels(
            servicesText = models.addedServices.joinToString("\n") { service -> service.key },
            // Model ids hold slashes, colons and dots, so they are stored one key per line, like chat models.
            modelKeysText = models.allModelKeys.joinToString("\n"),
            defaultModelKey = models.defaultModelKey,
            vectorModelKeysText = models.vectorModelKeys.filter { modelKey -> modelKey in models.allModelKeys }.joinToString("\n"),
            vectorServicesText = models.vectorServices.joinToString("\n") { service -> service.key },
        )

        /**
         * Reads what [toStored] wrote. A null [servicesText] and [modelKeysText]
         * mean the new format was never saved; then the first version's values
         * count: [legacyModelsText] was one OpenRouter model id per line
         * (`image_models`) and [legacyDefaultModel] one of those ids
         * (`image_default_model`). They become models of the OpenRouter
         * service with the same star. Once the new format exists it wins, even
         * when empty, so models the user removed do not come back.
         * [vectorServicesText] is null in settings saved before vector models
         * had a section; then every service that has a vector model is one.
         */
        fun fromStored(
            servicesText: String?,
            modelKeysText: String?,
            defaultModelKey: String?,
            legacyModelsText: String?,
            legacyDefaultModel: String?,
            /** Null in settings saved before vector models existed. */
            vectorModelKeysText: String? = null,
            vectorServicesText: String? = null,
        ): ImageModels {
            if (servicesText == null && modelKeysText == null) {
                return fromLegacy(legacyModelsText.orEmpty(), legacyDefaultModel)
            }
            var models = ImageModels()
            for (line in nonBlankLines(servicesText.orEmpty())) {
                ImageService.byKey(line)?.let { service -> models = models.addService(service) }
            }
            for (line in nonBlankLines(vectorServicesText.orEmpty())) {
                ImageService.byKey(line)?.let { service -> models = models.addVectorService(service) }
            }
            val storedVectorKeys = nonBlankLines(vectorModelKeysText.orEmpty()).toSet()
            for (key in nonBlankLines(modelKeysText.orEmpty())) {
                val service = ImageService.byKey(ModelKey.serviceOf(key)) ?: continue
                models = models.addModel(service, ModelKey.modelOf(key), isVector = key in storedVectorKeys)
            }
            return models.starred(defaultModelKey)
        }

        /**
         * The fallback for a model whose flag was never stored (added before
         * vector models existed, or through a typed id): an OpenRouter id whose
         * last path segment ends with "vector", such as "recraft/recraft-v4-vector".
         * OpenRouter's vector models are named so (checked 2026-10-10).
         */
        fun looksLikeVectorModel(modelKey: String): Boolean {
            if (ModelKey.serviceOf(modelKey) != ImageService.OPENROUTER.key) {
                return false
            }
            return ModelKey.modelOf(modelKey).substringAfterLast('/').endsWith("vector", ignoreCase = true)
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
