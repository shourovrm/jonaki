package app.jonaki.core.toolapi

import java.util.Locale

/**
 * What the app knows about one video model the user added, from the
 * service's public list. The app passes it to generate_video, so that the
 * tool can refuse a value the model does not support before any request and
 * can show a price estimate, without the tool module reading any catalog.
 * Every list is null when the service gave none; then nothing is checked.
 */
data class VideoModelFacts(
    /** For example "openrouter:google/veo-3.1-lite". */
    val modelKey: String,
    val supportedDurations: List<Int>? = null,
    val supportedResolutions: List<String>? = null,
    val supportedAspectRatios: List<String>? = null,
    /** True when the model makes sound by default, false when it cannot, null when the list did not say. */
    val generatesAudio: Boolean? = null,
    /** The service's price entries: key to a number written as text, whose meaning the key gives. */
    val priceSkus: Map<String, String>? = null,
)

/** The length, resolution and shape generate_video will ask for, after the defaults are filled in. */
data class VideoChoice(
    val durationSeconds: Int?,
    val resolution: String?,
    val aspectRatio: String?,
    val withAudio: Boolean?,
)

/** The result of [VideoChoices.resolve]: a choice to send, or a refusal that lists the supported values. */
sealed interface VideoChoiceResult {
    data class Chosen(val choice: VideoChoice) : VideoChoiceResult

    data class Refused(val message: String) : VideoChoiceResult
}

/**
 * Fills in what the agent left out, choosing the cheapest sensible video,
 * and refuses what the model does not support.
 */
object VideoChoices {
    private const val MINIMUM_USEFUL_SECONDS = 4
    private const val DEFAULT_RESOLUTION = "720p"

    /**
     * With [facts] null (the list could not be loaded) the values are passed
     * on as given and nothing is checked.
     */
    fun resolve(
        facts: VideoModelFacts?,
        durationSeconds: Int?,
        resolution: String?,
        aspectRatio: String?,
        withAudio: Boolean?,
    ): VideoChoiceResult {
        if (facts == null) {
            return VideoChoiceResult.Chosen(VideoChoice(durationSeconds, resolution, aspectRatio, withAudio))
        }
        val duration = chooseDuration(facts, durationSeconds)
        val chosenResolution = chooseResolution(facts, resolution)
        val chosenAspectRatio = chooseAspectRatio(facts, aspectRatio)
        val problem = listOfNotNull(duration.problem, chosenResolution.problem, chosenAspectRatio.problem, audioProblem(facts, withAudio))
            .firstOrNull()
        if (problem != null) {
            return VideoChoiceResult.Refused(problem)
        }
        return VideoChoiceResult.Chosen(VideoChoice(duration.value, chosenResolution.value, chosenAspectRatio.value, withAudio))
    }

    /** A value to send (null sends none), or the reason it is refused. */
    private class Picked<T>(val value: T?, val problem: String? = null)

    private fun chooseDuration(facts: VideoModelFacts, requested: Int?): Picked<Int> {
        val supported = facts.supportedDurations.orEmpty().sorted()
        if (requested != null && supported.isNotEmpty() && requested !in supported) {
            return Picked(null, "${facts.modelKey} does not make ${requested}-second videos; supported lengths: ${describeSeconds(supported)}")
        }
        if (requested != null || supported.isEmpty()) {
            return Picked(requested)
        }
        return Picked(supported.firstOrNull { it >= MINIMUM_USEFUL_SECONDS } ?: supported.first())
    }

    private fun chooseResolution(facts: VideoModelFacts, requested: String?): Picked<String> {
        val supported = facts.supportedResolutions.orEmpty()
        if (requested != null && supported.isNotEmpty()) {
            val match = supported.firstOrNull { it.equals(requested, ignoreCase = true) }
                ?: return Picked(null, "${facts.modelKey} does not make $requested videos; supported resolutions: ${supported.joinToString(", ")}")
            return Picked(match)
        }
        if (requested != null || supported.isEmpty()) {
            return Picked(requested)
        }
        val standard = supported.firstOrNull { it.equals(DEFAULT_RESOLUTION, ignoreCase = true) }
        return Picked(standard ?: supported.minBy(::resolutionSize))
    }

    private fun chooseAspectRatio(facts: VideoModelFacts, requested: String?): Picked<String> {
        val supported = facts.supportedAspectRatios.orEmpty()
        if (requested != null && supported.isNotEmpty() && supported.none { it == requested }) {
            return Picked(null, "${facts.modelKey} does not make $requested videos; supported shapes: ${supported.joinToString(", ")}")
        }
        return Picked(requested)
    }

    private fun audioProblem(facts: VideoModelFacts, withAudio: Boolean?): String? {
        if (withAudio == true && facts.generatesAudio == false) {
            return "${facts.modelKey} cannot make sound; leave with_audio out or set it to false"
        }
        return null
    }

    /** "480p" is 480 and "4K" is 4000, so that the lowest resolution is the smallest number. */
    private fun resolutionSize(resolution: String): Int {
        val digits = resolution.takeWhile { it.isDigit() }.toIntOrNull() ?: return Int.MAX_VALUE
        return if (resolution.endsWith("k", ignoreCase = true)) digits * 1000 else digits
    }

    /** The first and last second when [seconds] is an unbroken run of more than three numbers, such as 1 to 15; else null. */
    fun unbrokenRun(seconds: List<Int>): IntRange? {
        val sorted = seconds.sorted()
        val isUnbrokenRun = sorted.size > 3 && sorted.zipWithNext().all { (first, second) -> second == first + 1 }
        return if (isUnbrokenRun) sorted.first()..sorted.last() else null
    }

    /** "4, 6, 8 s", or "1 to 15 s" for an unbroken run of more than three numbers. */
    fun describeSeconds(seconds: List<Int>): String {
        val run = unbrokenRun(seconds)
        if (run != null) {
            return "${run.first} to ${run.last} s"
        }
        return seconds.sorted().joinToString(", ") + " s"
    }
}

/** What a video of a given length is expected to cost, from the model's price entries. */
sealed interface VideoEstimate {
    data class Dollars(val totalUsd: Double) : VideoEstimate

    /** The model is billed per token, so no figure in dollars can be given before the job runs. */
    data object PerToken : VideoEstimate

    data object Unknown : VideoEstimate
}

/** The price of a second of video: one figure or the lowest and highest across the resolutions. */
sealed interface VideoPrice {
    data class PerSecond(val lowestUsd: Double, val highestUsd: Double) : VideoPrice

    data object PerToken : VideoPrice

    data object Unknown : VideoPrice
}

/**
 * Reads the service's price entries. The key names differ per model; three
 * families carry a price per second of video, each with optional parts:
 *
 * - `cents_per_video_output_second` and `cents_per_second_output`, in cents;
 * - `duration_seconds` and `text_to_video_duration_seconds`, in dollars;
 * - then `_with_audio` or `_without_audio`, then `_<resolution>` such as `_720p` or `_4k`.
 *
 * The most specific entry that exists wins, so "with audio at 720p" falls
 * back to "with audio", then to "720p", then to the plain entry.
 */
object VideoPricing {
    private class Family(val keyStart: String, val dollarsPerUnit: Double)

    private const val CENTS_TO_DOLLARS = 0.01

    private val families = listOf(
        Family("cents_per_video_output_second", CENTS_TO_DOLLARS),
        Family("cents_per_second_output", CENTS_TO_DOLLARS),
        Family("duration_seconds", 1.0),
        Family("text_to_video_duration_seconds", 1.0),
    )

    /** Dollars per second for [resolution] (null for the model's own) and the sound setting; null when no entry gives it. */
    fun perSecondUsd(skus: Map<String, String>?, resolution: String?, withAudio: Boolean): Double? {
        if (skus.isNullOrEmpty()) {
            return null
        }
        val audioPart = if (withAudio) "_with_audio" else "_without_audio"
        val resolutionPart = resolution?.lowercase(Locale.ROOT)?.let { "_$it" }
        val endings = listOfNotNull(resolutionPart?.let { audioPart + it }, audioPart, resolutionPart, "")
        for (ending in endings) {
            for (family in families) {
                val dollars = skus[family.keyStart + ending]?.toDoubleOrNull() ?: continue
                if (dollars >= 0) {
                    return dollars * family.dollarsPerUnit
                }
            }
        }
        return null
    }

    fun estimate(skus: Map<String, String>?, resolution: String?, withAudio: Boolean, seconds: Int): VideoEstimate {
        val perSecond = perSecondUsd(skus, resolution, withAudio)
        if (perSecond != null) {
            return VideoEstimate.Dollars(perSecond * seconds)
        }
        return if (isPerToken(skus)) VideoEstimate.PerToken else VideoEstimate.Unknown
    }

    /** The price per second across [resolutions] (the model's own price when it lists none). */
    fun priceOf(skus: Map<String, String>?, resolutions: List<String>?, withAudio: Boolean): VideoPrice {
        val choices = if (resolutions.isNullOrEmpty()) listOf<String?>(null) else resolutions
        val prices = choices.mapNotNull { resolution -> perSecondUsd(skus, resolution, withAudio) }
        if (prices.isNotEmpty()) {
            return VideoPrice.PerSecond(prices.min(), prices.max())
        }
        return if (isPerToken(skus)) VideoPrice.PerToken else VideoPrice.Unknown
    }

    private fun isPerToken(skus: Map<String, String>?): Boolean = skus.orEmpty().keys.any { key -> "token" in key }

    /** "$0.03", "$0.014", "$2.40": two decimals from 10 cents up, three below, no trailing zero beyond two decimals. */
    fun dollars(amount: Double): String {
        val pattern = if (amount >= 0.1) "%.2f" else "%.3f"
        val text = String.format(Locale.ENGLISH, pattern, amount)
        val trimmed = if (text.endsWith("0") && text.substringAfter('.').length == 3) text.dropLast(1) else text
        return "$$trimmed"
    }
}
