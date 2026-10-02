package app.jonaki.tools.findfiles

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

class FindFilesToolTest {
    private val threadFolder: File = Files.createTempDirectory("thread").toFile()
    private val context = ToolContext(threadFolder, OkHttpClient())
    private val tool = FindFilesTool()

    init {
        for (path in listOf("inbox/sales.csv", "work/notes.md", "work/draft/plan.md", "artifacts/report.html")) {
            val file = File(threadFolder, path)
            file.parentFile.mkdirs()
            file.writeText("x")
        }
    }

    private fun find(vararg arguments: Pair<String, String>): ToolOutput = runBlocking {
        tool.run(JsonObject(arguments.associate { (key, value) -> key to JsonPrimitive(value) }), context)
    }

    @Test
    fun noArgumentsListsEveryFileSorted() {
        assertEquals(
            "artifacts/report.html\ninbox/sales.csv\nwork/draft/plan.md\nwork/notes.md",
            find().text,
        )
    }

    @Test
    fun namePatternMatchesInEveryFolder() {
        assertEquals("work/draft/plan.md\nwork/notes.md", find("pattern" to "*.md").text)
    }

    @Test
    fun pathLimitsTheSearchAndResultsStayRelativeToTheThread() {
        assertEquals("work/draft/plan.md", find("pattern" to "*.md", "path" to "work/draft").text)
    }

    @Test
    fun noMatchIsNotAnError() {
        val output = find("pattern" to "*.pdf")
        assertTrue(!output.isError && output.text.startsWith("No files match"))
    }

    @Test
    fun folderOutsideTheThreadIsRefused() {
        assertTrue(find("path" to "..").isError)
    }

    @Test
    fun manyMatchesAreCappedWithANotice() {
        val many = File(threadFolder, "many")
        many.mkdirs()
        for (number in 1..250) {
            File(many, "file-$number.txt").writeText("x")
        }
        val output = find("path" to "many")
        assertTrue(output.text.contains("[Showing 200 of 250 files."))
    }
}
