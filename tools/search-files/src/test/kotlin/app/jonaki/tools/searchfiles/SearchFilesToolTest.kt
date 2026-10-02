package app.jonaki.tools.searchfiles

import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFilesToolTest {
    private val threadFolder: File = Files.createTempDirectory("thread").toFile()
    private val context = ToolContext(threadFolder, OkHttpClient())
    private val tool = SearchFilesTool()

    init {
        write("work/notes.md", "Invoice from Daraz\nরসিদ: ২০০ টাকা\nnothing here\n")
        write("inbox/mail.txt", "Your invoice is attached\n")
        File(threadFolder, "inbox/photo.jpg").writeBytes(byteArrayOf(-1, -40, 0, 0, 73, 110, 118, 111, 105, 99, 101))
    }

    private fun write(path: String, text: String) {
        val file = File(threadFolder, path)
        file.parentFile.mkdirs()
        file.writeText(text)
    }

    private fun search(vararg arguments: Pair<String, Any>): ToolOutput = runBlocking {
        val json = arguments.associate { (key, value) ->
            key to if (value is Boolean) JsonPrimitive(value) else JsonPrimitive(value.toString())
        }
        tool.run(JsonObject(json), context)
    }

    @Test
    fun findsMatchingLinesWithFileAndLineNumber() {
        assertEquals("inbox/mail.txt:1: Your invoice is attached", search("pattern" to "invoice").text)
    }

    @Test
    fun ignoreCaseFindsBothAndSkipsBinaryFiles() {
        val output = search("pattern" to "invoice", "ignore_case" to true)
        assertEquals("inbox/mail.txt:1: Your invoice is attached\nwork/notes.md:1: Invoice from Daraz", output.text)
    }

    @Test
    fun banglaPatternsWork() {
        assertEquals("work/notes.md:2: রসিদ: ২০০ টাকা", search("pattern" to "টাকা").text)
    }

    @Test
    fun globLimitsTheFiles() {
        val output = search("pattern" to "(?i)invoice", "glob" to "*.md")
        assertEquals("work/notes.md:1: Invoice from Daraz", output.text)
    }

    @Test
    fun invalidRegexSaysHowToFixIt() {
        val output = search("pattern" to "price (usd")
        assertTrue(output.isError)
        assertTrue(output.text.contains("backslash"))
    }

    @Test
    fun noMatchIsNotAnError() {
        val output = search("pattern" to "zebra")
        assertTrue(!output.isError && output.text.startsWith("No lines match"))
    }

    @Test
    fun manyMatchesAreCapped() {
        write("big.txt", (1..150).joinToString("\n") { number -> "hit $number" })
        val output = search("pattern" to "hit", "path" to "big.txt")
        assertTrue(output.text.contains("[Showing 100 of 150 matching lines."))
    }

    @Test
    fun outsideTheThreadIsRefused() {
        assertTrue(search("pattern" to "root", "path" to "/etc").isError)
    }
}
