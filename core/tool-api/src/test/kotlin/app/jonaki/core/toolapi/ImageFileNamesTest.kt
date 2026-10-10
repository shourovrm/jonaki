package app.jonaki.core.toolapi

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageFileNamesTest {
    @Test
    fun aRequestedNameLosesFoldersAndTheOldExtension() {
        assertEquals("cat", ImageFileNames.baseName("../../etc/cat.PNG", "a cat"))
        assertEquals("cat", ImageFileNames.baseName("work\\cat.jpeg", "a cat"))
        assertEquals("poster-final", ImageFileNames.baseName("poster final.webp", "x"))
    }

    @Test
    fun unsafeCharactersBecomeDashes() {
        assertEquals("a-b-c", ImageFileNames.baseName("a:b*c?", "x"))
        assertEquals("notes-hidden", ImageFileNames.baseName(".notes<hidden>", "x"))
    }

    @Test
    fun banglaLettersStay() {
        assertEquals("জোনাকি-১", ImageFileNames.baseName("জোনাকি ১", "x"))
    }

    @Test
    fun withoutAUsableNameTheFirstWordsOfThePromptName_theFile() {
        assertEquals("a-red-bicycle-on-a-hill", ImageFileNames.baseName(null, "A red bicycle on a hill, golden hour light, photo"))
        assertEquals("a-red-bicycle-on-a-hill", ImageFileNames.baseName("///", "A red bicycle on a hill, golden hour light, photo"))
    }

    @Test
    fun withNothingToGoOnTheNameIsImage() {
        assertEquals("image", ImageFileNames.baseName(null, "?!"))
    }

    @Test
    fun aLongNameIsCut() {
        assertEquals(60, ImageFileNames.baseName("x".repeat(200), "p").length)
    }

    @Test
    fun anExistingFileIsNeverOverwritten() {
        val folder = Files.createTempDirectory("images").toFile()
        File(folder, "cat.png").writeBytes(byteArrayOf(1))

        val second = ImageFileNames.freeFile(folder, "cat", "png")
        second.writeBytes(byteArrayOf(2))
        val third = ImageFileNames.freeFile(folder, "cat", "png")

        assertEquals("cat (2).png", second.name)
        assertEquals("cat (3).png", third.name)
        assertEquals(1, File(folder, "cat.png").readBytes().size)
    }

    @Test
    fun aSvgEndingIsDroppedFromARequestedName() {
        assertEquals("logo", ImageFileNames.baseName("logo.SVG", "x"))
    }
}
