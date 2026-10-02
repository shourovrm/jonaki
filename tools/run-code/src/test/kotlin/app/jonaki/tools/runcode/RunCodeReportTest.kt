package app.jonaki.tools.runcode

import app.jonaki.core.runtimeapi.CodeJob
import app.jonaki.core.runtimeapi.CodeLanguage
import app.jonaki.core.runtimeapi.CodeRunOutcome
import app.jonaki.core.runtimeapi.CodeRuntime
import app.jonaki.core.runtimeapi.OutputFile
import app.jonaki.core.toolapi.ToolContext
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reads back what [RunCodeTool] wrote, so both sides of the format are tested together. */
class RunCodeReportTest {
    private val threadFolder: File = Files.createTempDirectory("thread").toFile()
    private val context = ToolContext(threadFolder, OkHttpClient())

    private class ReplayRuntime(override val language: CodeLanguage, private val outcome: CodeRunOutcome) : CodeRuntime {
        override suspend fun run(job: CodeJob): CodeRunOutcome = outcome
    }

    private fun toolTextFor(outcome: CodeRunOutcome, language: CodeLanguage = CodeLanguage.PYTHON): String = runBlocking {
        val tool = RunCodeTool(listOf(ReplayRuntime(language, outcome)))
        val arguments = JsonObject(
            mapOf("language" to JsonPrimitive(language.argumentValue), "code" to JsonPrimitive("print(1)")),
        )
        tool.run(arguments, context).text
    }

    private fun finished(
        stdout: String = "",
        stderr: String = "",
        resultValue: String? = null,
        errorText: String? = null,
        outputFiles: List<OutputFile> = emptyList(),
    ) = CodeRunOutcome.Finished(stdout, stderr, resultValue, errorText, outputFiles)

    @Test
    fun everyPartOfAFinishedRunIsReadBack() {
        val text = toolTextFor(
            finished(
                stdout = "total 42\nResult: is not a marker here\n",
                stderr = "warning: slow\n",
                resultValue = "{\n  \"a\": 1\n}",
                outputFiles = listOf(
                    OutputFile("work/summary (1).csv", "a,b".encodeToByteArray()),
                    OutputFile("inbox/sales.csv", "x".encodeToByteArray()),
                ),
            ),
        )

        val report = RunCodeReport.parse(text)

        assertEquals("total 42\nResult: is not a marker here", report.printed)
        assertEquals("warning: slow", report.printedToStderr)
        assertEquals("{\n  \"a\": 1\n}", report.result)
        assertNull(report.error)
        assertEquals(listOf("work/summary (1).csv"), report.savedPaths)
        assertEquals(listOf(NotSavedFile("inbox/sales.csv", "only files under work/ and artifacts/ are saved")), report.notSaved)
        assertFalse(report.timedOut)
    }

    @Test
    fun theProgramsErrorIsSeparateFromItsOutput() {
        val traceback = "Traceback (most recent call last):\n  File \"main.py\", line 2, in <module>\nZeroDivisionError: division by zero"
        val text = toolTextFor(finished(stdout = "before\n", errorText = traceback))

        val report = RunCodeReport.parse(text)

        assertEquals("before", report.printed)
        assertEquals(traceback, report.error)
        assertNull(report.result)
        assertTrue(report.savedPaths.isEmpty())
    }

    @Test
    fun aRunWithNothingToShowIsEmpty() {
        val report = RunCodeReport.parse(toolTextFor(finished(), CodeLanguage.JAVASCRIPT))

        assertEquals("", report.printed)
        assertEquals("", report.printedToStderr)
        assertNull(report.result)
        assertNull(report.error)
    }

    @Test
    fun aTimeoutKeepsWhatWasPrinted() {
        val report = RunCodeReport.parse(toolTextFor(CodeRunOutcome.TimedOut(stdout = "step 1\n", stderr = "")))

        assertTrue(report.timedOut)
        assertTrue(report.error!!, report.error!!.startsWith("the program ran longer than 120 seconds"))
        assertEquals("step 1", report.printed)
    }

    @Test
    fun aToolErrorIsShownAsTheError() {
        val report = RunCodeReport.parse(toolTextFor(CodeRunOutcome.Unavailable("WebView too old")))

        assertTrue(report.error!!, report.error!!.startsWith("Python cannot run on this phone: WebView too old"))
        assertEquals("", report.printed)
    }

    @Test
    fun textInAnotherShapeIsShownAsPrinted() {
        val report = RunCodeReport.parse("something else\nentirely")

        assertEquals("something else\nentirely", report.printed)
        assertNull(report.error)
    }
}
