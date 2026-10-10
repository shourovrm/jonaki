package app.jonaki.ui

import app.jonaki.core.toolapi.VideoChoice
import app.jonaki.core.toolapi.VideoChoiceResult
import app.jonaki.core.toolapi.VideoChoices
import app.jonaki.core.toolapi.VideoEstimate
import app.jonaki.core.toolapi.VideoModelFacts
import app.jonaki.core.toolapi.VideoPricing
import app.jonaki.core.toolapi.booleanArgument
import app.jonaki.core.toolapi.intArgument
import app.jonaki.core.toolapi.stringArgument
import kotlinx.serialization.json.JsonObject

/** The words of a generate_video line, from string resources so they follow the app's language. */
class VideoStepWords(
    /** "Collect video job %s, no new cost". */
    val collectsEarlierJob: (jobId: String) -> String,
    /** "about $0.20". */
    val aboutCost: (dollars: String) -> String,
    /** "price per token, not estimated". */
    val priceIsPerToken: String,
    /** "4 s". */
    val seconds: (Int) -> String,
)

/**
 * The line the approval card and the step track show for a generate_video
 * call: the model, the length, the resolution, a price estimate and the
 * prompt, so that the user sees what will be paid before allowing it. The
 * length and resolution are the ones the tool will really ask for (the
 * defaults filled in), and the estimate comes from the model's price entries.
 */
class VideoStepText(
    private val factsByModelKey: Map<String, VideoModelFacts>,
    private val defaultModelKey: String?,
    private val words: VideoStepWords,
) {
    fun target(arguments: JsonObject): String? {
        val jobId = arguments.stringArgument("job_id")?.trim()?.ifEmpty { null }
        if (jobId != null) {
            return words.collectsEarlierJob(jobId)
        }
        val model = arguments.stringArgument("model")?.trim()?.ifEmpty { null } ?: defaultModelKey
        val facts = model?.let { key -> factsByModelKey[key] }
        val choice = chosenValues(facts, arguments)
        val prompt = arguments.stringArgument("prompt")?.trim()?.take(MAX_PROMPT_CHARACTERS_SHOWN)
        val parts = listOfNotNull(
            model,
            choice.durationSeconds?.let(words.seconds),
            choice.resolution,
            estimateText(facts, choice),
            prompt,
        )
        return parts.joinToString(" · ").ifEmpty { null }
    }

    /** What the call asks for. A value the model refuses is shown as asked; the tool refuses it before any request. */
    private fun chosenValues(facts: VideoModelFacts?, arguments: JsonObject): VideoChoice {
        val asked = VideoChoice(
            durationSeconds = arguments.intArgument("duration_seconds"),
            resolution = arguments.stringArgument("resolution")?.trim()?.ifEmpty { null },
            aspectRatio = arguments.stringArgument("aspect_ratio")?.trim()?.ifEmpty { null },
            withAudio = arguments.booleanArgument("with_audio"),
        )
        val result = VideoChoices.resolve(facts, asked.durationSeconds, asked.resolution, asked.aspectRatio, asked.withAudio)
        return (result as? VideoChoiceResult.Chosen)?.choice ?: asked
    }

    private fun estimateText(facts: VideoModelFacts?, choice: VideoChoice): String? {
        val skus = facts?.priceSkus ?: return null
        val withAudio = choice.withAudio ?: (facts.generatesAudio == true)
        // A model that lists no length is estimated for the shortest clip the tool would ask for: unknown, so no figure.
        val seconds = choice.durationSeconds ?: return perTokenOrNull(skus, choice, withAudio)
        return when (val estimate = VideoPricing.estimate(skus, choice.resolution, withAudio, seconds)) {
            is VideoEstimate.Dollars -> words.aboutCost(VideoPricing.dollars(estimate.totalUsd))
            VideoEstimate.PerToken -> words.priceIsPerToken
            VideoEstimate.Unknown -> null
        }
    }

    private fun perTokenOrNull(skus: Map<String, String>, choice: VideoChoice, withAudio: Boolean): String? =
        if (VideoPricing.estimate(skus, choice.resolution, withAudio, 1) == VideoEstimate.PerToken) words.priceIsPerToken else null

    private companion object {
        const val MAX_PROMPT_CHARACTERS_SHOWN = 300
    }
}
