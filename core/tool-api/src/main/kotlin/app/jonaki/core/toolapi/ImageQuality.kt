package app.jonaki.core.toolapi

import java.util.Locale

/**
 * The two quality words the chat model and the user know. Each image model has
 * its own scale ("low" to "max", or a size such as "2K"); [ImageQualityChoices]
 * translates, so that the chat model never sees those scales.
 */
enum class ImageQuality(val word: String) {
    STANDARD("standard"),
    HIGH("high"),
    ;

    companion object {
        /** The value of the tool's quality argument; null for a missing or unknown word, so the Settings default applies. */
        fun fromArgument(text: String?): ImageQuality? {
            val wanted = text?.trim()?.lowercase(Locale.ROOT)
            return entries.firstOrNull { quality -> quality.word == wanted }
        }

        /** The Settings value; Standard when the key was never saved. */
        fun fromStored(text: String?): ImageQuality = fromArgument(text) ?: STANDARD
    }
}

/**
 * What one image model declares it can do, from the service's public list.
 * The app passes it to generate_image, so the tool can translate a quality
 * word and check the number of reference pictures without reading any
 * catalog. A list the model does not declare is null.
 */
data class ImageModelFacts(
    /** For example "openrouter:openai/gpt-image-2.5-sunburst". */
    val modelKey: String,
    /** The values of the model's `quality` parameter, for example "low", "medium", "high". */
    val qualityValues: List<String>? = null,
    /** The values of the model's `resolution` parameter, for example "1K", "2K". */
    val resolutionValues: List<String>? = null,
    /** The smallest number of reference pictures the model needs; 0 when it needs none. */
    val minReferences: Int = 0,
    /** The most reference pictures the model takes; null when it declares no `input_references`. */
    val maxReferences: Int? = null,
)

/** The settings to send for a quality, and the note that tells the chat model when none could be sent. */
data class ImageQualityPlan(
    val quality: String? = null,
    val resolution: String? = null,
    val note: Note? = null,
) {
    enum class Note {
        /** The model declares neither a quality nor a resolution. */
        NO_SETTING,

        /** The model's list could not be read, so nothing was sent. */
        NOT_CHECKED,
    }

    companion object {
        val NOTHING = ImageQualityPlan()
    }
}

object ImageQualityChoices {
    private val QUALITY_ORDER = listOf("low", "medium", "high")

    private val RESOLUTION_ORDER = listOf("512", "768", "1K", "1.5K", "2K", "4K")

    private const val PREFERRED_RESOLUTION = "2K"

    private const val BASE_RESOLUTION = "1K"

    /**
     * Standard sends nothing, so the model uses its own default. High sends
     * "high" when the model lists it, else the highest listed value below
     * it; "xhigh" and "max" are never sent, since they cost much more. A
     * model with no quality but a resolution gets "2K", else the smallest
     * listed size above "1K". A model with both gets the quality only.
     */
    fun plan(facts: ImageModelFacts?, quality: ImageQuality): ImageQualityPlan {
        if (quality == ImageQuality.STANDARD) {
            return ImageQualityPlan.NOTHING
        }
        if (facts == null) {
            return ImageQualityPlan(note = ImageQualityPlan.Note.NOT_CHECKED)
        }
        if (facts.qualityValues != null) {
            return ImageQualityPlan(quality = highestQualityUpToHigh(facts.qualityValues))
        }
        if (facts.resolutionValues != null) {
            return ImageQualityPlan(resolution = largerResolution(facts.resolutionValues))
        }
        return ImageQualityPlan(note = ImageQualityPlan.Note.NO_SETTING)
    }

    private fun highestQualityUpToHigh(listed: List<String>): String? =
        QUALITY_ORDER.lastOrNull { value -> listed.any { it.equals(value, ignoreCase = true) } }

    private fun largerResolution(listed: List<String>): String? {
        val sizes = listed.map { it.trim() }
        sizes.firstOrNull { it.equals(PREFERRED_RESOLUTION, ignoreCase = true) }?.let { return it }
        val baseRank = RESOLUTION_ORDER.indexOf(BASE_RESOLUTION)
        return RESOLUTION_ORDER
            .drop(baseRank + 1)
            .firstOrNull { value -> sizes.any { it.equals(value, ignoreCase = true) } }
    }
}
