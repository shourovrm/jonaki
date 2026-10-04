package app.jonaki.files

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TileCacheKeyTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun imageFile(content: String, modifiedAt: Long): File {
        val file = folder.newFile()
        file.writeText(content)
        file.setLastModified(modifiedAt)
        return file
    }

    @Test
    fun theSameFileAndSizeGiveTheSameKey() {
        val file = imageFile("abc", 1_000_000L)

        assertEquals(TileCacheKey.of(file, 288), TileCacheKey.of(file, 288))
    }

    @Test
    fun anotherTileSizeGivesAnotherKey() {
        val file = imageFile("abc", 1_000_000L)

        assertNotEquals(TileCacheKey.of(file, 288), TileCacheKey.of(file, 216))
    }

    @Test
    fun aFileChangedLaterGivesAnotherKey() {
        val file = imageFile("abc", 1_000_000L)
        val before = TileCacheKey.of(file, 288)

        file.setLastModified(2_000_000L)

        assertNotEquals(before, TileCacheKey.of(file, 288))
    }

    @Test
    fun aFileOfAnotherLengthGivesAnotherKey() {
        val file = imageFile("abc", 1_000_000L)
        val before = TileCacheKey.of(file, 288)

        file.writeText("abcdef")
        file.setLastModified(1_000_000L)

        assertNotEquals(before, TileCacheKey.of(file, 288))
    }
}
