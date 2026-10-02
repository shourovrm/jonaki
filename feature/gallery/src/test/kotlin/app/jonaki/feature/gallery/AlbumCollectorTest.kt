package app.jonaki.feature.gallery

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumCollectorTest {
    @Test
    fun albumsComeInTheOrderOfTheirNewestImageWhichIsTheCover() {
        val collector = AlbumCollector()
        // Rows arrive newest first, as the MediaStore query sorts them.
        collector.add("screens", "Screenshots", image(9))
        collector.add("camera", "Camera", image(8))
        collector.add("screens", "Screenshots", image(7))
        collector.add("camera", "Camera", image(6))
        collector.add("camera", "Camera", image(5))

        val albums = collector.albums()

        assertEquals(listOf("screens", "camera"), albums.map { album -> album.id })
        assertEquals(listOf(9L, 8L), albums.map { album -> album.cover.id })
        assertEquals(listOf(2, 3), albums.map { album -> album.imageCount })
    }

    @Test
    fun aFolderWithoutANameGetsAnEmptyName() {
        val collector = AlbumCollector()
        collector.add("root", null, image(1))

        assertEquals("", collector.albums().single().name)
    }

    @Test
    fun noRowsGiveNoAlbums() {
        assertEquals(emptyList<GalleryAlbum>(), AlbumCollector().albums())
    }

    private fun image(id: Long) = GalleryImage(id, "content://media/external/images/media/$id")
}
