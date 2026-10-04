package app.jonaki.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageViewFitTest {
    @Test
    fun aPhotoFitsWholeInTheScreen() {
        // 4000 x 3000 on 1080 x 2000: the width is the tight side, 1080 / 4000.
        assertEquals(0.27, ImageViewFit.baseScale(4000, 3000, 1080, 2000), 0.0001)
    }

    @Test
    fun aLongImageFitsTheScreenWidthAndScrollsDown() {
        // 1000 x 8000 is 8 times taller than wide, more than the 3 times of a strip.
        assertEquals(1.08, ImageViewFit.baseScale(1000, 8000, 1080, 2000), 0.0001)
    }

    @Test
    fun aWidePanoramaFitsWhole() {
        assertEquals(0.135, ImageViewFit.baseScale(8000, 1000, 1080, 2000), 0.0001)
    }

    @Test
    fun aSlightlyTallImageStillFitsWhole() {
        // 1000 x 1800 is less than 3 times taller than wide, so the whole picture shows.
        assertEquals(1.08, ImageViewFit.baseScale(1000, 1800, 1080, 2000), 0.0001)
    }

    @Test
    fun panStopsWhenTheImageEdgeReachesTheScreenEdge() {
        assertEquals(0f, ImageViewFit.maxPan(displayedPx = 900f, containerPx = 1080), 0f)
        assertEquals(500f, ImageViewFit.maxPan(displayedPx = 2080f, containerPx = 1080), 0f)
    }

    @Test
    fun zoomingKeepsThePointUnderTheFingerStill() {
        val start = ViewTransform(zoom = 1f, panX = 0f, panY = 0f)

        val zoomed = start.changed(
            focusX = 100f,
            focusY = 0f,
            zoomChange = 2f,
            panChangeX = 0f,
            panChangeY = 0f,
            displayed = DisplayedSize(1000f, 1000f),
            containerWidth = 1000,
            containerHeight = 1000,
        )

        // The point 100 px right of centre is now 200 px right of it, so the picture moved 100 px left.
        assertEquals(2f, zoomed.zoom, 0f)
        assertEquals(-100f, zoomed.panX, 0.001f)
        assertEquals(0f, zoomed.panY, 0.001f)
    }

    @Test
    fun zoomStaysBetweenOneAndTheLimit() {
        val start = ViewTransform(zoom = 1f, panX = 0f, panY = 0f)
        val displayed = DisplayedSize(1000f, 1000f)

        val tooSmall = start.changed(0f, 0f, 0.1f, 0f, 0f, displayed, 1000, 1000)
        val tooLarge = start.changed(0f, 0f, 100f, 0f, 0f, displayed, 1000, 1000)

        assertEquals(1f, tooSmall.zoom, 0f)
        assertEquals(ViewTransform.MAX_ZOOM, tooLarge.zoom, 0f)
    }

    @Test
    fun panIsClampedToTheImageEdge() {
        val start = ViewTransform(zoom = 2f, panX = 0f, panY = 0f)

        val dragged = start.changed(0f, 0f, 1f, 5000f, -5000f, DisplayedSize(1000f, 1000f), 1000, 1000)

        // At 2x a 1000 px image is 2000 px wide on a 1000 px screen: 500 px of slack each way.
        assertEquals(500f, dragged.panX, 0.001f)
        assertEquals(-500f, dragged.panY, 0.001f)
    }
}
