package app.jonaki.tools.generateimage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageFilesTest {
    @Test
    fun extensionComesFromTheMediaType() {
        assertEquals("png", ImageFiles.extensionFor("image/png", ByteArray(0)))
        assertEquals("jpg", ImageFiles.extensionFor("image/jpeg", ByteArray(0)))
        assertEquals("jpg", ImageFiles.extensionFor("image/jpg", ByteArray(0)))
        assertEquals("webp", ImageFiles.extensionFor("IMAGE/WEBP; charset=binary", ByteArray(0)))
    }

    @Test
    fun anUnknownMediaTypeFallsBackToTheFileHeader() {
        assertEquals("png", ImageFiles.extensionFor("application/octet-stream", pngHeader(8, 4)))
        assertEquals("png", ImageFiles.extensionFor("", pngHeader(8, 4)))
    }

    @Test
    fun noKnownTypeGivesNoExtension() {
        assertNull(ImageFiles.extensionFor("text/html", "<html>".toByteArray()))
    }

    private fun pngHeader(width: Int, height: Int): ByteArray = ImageDimensionsTest.png(width, height)
}
