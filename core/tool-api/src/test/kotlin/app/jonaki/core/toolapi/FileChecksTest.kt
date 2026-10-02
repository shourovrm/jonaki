package app.jonaki.core.toolapi

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileChecksTest {
    @Test
    fun textIsNotBinaryAndZeroBytesAre() {
        val text = Files.createTempFile("text", ".txt").toFile()
        text.writeText("জোনাকি plain text\n")
        val binary = Files.createTempFile("data", ".bin").toFile()
        binary.writeBytes(byteArrayOf(80, 75, 3, 4, 0, 0))

        assertFalse(looksBinary(text))
        assertTrue(looksBinary(binary))
    }

    @Test
    fun patternWithoutSlashMatchesTheFileNameInAnyFolder() {
        val filter = GlobFilter("*.md")
        assertTrue(filter.matches(Paths.get("notes.md")))
        assertTrue(filter.matches(Paths.get("work/deep/notes.md")))
        assertFalse(filter.matches(Paths.get("work/notes.txt")))
    }

    @Test
    fun patternWithSlashMatchesTheWholePath() {
        val filter = GlobFilter("work/*.md")
        assertTrue(filter.matches(Paths.get("work/notes.md")))
        assertFalse(filter.matches(Paths.get("inbox/notes.md")))
    }
}
