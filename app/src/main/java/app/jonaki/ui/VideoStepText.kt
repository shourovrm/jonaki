package app.jonaki.ui

import app.jonaki.core.modelcatalog.ModelKey
import app.jonaki.core.toolapi.VideoChoice
import app.jonaki.core.toolapi.VideoChoiceResult
import app.jonaki.core.toolapi.VideoChoices
import app.jonaki.core.toolapi.VideoEstimate
import app.jonaki.core.toolapi.VideoModelFacts
import app.jonaki.core.toolapi.VideoPricing
import app.jonaki.core.toolapi.booleanArgument
import app.jonaki.core.toolapi.intArgument
import app.jonaki.core.toolapi.stringArgument
import app.jonaki.feature.chat.StepPromptUi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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

/** The length, resolution and cost estimate of a video call that sets nothing but the prompt; null where unknown. */
class VideoCallDefaults(
    val lengthText: String?,
    val resolution: String?,
    val estimateText: String?,
    /** The length behind [lengthText], in seconds. */
    val durationSeconds: Int? = null,
)

/** The lengths and resolutions a video model supports, for the settings sheet; empty where its list names none. */
class VideoOptions(
    val lengthsSeconds: List<Int>,
    val resolutions: List<String>,
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
    /** The service's name of each model by key, such as "Grok Imagine Video 1.5 Lite"; empty until the list has loaded. */
    private val displayNamesByModelKey: Map<String, String> = emptyMap(),
) {
    /** The name to show for a model: the service's name when the list has loaded, else the model id. */
    fun displayNameOf(modelKey: String): String = displayNamesByModelKey[modelKey] ?: ModelKey.modelOf(modelKey)

    /** The short line of the step track: the prompt is cut, and the step's sheet shows all of it. */
    fun target(arguments: JsonObject): String? = line(arguments, PromptStepText::cut)

    /** The approval card's text: the same line with the whole prompt, so the user reads it before allowing. */
    fun fullTarget(arguments: JsonObject): String? = line(arguments) { prompt -> prompt.trim() }

    /** What the step's sheet shows; null for a call that only collects an earlier job. */
    fun detail(arguments: JsonObject): StepPromptUi? {
        if (arguments.stringArgument("job_id")?.trim()?.isNotEmpty() == true) {
            return null
        }
        val prompt = arguments.stringArgument("prompt")?.trim()?.ifEmpty { null } ?: return null
        val model = arguments.stringArgument("model")?.trim()?.ifEmpty { null } ?: defaultModelKey
        return StepPromptUi(
            prompt = prompt,
            model = model,
            aspectRatio = arguments.stringArgument("aspect_ratio")?.trim()?.ifEmpty { null },
        )
    }

    private fun line(arguments: JsonObject, promptOf: (String) -> String): String? {
        val jobId = arguments.stringArgument("job_id")?.trim()?.ifEmpty { null }
        if (jobId != null) {
            return words.collectsEarlierJob(jobId)
        }
        val model = arguments.stringArgument("model")?.trim()?.ifEmpty { null } ?: defaultModelKey
        val facts = model?.let { key -> factsByModelKey[key] }
        val choice = chosenValues(facts, arguments)
        val prompt = arguments.stringArgument("prompt")?.trim()?.ifEmpty { null }?.let(promptOf)
        val parts = listOfNotNull(
            model,
            choice.durationSeconds?.let(words.seconds),
            choice.resolution,
            estimateText(facts, choice),
            prompt,
        )
        return parts.joinToString(" · ").ifEmpty { null }
    }

    /**
     * What a call with only a prompt asks for from [modelKey], and its estimate,
     * for the line above the message box in video mode (no approval card is shown
     * there). The values are the ones [target] shows for such a call.
     */
    fun defaultsFor(modelKey: String?): VideoCallDefaults = callFor(modelKey, durationSeconds = null, resolution = null)

    /**
     * Like [defaultsFor], for a call that also sets a length or a resolution
     * (the settings sheet of video mode); a null one is filled in as the tool would.
     */
    fun callFor(modelKey: String?, durationSeconds: Int?, resolution: String?): VideoCallDefaults {
        val facts = modelKey?.let { key -> factsByModelKey[key] }
        val arguments = buildJsonObject {
            durationSeconds?.let { seconds -> put("duration_seconds", seconds) }
            resolution?.let { asked -> put("resolution", asked) }
        }
        val choice = chosenValues(facts, arguments)
        return VideoCallDefaults(
            lengthText = choice.durationSeconds?.let(words.seconds),
            resolution = choice.resolution,
            estimateText = estimateText(facts, choice),
            durationSeconds = choice.durationSeconds,
        )
    }

    /** What the settings sheet can offer for [modelKey]; both lists are empty until the model list has loaded. */
    fun optionsFor(modelKey: String?): VideoOptions {
        val facts = modelKey?.let { key -> factsByModelKey[key] }
        return VideoOptions(
            lengthsSeconds = facts?.supportedDurations.orEmpty().sorted(),
            resolutions = facts?.supportedResolutions.orEmpty(),
        )
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
}
