package app.jonaki.ui

import app.jonaki.core.modelcatalog.VideoModelInfo
import app.jonaki.core.toolapi.VideoChoices
import app.jonaki.core.toolapi.VideoPrice
import app.jonaki.core.toolapi.VideoPricing

/** The words of a video model's price and length lines, from string resources so they follow the app's language. */
class VideoListWords(
    /** "$0.03 to $0.14 per second". */
    val priceRange: (lowest: String, highest: String) -> String,
    /** "$0.08 per second". */
    val priceSingle: (price: String) -> String,
    /** "per token". */
    val perToken: String,
    /** "1 to 15 s". */
    val lengthsRun: (first: Int, last: Int) -> String,
    /** "4, 6, 8 s", given "4, 6, 8". */
    val lengthsList: (seconds: String) -> String,
)

/** The price line and the length line of a video model, in the model picker and in Settings. */
object VideoListText {
    /**
     * The price per second at the lowest and the highest resolution the model
     * offers, from the price entries of the list itself. Null when the entries
     * give no price, so that the row shows no line instead of a guess.
     */
    fun price(model: VideoModelInfo, words: VideoListWords): String? {
        val withAudio = model.generatesAudio == true
        return when (val price = VideoPricing.priceOf(model.priceSkus, model.supportedResolutions, withAudio)) {
            is VideoPrice.PerSecond -> {
                val lowest = VideoPricing.dollars(price.lowestUsd)
                val highest = VideoPricing.dollars(price.highestUsd)
                if (lowest == highest) words.priceSingle(lowest) else words.priceRange(lowest, highest)
            }
            VideoPrice.PerToken -> words.perToken
            VideoPrice.Unknown -> null
        }
    }

    /** "1 to 15 s" for an unbroken run, else "4, 6, 8 s"; null when the list names no lengths. */
    fun lengths(model: VideoModelInfo, words: VideoListWords): String? {
        val seconds = model.supportedDurations?.takeIf { it.isNotEmpty() } ?: return null
        val run = VideoChoices.unbrokenRun(seconds)
        if (run != null) {
            return words.lengthsRun(run.first, run.last)
        }
        return words.lengthsList(seconds.sorted().joinToString(", "))
    }
}
