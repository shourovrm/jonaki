package app.jonaki.skills

import app.jonaki.core.skills.SkillDownloader
import app.jonaki.core.skills.SkillLibrary
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillImporterTest {
    private val root: File = Files.createTempDirectory("import").toFile()
    private val library = SkillLibrary(File(root, "skills"), File(root, "state.json"))
    private val importer = SkillImporter(library, SkillDownloader(OkHttpClient()))

    private fun skillBytes(body: String) = "---\nname: letter\ndescription: Formal letters.\n---\n$body\n".toByteArray()

    @Test
    fun aPickedSkillMdIsAddedAndASecondOneAsksBeforeReplacing() = runBlocking {
        assertEquals(ImportOutcome.Added("letter"), importer.fromFile(skillBytes("First")))

        val second = importer.fromFile(skillBytes("Second"))

        second as ImportOutcome.NameTaken
        assertEquals("letter", second.name)
        assertTrue(library.readText("letter")!!.contains("First"))

        assertEquals(ImportOutcome.Added("letter"), importer.replace(second.files))
        assertTrue(library.readText("letter")!!.contains("Second"))
    }

    @Test
    fun failuresCarryTheReason() = runBlocking {
        assertEquals(ImportOutcome.Failed("this is not a link"), importer.fromLink("my skill"))
        assertEquals(ImportOutcome.Failed("SKILL.md must start with ---"), importer.fromFile("hello".toByteArray()))
    }
}
