package app.jonaki.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun aTileTakesAPhotoDownToItsShortSide() {
        // A 96 dp tile at 3x density is 288 px; a 12-megapixel photo decodes at 8x, 500 x 375.
        assertEquals(PixelSize(384, 288), ImageScale.cover(PixelSize(4000, 3000), 288))
        assertEquals(8, ImageScale.sampleSize(PixelSize(4000, 3000), ImageScale.cover(PixelSize(4000, 3000), 288)))
    }

    @Test
    fun aTileNeverEnlargesASmallImage() {
        assertEquals(PixelSize(100, 80), ImageScale.cover(PixelSize(100, 80), 288))
    }

    @Test
    fun aTileOfALongStripKeepsItsShape() {
        assertEquals(PixelSize(288, 5760), ImageScale.cover(PixelSize(1000, 20000), 288))
    }

    @Test
    fun theFullViewDecodesAtTwiceTheFittedSize() {
        // 4000 x 3000 fits 1080 x 2000 at 0.27; twice that is 0.54.
        assertEquals(PixelSize(2160, 1620), ImageScale.forFullView(PixelSize(4000, 3000), PixelSize(1080, 2000)))
    }

    @Test
    fun theFullViewNeverEnlargesASmallImage() {
        assertEquals(PixelSize(300, 200), ImageScale.forFullView(PixelSize(300, 200), PixelSize(1080, 2000)))
    }

    @Test
    fun theFullViewOfAHugeStripStaysWithinTheMemoryLimit() {
        val target = ImageScale.forFullView(PixelSize(1000, 20000), PixelSize(1080, 2000))

        assertEquals(PixelSize(632, 12649), target)
        assertTrue(target.width.toLong() * target.height <= ImageScale.MAX_FULL_VIEW_PIXELS)
    }
}
