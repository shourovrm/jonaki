package app.jonaki.files

import app.jonaki.feature.chat.ImageViewFit
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

    /** The full-screen view decodes at twice its first fitted size, so a pinch can zoom in without going soft. */
    private const val ZOOM_HEADROOM = 2.0

    /** 8 million pixels is 32 MB as ARGB_8888; one full-screen picture is held at a time. */
    const val MAX_FULL_VIEW_PIXELS = 8_000_000L

    /**
     * The size for a square tile that crops to the middle: the short side
     * equals [sideLength], so nothing is blurred by stretching; never larger
     * than [original].
     */
    fun cover(original: PixelSize, sideLength: Int): PixelSize {
        val factor = maxOf(sideLength.toDouble() / original.width, sideLength.toDouble() / original.height)
        return scaled(original, minOf(factor, 1.0))
    }

    /**
     * The size for the full-screen view on a screen of [screen] pixels: twice
     * the size the picture first shows at (see ImageViewFit), never larger
     * than [original], and never more than [MAX_FULL_VIEW_PIXELS].
     */
    fun forFullView(original: PixelSize, screen: PixelSize): PixelSize {
        val firstScale = ImageViewFit.baseScale(original.width, original.height, screen.width, screen.height)
        val wantedFactor = minOf(firstScale * ZOOM_HEADROOM, 1.0)
        val originalPixels = original.width.toDouble() * original.height
        val memoryFactor = Math.sqrt(MAX_FULL_VIEW_PIXELS / originalPixels)
        return scaled(original, minOf(wantedFactor, memoryFactor))
    }

    private fun scaled(original: PixelSize, factor: Double) = PixelSize(
        width = (original.width * factor).roundToInt().coerceAtLeast(1),
        height = (original.height * factor).roundToInt().coerceAtLeast(1),
    )

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
