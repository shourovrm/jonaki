package app.jonaki.ui

import app.jonaki.core.storage.StepEntity
import app.jonaki.feature.chat.CodeSyntax
import app.jonaki.feature.chat.NotSavedFileUi
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeRunDetailsTest {
    private val pythonCode = "values = []\nprint(sum(values) / len(values))\n"

    private fun step(
        language: String = "python",
        code: String = pythonCode,
        status: String = "DONE",
        resultText: String? = null,
    ) = StepEntity(
        toolCallId = "call-1",
        threadId = "thread-1",
        toolName = "run_code",
        argumentsJson = buildJsonObject {
            put("language", language)
            put("code", code)
        }.toString(),
        status = status,
        resultText = resultText,
        startedAtMillis = 0,
        finishedAtMillis = 10,
    )

    private val noFiles: (String) -> String? = { null }

    @Test
    fun codeAndOutputOfAFailedPythonRun() {
        val toolText = "Python stopped with an error.\nPrinted:\nstarting\n" +
            "Error:\nTraceback (most recent call last):\n  File \"main.py\", line 2, in <module>\n" +
            "ZeroDivisionError: division by zero\n" +
            "Saved: work/partial.csv (new, 12 bytes)\nNot saved: inbox/a.csv (only files under work/ and artifacts/ are saved)\n" +
            "Fix the program and call run_code again."

        val details = CodeRunDetails.of(step(status = "FAILED"), toolText, noFiles)

        assertEquals("values = []\nprint(sum(values) / len(values))", details.code)
        assertEquals(CodeSyntax.PYTHON, details.syntax)
        assertEquals("Python", details.languageName)
        assertEquals("starting", details.printed)
        assertEquals(2, details.errorLine)
        assertTrue(details.error!!.endsWith("ZeroDivisionError: division by zero"))
        assertEquals(listOf("work/partial.csv"), details.savedFiles)
        assertEquals(listOf(NotSavedFileUi("inbox/a.csv", "only files under work/ and artifacts/ are saved")), details.notSavedFiles)
        assertFalse(details.outputIsCut)
    }

    @Test
    fun aRunningStepShowsItsCodeAndNoOutputYet() {
        val details = CodeRunDetails.of(step(status = "RUNNING"), toolResultText = null, readThreadFile = noFiles)

        assertTrue(details.isRunning)
        assertEquals("", details.printed)
        assertNull(details.error)
    }

    @Test
    fun aCutToolResultIsReadWholeFromItsSpillFile() {
        val wholeText = "JavaScript finished.\nPrinted:\n" + "row\n".repeat(10) + "Saved: work/out.txt (new, 3 bytes)"
        val cutText = "JavaScript finished.\nPrinted:\nrow\n\n[Output truncated: showed lines 1-3 of 13 " +
            "(30 of 200 characters). Full output saved to work/tool-output/run_code-1.txt. " +
            "Use read_file path=\"work/tool-output/run_code-1.txt\" offset=4 to continue.]"
        val files = mapOf("work/tool-output/run_code-1.txt" to wholeText)

        val details = CodeRunDetails.of(step(language = "javascript"), cutText) { path -> files[path] }

        assertEquals("row\n".repeat(10).trimEnd(), details.printed)
        assertEquals(listOf("work/out.txt"), details.savedFiles)
        assertFalse(details.outputIsCut)
    }

    @Test
    fun aCutToolResultWithoutItsSpillFileShowsTheStartAndSaysSo() {
        val cutText = "JavaScript finished.\nPrinted:\nrow\n\n[Output truncated: showed 30 of 200 characters. " +
            "Full output saved to work/tool-output/run_code-1.txt; search it with search_files path=\"x\".]"

        val details = CodeRunDetails.of(step(language = "javascript"), cutText, noFiles)

        assertEquals("row", details.printed)
        assertTrue(details.outputIsCut)
    }

    @Test
    fun withoutAToolRowTheStepPreviewIsUsed() {
        val preview = "Python finished.\nPrinted:\n" + "x".repeat(2_000)

        val details = CodeRunDetails.of(step(resultText = preview.take(2_000)), toolResultText = null, readThreadFile = noFiles)

        assertTrue(details.printed.startsWith("xxx"))
        assertTrue(details.outputIsCut)
    }

    @Test
    fun anUnknownLanguageIsShownPlainUnderItsOwnName() {
        val details = CodeRunDetails.of(step(language = "ruby", code = "puts 1"), "Error: language \"ruby\" is not supported. Use javascript or python.", noFiles)

        assertNull(details.syntax)
        assertEquals("ruby", details.languageName)
        assertNull(details.errorLine)
        assertTrue(details.error!!.startsWith("language \"ruby\""))
    }

    @Test
    fun veryLongPrintedTextIsShortenedForTheScreen() {
        val toolText = "Python finished.\nPrinted:\n" + "y".repeat(CodeRunDetails.MAX_SHOWN_CHARACTERS + 10)

        val details = CodeRunDetails.of(step(), toolText, noFiles)

        assertEquals(CodeRunDetails.MAX_SHOWN_CHARACTERS, details.printed.length)
        assertTrue(details.outputIsCut)
    }
}
