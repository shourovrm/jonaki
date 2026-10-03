package app.jonaki.feature.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagePagesTest {
    @Test
    fun anEmptyGridAsksForTheFirstPageBeforeLayout() {
        assertTrue(ImagePages().needsMore(lastVisibleIndex = -1))
    }

    @Test
    fun aFullPageLeavesMoreToLoadAndAShortPageEndsIt() {
        val full = ImagePages().withPage(images(0L until 120L), pageSize = 120)
        assertFalse(full.endReached)
        assertEquals(120, full.nextOffset)

        val ended = full.withPage(images(120L until 150L), pageSize = 120)
        assertTrue(ended.endReached)
        assertFalse(ended.needsMore(lastVisibleIndex = 149))
    }

    @Test
    fun theNextPageIsWantedOnlyNearTheEndOfWhatIsLoaded() {
        val pages = ImagePages().withPage(images(0L until 120L), pageSize = 120)

        assertFalse(pages.needsMore(lastVisibleIndex = 20))
        assertFalse(pages.needsMore(lastVisibleIndex = 89))
        assertTrue(pages.needsMore(lastVisibleIndex = 90))
    }

    @Test
    fun anImageRepeatedByAShiftedOffsetIsDroppedAndOrderKept() {
        // A new photo arrived between the two queries, so image 119 comes back at offset 120.
        val first = ImagePages().withPage(images(0L until 120L), pageSize = 120)
        val second = first.withPage(images(119L until 239L), pageSize = 120)

        assertEquals(239, second.images.size)
        assertEquals(second.images.map { image -> image.id }.distinct(), second.images.map { image -> image.id })
        assertEquals(238L, second.images.last().id)
        assertEquals(240, second.nextOffset)
        assertFalse(second.endReached)
    }

    @Test
    fun aPageOfOnlyRepeatsStillMovesTheOffsetOn() {
        val first = ImagePages().withPage(images(0L until 3L), pageSize = 3)
        val repeats = first.withPage(images(0L until 3L), pageSize = 3)

        assertEquals(3, repeats.images.size)
        assertEquals(6, repeats.nextOffset)
    }

    private fun images(ids: LongRange): List<GalleryImage> =
        ids.map { id -> GalleryImage(id, "content://media/external/images/media/$id") }
}
