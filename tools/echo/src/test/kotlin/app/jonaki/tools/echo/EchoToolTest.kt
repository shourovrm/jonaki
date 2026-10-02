package app.jonaki.tools.echo

import app.jonaki.core.toolapi.ToolContext
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoToolTest {
    private val context = ToolContext(threadFolder = File("build/test-thread"))

    @Test
    fun returnsTheTextUnchanged() = runBlocking {
        val output = EchoTool().run(JsonObject(mapOf("text" to JsonPrimitive("জোনাকি hello"))), context)
        assertEquals("জোনাকি hello", output.text)
    }

    @Test
    fun missingTextIsAnErrorThatNamesTheFix() = runBlocking {
        val output = EchoTool().run(JsonObject(emptyMap()), context)
        assertTrue(output.isError)
        assertTrue(output.text.contains("Call echo again"))
    }
}
