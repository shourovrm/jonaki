package app.jonaki.core.toolapi

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThreadPathsTest {
    private val threadFolder: File = Files.createTempDirectory("thread").toFile()
    private val paths = ThreadPaths(threadFolder)

    @Test
    fun relativePathResolvesInsideTheThreadFolder() {
        val resolved = paths.resolve("work/notes.md")
        assertEquals(File(threadFolder, "work/notes.md").canonicalFile, resolved)
    }

    @Test
    fun parentEscapeIsRejected() {
        assertNull(paths.resolve("../secret.txt"))
        assertNull(paths.resolve("work/../../secret.txt"))
    }

    @Test
    fun absolutePathOutsideIsRejected() {
        assertNull(paths.resolve("/etc/passwd"))
    }

    @Test
    fun absolutePathInsideIsAccepted() {
        val inside = File(threadFolder, "inbox/a.csv").absolutePath
        assertEquals(File(inside).canonicalFile, paths.resolve(inside))
    }

    @Test
    fun symlinkPointingOutsideIsRejected() {
        val outside = Files.createTempDirectory("outside").toFile()
        Files.createSymbolicLink(File(threadFolder, "link").toPath(), outside.toPath())
        assertNull(paths.resolve("link/file.txt"))
    }

    @Test
    fun pathIsShownRelativeToTheThreadFolder() {
        val file = File(threadFolder, "work/notes.md")
        assertEquals("work/notes.md", paths.relativePath(file))
    }
}
