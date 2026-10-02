package app.jonaki.files

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageScaleTest {
    @Test
    fun aLargePhotoShrinksToTheLongSideKeepingItsShape() {
        assertEquals(PixelSize(1568, 1176), ImageScale.fitted(PixelSize(4000, 3000)))
        assertEquals(PixelSize(882, 1568), ImageScale.fitted(PixelSize(2250, 4000)))
    }

    @Test
    fun aSmallImageKeepsItsSize() {
        assertEquals(PixelSize(800, 600), ImageScale.fitted(PixelSize(800, 600)))
    }

    @Test
    fun sampleSizeHalvesWhileTheTargetStillFits() {
        assertEquals(2, ImageScale.sampleSize(PixelSize(4000, 3000), PixelSize(1568, 1176)))
        assertEquals(4, ImageScale.sampleSize(PixelSize(8000, 6000), PixelSize(1568, 1176)))
        assertEquals(1, ImageScale.sampleSize(PixelSize(800, 600), PixelSize(800, 600)))
    }
}
