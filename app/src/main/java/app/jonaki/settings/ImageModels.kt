package app.jonaki.settings

/**
 * The image models the user added in Settings > Models > Image generation:
 * OpenRouter ids in the order added, and the starred one that generate_image
 * uses when the agent names none. Every change returns a new value.
 */
data class ImageModels(
    /** OpenRouter ids such as "black-forest-labs/flux.2-klein-4b". */
    val modelIds: List<String> = emptyList(),
    /** One of [modelIds]; null only while the list is empty. */
    val defaultModelId: String? = null,
) {
    /** Adds a model; a blank or already-listed id changes nothing. The first model added is starred. */
    fun add(modelId: String): ImageModels {
        val trimmed = modelId.trim()
        if (trimmed.isEmpty() || trimmed in modelIds) {
            return this
        }
        return copy(modelIds = modelIds + trimmed).withValidDefault()
    }

    fun remove(modelId: String): ImageModels = copy(modelIds = modelIds - modelId).withValidDefault()

    /** Stars [modelId]; an id that is not listed changes nothing. */
    fun setDefault(modelId: String): ImageModels {
        if (modelId !in modelIds) {
            return this
        }
        return copy(defaultModelId = modelId)
    }

    /** Keeps the star on a listed model: the current one if still listed, else the first, else none. */
    private fun withValidDefault(): ImageModels {
        if (defaultModelId in modelIds) {
            return this
        }
        return copy(defaultModelId = modelIds.firstOrNull())
    }

    companion object {
        /** Model ids hold slashes, colons and dots, so they are stored one per line, like chat models. */
        fun toText(modelIds: List<String>): String = modelIds.joinToString("\n")

        fun fromText(modelsText: String, defaultModelId: String?): ImageModels {
            val modelIds = modelsText.lines().map { line -> line.trim() }.filter { line -> line.isNotEmpty() }.distinct()
            return ImageModels(modelIds, defaultModelId).withValidDefault()
        }
    }
}
