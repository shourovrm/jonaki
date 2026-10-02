package app.jonaki.core.skills

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class SkillArchiveTest {
    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            for ((name, text) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    @Test
    fun aTextFileIsTheSkillMd() {
        val result = SkillArchive.filesOf("---\nname: a\n---\n".toByteArray())

        assertEquals("---\nname: a\n---\n", (result as SkillFilesResult.Files).files.getValue("SKILL.md").decodeToString())
    }

    @Test
    fun aZipKeepsTheFolderThatHoldsSkillMd() {
        val zip = zipOf(
            "report/SKILL.md" to "skill",
            "report/templates/page.html" to "page",
            "__MACOSX/report/._SKILL.md" to "junk",
            "README.md" to "outside",
        )

        val result = SkillArchive.filesOf(zip) as SkillFilesResult.Files

        assertEquals(setOf("SKILL.md", "templates/page.html"), result.files.keys)
        assertEquals("page", result.files.getValue("templates/page.html").decodeToString())
    }

    @Test
    fun aZipWithSkillMdAtTheTopKeepsEverything() {
        val result = SkillArchive.filesOf(zipOf("SKILL.md" to "skill", "notes/a.txt" to "a")) as SkillFilesResult.Files

        assertEquals(setOf("SKILL.md", "notes/a.txt"), result.files.keys)
    }

    @Test
    fun aZipWithoutSkillMdOrTooBigIsRefused() {
        assertEquals(SkillFilesResult.Failed("no SKILL.md found"), SkillArchive.filesOf(zipOf("a.txt" to "a")))
        assertEquals(
            SkillFilesResult.Failed("the skill is larger than 2 MB"),
            SkillArchive.filesOf(zipOf("SKILL.md" to "x".repeat(SkillDownloader.MAX_TOTAL_BYTES + 1))),
        )
    }
}
