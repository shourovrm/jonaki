package app.jonaki.feature.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GallerySelectionTest {
    private val first = image(1)
    private val second = image(2)
    private val third = image(3)

    @Test
    fun imagesAreNumberedInTheOrderTapped() {
        val selection = GallerySelection().toggled(third).toggled(first)

        assertEquals(1, selection.numberOf(3))
        assertEquals(2, selection.numberOf(1))
        assertNull(selection.numberOf(2))
        assertEquals(listOf(third, first), selection.images)
    }

    @Test
    fun tappingAPickedImageUnpicksItAndLaterNumbersMoveUp() {
        val selection = GallerySelection().toggled(first).toggled(second).toggled(third).toggled(second)

        assertNull(selection.numberOf(2))
        assertEquals(1, selection.numberOf(1))
        assertEquals(2, selection.numberOf(3))
        assertEquals(2, selection.count)
    }

    @Test
    fun theSameImageFromAnAlbumMatchesByIdNotByOrientationOrLink() {
        val fromRecent = GalleryImage(7, "content://media/external/images/media/7")
        val fromAlbum = GalleryImage(7, "content://media/external/images/media/7", orientationDegrees = 90)

        val selection = GallerySelection().toggled(fromRecent).toggled(fromAlbum)

        assertEquals(0, selection.count)
    }

    private fun image(id: Long) = GalleryImage(id, "content://media/external/images/media/$id")
}
