package app.jonaki.tools.readfile

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

class ReadFileToolTest {
    private val threadFolder: File = Files.createTempDirectory("thread").toFile()
    private val context = ToolContext(threadFolder, OkHttpClient())
    private val tool = ReadFileTool()

    private fun read(vararg arguments: Pair<String, Any>): ToolOutput = runBlocking {
        val json = JsonObject(
            arguments.associate { (key, value) ->
                key to if (value is Int) JsonPrimitive(value) else JsonPrimitive(value.toString())
            },
        )
        tool.run(json, context)
    }

    private fun writeFile(path: String, text: String) {
        val file = File(threadFolder, path)
        file.parentFile.mkdirs()
        file.writeText(text)
    }

    @Test
    fun readsAWholeSmallFile() {
        writeFile("work/notes.md", "জোনাকি\nsecond line\n")
        val output = read("path" to "work/notes.md")
        assertFalse(output.isError)
        assertEquals("জোনাকি\nsecond line", output.text)
    }

    @Test
    fun offsetAndLimitShowAWindowAndNameTheNextCall() {
        writeFile("long.txt", (1..50).joinToString("\n") { number -> "line $number" })
        val output = read("path" to "long.txt", "offset" to 11, "limit" to 5)
        assertTrue(output.text.startsWith("line 11\nline 12"))
        assertTrue(output.text.contains("line 15\n\n[Showing lines 11-15 of 50."))
        assertTrue(output.text.contains("Use read_file path=\"long.txt\" offset=16 to continue."))
    }

    @Test
    fun readsTheLimiterSpillFileFromItsNotice() {
        writeFile("work/tool-output/web_fetch-1.txt", "a\nb\nc\n")
        val output = read("path" to "work/tool-output/web_fetch-1.txt", "offset" to "2")
        assertEquals("b\nc", output.text)
    }

    @Test
    fun windowsLineEndsAreRemoved() {
        writeFile("dos.txt", "one\r\ntwo\r\n")
        assertEquals("one\ntwo", read("path" to "dos.txt").text)
    }

    @Test
    fun veryLongLinesAreCut() {
        writeFile("wide.txt", "x".repeat(5_000))
        val output = read("path" to "wide.txt")
        assertTrue(output.text.endsWith("[line cut at 2000 characters]"))
    }

    @Test
    fun pathOutsideTheThreadIsRefused() {
        val output = read("path" to "../../etc/passwd")
        assertTrue(output.isError)
        assertTrue(output.text.contains("outside the thread folder"))
    }

    @Test
    fun missingFileSuggestsFindFiles() {
        val output = read("path" to "nothing.md")
        assertTrue(output.isError)
        assertTrue(output.text.contains("find_files"))
    }

    @Test
    fun offsetPastTheEndSaysHowManyLinesThereAre() {
        writeFile("short.txt", "one\ntwo\n")
        val output = read("path" to "short.txt", "offset" to 9)
        assertTrue(output.isError)
        assertTrue(output.text.contains("has 2 lines"))
    }

    @Test
    fun binaryFileIsRefused() {
        File(threadFolder, "image.png").writeBytes(byteArrayOf(-119, 80, 78, 71, 0, 0))
        assertTrue(read("path" to "image.png").isError)
    }

    @Test
    fun emptyFileSaysSo() {
        writeFile("empty.txt", "")
        assertEquals("(empty.txt is empty)", read("path" to "empty.txt").text)
    }

    @Test
    fun readsASkillFileFromTheLibrary() {
        val library = Files.createTempDirectory("skills").toFile()
        File(library, "report").mkdirs()
        File(library, "report/SKILL.md").writeText("---\nname: report\n---\nSteps\n")
        val skillContext = ToolContext(threadFolder, OkHttpClient(), skillLibraryFolder = library)

        val output = runBlocking { tool.run(JsonObject(mapOf("path" to JsonPrimitive("/skills/report/SKILL.md"))), skillContext) }

        assertFalse(output.isError)
        assertEquals("---\nname: report\n---\nSteps", output.text)
    }

    @Test
    fun skillPathsFailLoudlyWithoutALibraryOrOutsideIt() {
        val library = Files.createTempDirectory("skills").toFile()
        val skillContext = ToolContext(threadFolder, OkHttpClient(), skillLibraryFolder = library)

        val withoutLibrary = read("path" to "/skills/report/SKILL.md")
        val escape = runBlocking { tool.run(JsonObject(mapOf("path" to JsonPrimitive("/skills/../x"))), skillContext) }
        val missing = runBlocking { tool.run(JsonObject(mapOf("path" to JsonPrimitive("/skills/nope/SKILL.md"))), skillContext) }

        assertTrue(withoutLibrary.isError)
        assertTrue(withoutLibrary.text.contains("no skill library"))
        assertTrue(escape.isError)
        assertTrue(escape.text.contains("outside the skill library"))
        assertTrue(missing.isError)
        assertTrue(missing.text.contains("Skills list"))
    }
}
