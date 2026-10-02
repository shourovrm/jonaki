package app.jonaki.tools.editfile

import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditFileToolTest {
    private val threadFolder: File = Files.createTempDirectory("thread").toFile()
    private val context = ToolContext(threadFolder, OkHttpClient())
    private val tool = EditFileTool()
    private val notes = File(threadFolder, "notes.md")

    private fun edit(argumentsJson: String): ToolOutput = runBlocking {
        tool.run(Json.parseToJsonElement(argumentsJson).jsonObject, context)
    }

    @Test
    fun needsApproval() {
        assertEquals(SideEffect.CHANGES_THREAD_FOLDER, tool.sideEffect)
    }

    @Test
    fun appliesSeveralEdits() {
        notes.writeText("1. tea\n2. rice\n3. fish\n")
        val output = edit(
            """{"path":"notes.md","edits":[{"old_text":"2. rice","new_text":"2. dal"},""" +
                """{"old_text":"3. fish","new_text":"3. ilish"}]}""",
        )
        assertFalse(output.text, output.isError)
        assertEquals("1. tea\n2. dal\n3. ilish\n", notes.readText())
        assertEquals("Applied 2 edits to notes.md: edit 1 at line 2 (exact); edit 2 at line 3 (exact).", output.text)
    }

    @Test
    fun failedEditLeavesTheFileUnchanged() {
        notes.writeText("a\nb\n")
        val output = edit(
            """{"path":"notes.md","edits":[{"old_text":"a","new_text":"A"},{"old_text":"zzz","new_text":"Z"}]}""",
        )
        assertTrue(output.isError)
        assertTrue(output.text.contains("edit 2 of 2 failed"))
        assertEquals("a\nb\n", notes.readText())
    }

    @Test
    fun acceptsOneTopLevelPair() {
        notes.writeText("hello world\n")
        edit("""{"path":"notes.md","old_text":"world","new_text":"জোনাকি"}""")
        assertEquals("hello জোনাকি\n", notes.readText())
    }

    @Test
    fun missingFileSuggestsWriteFile() {
        val output = edit("""{"path":"absent.md","edits":[{"old_text":"a","new_text":"b"}]}""")
        assertTrue(output.isError)
        assertTrue(output.text.contains("write_file"))
    }

    @Test
    fun refusesPathsOutsideTheThread() {
        val output = edit("""{"path":"/etc/hosts","edits":[{"old_text":"a","new_text":"b"}]}""")
        assertTrue(output.isError)
        assertTrue(output.text.contains("outside the thread folder"))
    }
}
