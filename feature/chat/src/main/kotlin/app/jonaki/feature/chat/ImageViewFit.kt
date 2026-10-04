package app.jonaki.feature.chat

import kotlin.math.max
import kotlin.math.min

/**
 * How the full-screen image view first lays a picture out. The app uses the
 * same rule to decide how large to decode it, so the two cannot disagree.
 */
object ImageViewFit {
    /** A picture more than this many times taller than wide counts as a long strip. */
    private const val LONG_STRIP_RATIO = 3

    /**
     * Pixels on screen per pixel of the picture when it first shows. A normal
     * picture fits whole. A long strip, such as a screenshot of a web page,
     * would be unreadably thin that way, so it fits the screen's width and the
     * user pans down it. A wide picture always fits whole; zooming reveals detail.
     */
    fun baseScale(imageWidth: Int, imageHeight: Int, containerWidth: Int, containerHeight: Int): Double {
        val acrossScale = containerWidth.toDouble() / imageWidth
        val downScale = containerHeight.toDouble() / imageHeight
        val isLongStrip = imageHeight > LONG_STRIP_RATIO * imageWidth
        if (isLongStrip) {
            return acrossScale
        }
        return min(acrossScale, downScale)
    }

    /** How far the picture may move from the centre before its edge shows a gap; zero when it fits. */
    fun maxPan(displayedPx: Float, containerPx: Int): Float = max(0f, (displayedPx - containerPx) / 2f)
}

/** The picture's size on screen at zoom 1, in pixels. */
data class DisplayedSize(val width: Float, val height: Float)

/**
 * The zoom and the shift of the picture from the centre of the screen.
 * A positive [panY] moves the picture down, which shows its top.
 */
data class ViewTransform(val zoom: Float, val panX: Float, val panY: Float) {
    /**
     * The transform after a pinch or drag. [focusX] and [focusY] are the
     * fingers' centre measured from the screen's centre. The picture is
     * scaled about its own centre, so the point under the fingers is kept
     * still by moving the picture by the amount the zoom would have moved it.
     */
    fun changed(
        focusX: Float,
        focusY: Float,
        zoomChange: Float,
        panChangeX: Float,
        panChangeY: Float,
        displayed: DisplayedSize,
        containerWidth: Int,
        containerHeight: Int,
    ): ViewTransform {
        val newZoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
        val ratio = newZoom / zoom
        val limitX = ImageViewFit.maxPan(displayed.width * newZoom, containerWidth)
        val limitY = ImageViewFit.maxPan(displayed.height * newZoom, containerHeight)
        val newPanX = (panX - focusX) * ratio + focusX + panChangeX
        val newPanY = (panY - focusY) * ratio + focusY + panChangeY
        return ViewTransform(newZoom, newPanX.coerceIn(-limitX, limitX), newPanY.coerceIn(-limitY, limitY))
    }

    companion object {
        const val MAX_ZOOM = 5f

        /** Whole picture centred; a long strip starts at its top or left end. */
        fun initial(displayed: DisplayedSize, containerWidth: Int, containerHeight: Int): ViewTransform = ViewTransform(
            zoom = 1f,
            panX = ImageViewFit.maxPan(displayed.width, containerWidth),
            panY = ImageViewFit.maxPan(displayed.height, containerHeight),
        )
    }
}
