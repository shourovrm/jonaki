package app.jonaki.tools.generateimage

import java.io.File
import java.nio.file.Files
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

    @Test
    fun aRequestedNameLosesFoldersAndTheOldExtension() {
        assertEquals("cat", ImageFiles.baseName("../../etc/cat.PNG", "a cat"))
        assertEquals("cat", ImageFiles.baseName("work\\cat.jpeg", "a cat"))
        assertEquals("poster-final", ImageFiles.baseName("poster final.webp", "x"))
    }

    @Test
    fun unsafeCharactersBecomeDashes() {
        assertEquals("a-b-c", ImageFiles.baseName("a:b*c?", "x"))
        assertEquals("notes-hidden", ImageFiles.baseName(".notes<hidden>", "x"))
    }

    @Test
    fun banglaLettersStay() {
        assertEquals("জোনাকি-১", ImageFiles.baseName("জোনাকি ১", "x"))
    }

    @Test
    fun withoutAUsableNameTheFirstWordsOfThePromptName_theFile() {
        assertEquals("a-red-bicycle-on-a-hill", ImageFiles.baseName(null, "A red bicycle on a hill, golden hour light, photo"))
        assertEquals("a-red-bicycle-on-a-hill", ImageFiles.baseName("///", "A red bicycle on a hill, golden hour light, photo"))
    }

    @Test
    fun withNothingToGoOnTheNameIsImage() {
        assertEquals("image", ImageFiles.baseName(null, "?!"))
    }

    @Test
    fun aLongNameIsCut() {
        assertEquals(60, ImageFiles.baseName("x".repeat(200), "p").length)
    }

    @Test
    fun anExistingFileIsNeverOverwritten() {
        val folder = Files.createTempDirectory("images").toFile()
        File(folder, "cat.png").writeBytes(byteArrayOf(1))

        val second = ImageFiles.freeFile(folder, "cat", "png")
        second.writeBytes(byteArrayOf(2))
        val third = ImageFiles.freeFile(folder, "cat", "png")

        assertEquals("cat (2).png", second.name)
        assertEquals("cat (3).png", third.name)
        assertEquals(1, File(folder, "cat.png").readBytes().size)
    }

    private fun pngHeader(width: Int, height: Int): ByteArray = ImageDimensionsTest.png(width, height)
}
