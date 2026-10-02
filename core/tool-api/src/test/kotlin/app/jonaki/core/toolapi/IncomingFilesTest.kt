package app.jonaki.core.toolapi

import java.io.ByteArrayInputStream
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingFilesTest {
    private val folder = Files.createTempDirectory("inbox").toFile()

    @Test
    fun plainNameStaysAsItIs() {
        assertEquals("sales report.csv", IncomingFiles.safeName("sales report.csv"))
        assertEquals("বিক্রি.csv", IncomingFiles.safeName("বিক্রি.csv"))
    }

    @Test
    fun folderSeparatorsAndControlCharactersBecomeUnderscores() {
        assertEquals("a_b_c.txt", IncomingFiles.safeName("a/b\\c.txt"))
        assertEquals("line_break.txt", IncomingFiles.safeName("line\nbreak.txt"))
    }

    @Test
    fun emptyOrDotNamesBecomeFile() {
        assertEquals("file", IncomingFiles.safeName(null))
        assertEquals("file", IncomingFiles.safeName("   "))
        assertEquals("file", IncomingFiles.safeName(".."))
        assertEquals("file", IncomingFiles.safeName("."))
    }

    @Test
    fun longNameIsShortenedButKeepsItsExtension() {
        val name = IncomingFiles.safeName("x".repeat(300) + ".pdf")
        assertTrue(name.length <= IncomingFiles.MAX_NAME_LENGTH)
        assertTrue(name.endsWith(".pdf"))
    }

    @Test
    fun freeNameIsTheNameItselfWhenUnused() {
        assertEquals("sales.csv", IncomingFiles.freeFileIn(folder, "sales.csv").name)
    }

    @Test
    fun takenNamesGetNumberedSuffixesBeforeTheExtension() {
        folder.resolve("sales.csv").writeText("1")
        assertEquals("sales (2).csv", IncomingFiles.freeFileIn(folder, "sales.csv").name)
        folder.resolve("sales (2).csv").writeText("2")
        assertEquals("sales (3).csv", IncomingFiles.freeFileIn(folder, "sales.csv").name)
    }

    @Test
    fun namesWithoutExtensionOrStartingWithADotGetTheSuffixAtTheEnd() {
        folder.resolve("README").writeText("1")
        folder.resolve(".env").writeText("1")
        assertEquals("README (2)", IncomingFiles.freeFileIn(folder, "README").name)
        assertEquals(".env (2)", IncomingFiles.freeFileIn(folder, ".env").name)
    }

    @Test
    fun copyWritesAllBytesUnderTheLimit() {
        val target = folder.resolve("copy.bin")
        val copied = IncomingFiles.copyWithLimit(ByteArrayInputStream(ByteArray(1_000) { 7 }), target, maxBytes = 1_000)
        assertEquals(1_000L, copied)
        assertEquals(1_000L, target.length())
    }

    @Test
    fun copyOverTheLimitFailsAndLeavesNoPartFile() {
        val target = folder.resolve("big.bin")
        val error = assertThrows(FileTooLargeException::class.java) {
            IncomingFiles.copyWithLimit(ByteArrayInputStream(ByteArray(1_001)), target, maxBytes = 1_000)
        }
        assertEquals(1_000L, error.limitBytes)
        assertFalse(target.exists())
    }

    @Test
    fun limitReadsAsMegabytes() {
        assertEquals("25 MB", IncomingFiles.describeSize(IncomingFiles.MAX_IMPORT_BYTES))
        assertEquals("1.5 MB", IncomingFiles.describeSize(1_572_864))
        assertEquals("12 KB", IncomingFiles.describeSize(12_288))
        assertEquals("900 bytes", IncomingFiles.describeSize(900))
    }
}
