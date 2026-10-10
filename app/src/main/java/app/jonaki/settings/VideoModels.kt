package app.jonaki.settings

import app.jonaki.core.modelcatalog.ModelKey

/** What [VideoModels] is saved as in preferences: two plain texts. */
data class StoredVideoModels(
    /** "service:modelId", one per line, in the order added. */
    val modelKeysText: String,
    val defaultModelKey: String?,
)

/**
 * The video models the user added in Settings > Models > Video generation,
 * and the starred one that generate_video uses when the agent names none.
 * Only OpenRouter serves video now, but every model is kept as
 * "service:modelId" so that another service can be added later without
 * changing what is saved. Every change returns a new value.
 */
data class VideoModels(
    /** "service:modelId" in the order added, for example "openrouter:google/veo-3.1-lite". */
    val modelKeys: List<String> = emptyList(),
    /** The starred model; null when no model is added. */
    val defaultModelKey: String? = null,
) {
    /** The ids of one service's models, without the service prefix. */
    fun modelIdsOf(serviceKey: String): List<String> =
        modelKeys.filter { key -> ModelKey.serviceOf(key) == serviceKey }.map(ModelKey::modelOf)

    /** Adds a model; a blank or already-listed id changes nothing. The first model added is starred. */
    fun addModel(serviceKey: String, modelId: String): VideoModels {
        val trimmed = modelId.trim()
        val modelKey = ModelKey.of(serviceKey, trimmed)
        if (trimmed.isEmpty() || modelKey in modelKeys) {
            return this
        }
        return copy(modelKeys = modelKeys + modelKey).withValidDefault()
    }

    fun removeModel(modelKey: String): VideoModels = copy(modelKeys = modelKeys - modelKey).withValidDefault()

    /** Stars [modelKey]; a key that is not listed changes nothing. */
    fun setDefault(modelKey: String): VideoModels {
        if (modelKey !in modelKeys) {
            return this
        }
        return copy(defaultModelKey = modelKey)
    }

    /** Keeps the star on a listed model: the current one if still listed, else the first, else none. */
    private fun withValidDefault(): VideoModels {
        if (defaultModelKey in modelKeys) {
            return this
        }
        return copy(defaultModelKey = modelKeys.firstOrNull())
    }

    companion object {
        fun toStored(models: VideoModels): StoredVideoModels =
            StoredVideoModels(models.modelKeys.joinToString("\n"), models.defaultModelKey)

        /** Reads what [toStored] wrote; blank lines and repeats are dropped, and a star on an unlisted model is ignored. */
        fun fromStored(modelKeysText: String?, defaultModelKey: String?): VideoModels {
            val keys = modelKeysText.orEmpty().lines().map { line -> line.trim() }.filter { line -> line.contains(':') }.distinct()
            val models = VideoModels(modelKeys = keys).withValidDefault()
            return if (defaultModelKey == null) models else models.setDefault(defaultModelKey)
        }
    }
}
