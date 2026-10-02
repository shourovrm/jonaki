package app.jonaki.core.toolapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolOutputTest {
    @Test
    fun errorNamesTheFailureAndTheNextStep() {
        val output = ToolOutput.error("file notes.md not found", "Use find_files to list the folder.")
        assertTrue(output.isError)
        assertEquals("Error: file notes.md not found. Use find_files to list the folder.", output.text)
    }
}
