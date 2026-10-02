package app.jonaki.files

import kotlin.math.roundToInt

/** Width and height in pixels. */
data class PixelSize(val width: Int, val height: Int)

/**
 * Sizes for images sent to a model (D-049). A long side of 1,568 pixels
 * keeps text in photos legible while costing about 1,600 tokens or fewer at
 * the common providers; larger images are shrunk, smaller ones kept.
 */
object ImageScale {
    const val MAX_LONG_SIDE = 1_568

    /** The size that fits [MAX_LONG_SIDE], keeping the shape; never larger than [original]. */
    fun fitted(original: PixelSize): PixelSize {
        val longSide = maxOf(original.width, original.height)
        if (longSide <= MAX_LONG_SIDE) {
            return original
        }
        val factor = MAX_LONG_SIDE.toDouble() / longSide
        return PixelSize(
            width = (original.width * factor).roundToInt().coerceAtLeast(1),
            height = (original.height * factor).roundToInt().coerceAtLeast(1),
        )
    }

    /**
     * The largest power of two to decode a big photo at (BitmapFactory's
     * inSampleSize) that still leaves it at least [target] large, so a
     * 48-megapixel photo is not held whole in memory before it is shrunk.
     */
    fun sampleSize(original: PixelSize, target: PixelSize): Int {
        var sample = 1
        while (original.width / (sample * 2) >= target.width && original.height / (sample * 2) >= target.height) {
            sample *= 2
        }
        return sample
    }
}
