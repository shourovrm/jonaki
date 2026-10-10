package app.jonaki.run

import app.jonaki.core.modelcatalog.ImageModelInfo
import app.jonaki.core.modelcatalog.ModelKey
import app.jonaki.core.toolapi.ImageModelFacts

/** Turns OpenRouter's image model list into the facts generate_image checks a call against. */
object ImageModelFactsBuilder {
    private const val OPENROUTER = "openrouter"

    /** By "openrouter:modelId". Only OpenRouter has a list; Gemini's models have no entry. */
    fun byKey(models: List<ImageModelInfo>): Map<String, ImageModelFacts> =
        models.associate { model -> ModelKey.of(OPENROUTER, model.id) to factsOf(model) }

    fun factsOf(model: ImageModelInfo) = ImageModelFacts(
        modelKey = ModelKey.of(OPENROUTER, model.id),
        qualityValues = model.qualityValues,
        resolutionValues = model.resolutionValues,
        minReferences = model.referenceRange?.first ?: 0,
        maxReferences = model.referenceRange?.last,
    )
}
