package app.jonaki.core.toolapi

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SkillLibraryPathsTest {
    private val library: File = Files.createTempDirectory("skills").toFile()
    private val paths = SkillLibraryPaths(library)

    @Test
    fun skillPathResolvesInsideTheLibrary() {
        assertEquals(File(library, "report/SKILL.md").canonicalFile, paths.resolve("/skills/report/SKILL.md"))
        assertEquals(File(library, "report/templates/a.html").canonicalFile, paths.resolve("/skills/report/templates/a.html"))
        assertEquals("/skills/report/SKILL.md", SkillLibraryPaths.skillFilePath("report"))
    }

    @Test
    fun otherPathsAreNotSkillPaths() {
        assertNull(paths.resolve("skills/report/SKILL.md"))
        assertNull(paths.resolve("/skillsets/report/SKILL.md"))
        assertNull(paths.resolve("work/notes.md"))
    }

    @Test
    fun escapesAreRejected() {
        assertNull(paths.resolve("/skills/../threads/t1/work/a.md"))
        assertNull(paths.resolve("/skills/report/../../secret"))
        val outside = Files.createTempDirectory("outside").toFile()
        Files.createSymbolicLink(File(library, "link").toPath(), outside.toPath())
        assertNull(paths.resolve("/skills/link/file.txt"))
    }
}
