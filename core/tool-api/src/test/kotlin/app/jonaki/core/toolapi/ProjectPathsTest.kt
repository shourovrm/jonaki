package app.jonaki.core.toolapi

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectPathsTest {
    private val threadFolder: File = Files.createTempDirectory("thread").toFile()
    private val projectFolder: File = Files.createTempDirectory("project").toFile()
    private val paths = ThreadPaths(threadFolder, projectFolder)

    @Test
    fun projectPathResolvesInsideTheProjectFolder() {
        assertEquals(File(projectFolder, "data/rows.csv").canonicalFile, paths.resolve("/project/data/rows.csv"))
        assertEquals(projectFolder.canonicalFile, paths.resolve("/project"))
    }

    @Test
    fun projectPathCannotEscapeTheProjectFolder() {
        assertNull(paths.resolve("/project/../secret.txt"))
        assertNull(paths.resolve("/project/data/../../thread-file.txt"))
    }

    @Test
    fun aThreadWithoutAProjectRefusesProjectPaths() {
        val withoutProject = ThreadPaths(threadFolder)
        assertNull(withoutProject.resolve("/project/notes.md"))
    }

    @Test
    fun aFolderNamedProjectInsideTheThreadStaysAThreadPath() {
        assertEquals(File(threadFolder, "project/notes.md").canonicalFile, paths.resolve("project/notes.md"))
    }

    @Test
    fun relativePathNamesProjectFilesUnderProject() {
        val file = File(projectFolder, "notes.md")
        assertEquals("/project/notes.md", paths.relativePath(file))
        assertEquals("work/a.txt", paths.relativePath(File(threadFolder, "work/a.txt")))
        assertTrue(paths.isInProject(file))
        assertFalse(paths.isInProject(File(threadFolder, "work/a.txt")))
    }
}
