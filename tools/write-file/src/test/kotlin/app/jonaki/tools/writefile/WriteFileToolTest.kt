package app.jonaki.tools.writefile

import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WriteFileToolTest {
    private val threadFolder: File = Files.createTempDirectory("thread").toFile()
    private val context = ToolContext(threadFolder, OkHttpClient())
    private val tool = WriteFileTool()

    private fun write(vararg arguments: Pair<String, String>): ToolOutput = runBlocking {
        tool.run(JsonObject(arguments.associate { (key, value) -> key to JsonPrimitive(value) }), context)
    }

    @Test
    fun needsApproval() {
        assertEquals(SideEffect.CHANGES_THREAD_FOLDER, tool.sideEffect)
    }

    @Test
    fun createsTheFileAndItsFolders() {
        val output = write("path" to "work/ideas/notes.md", "content" to "one\ntwo\nতিন\n")
        assertFalse(output.isError)
        assertEquals("Created work/ideas/notes.md: 3 lines, 12 characters.", output.text)
        assertEquals("one\ntwo\nতিন\n", File(threadFolder, "work/ideas/notes.md").readText())
    }

    @Test
    fun replacesAnExistingFile() {
        write("path" to "a.txt", "content" to "old")
        val output = write("path" to "a.txt", "content" to "new")
        assertTrue(output.text.startsWith("Replaced a.txt"))
        assertEquals("new", File(threadFolder, "a.txt").readText())
    }

    @Test
    fun refusesPathsOutsideTheThread() {
        val output = write("path" to "../escape.txt", "content" to "x")
        assertTrue(output.isError)
        assertFalse(File(threadFolder.parentFile, "escape.txt").exists())
    }

    @Test
    fun missingContentIsAnError() {
        assertTrue(write("path" to "a.txt").isError)
    }

    @Test
    fun cannotWriteIntoTheSkillLibrary() {
        val library = Files.createTempDirectory("skills").toFile()
        File(library, "report").mkdirs()
        val skillContext = ToolContext(threadFolder, OkHttpClient(), skillLibraryFolder = library)
        val arguments = JsonObject(mapOf("path" to JsonPrimitive("/skills/report/SKILL.md"), "content" to JsonPrimitive("x")))

        val output = runBlocking { tool.run(arguments, skillContext) }

        assertTrue(output.isError)
        assertFalse(File(library, "report/SKILL.md").exists())
        assertFalse(File("/skills/report/SKILL.md").exists())
    }
}
