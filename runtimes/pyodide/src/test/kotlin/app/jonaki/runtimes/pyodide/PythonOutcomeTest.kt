package app.jonaki.runtimes.pyodide

import app.jonaki.core.runtimeapi.CodeRunOutcome
import app.jonaki.core.runtimeapi.OutputFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonOutcomeTest {
    private val files = listOf(OutputFile("work/summary.csv", "x".toByteArray()))

    @Test
    fun aFinishedRunKeepsPrintedTextAndFiles() {
        val outcome = PythonOutcome.from("""{"result": "3", "error": null}""", "out\n", "err\n", files)

        val finished = outcome as CodeRunOutcome.Finished
        assertEquals("3", finished.resultValue)
        assertNull(finished.errorText)
        assertEquals("out\n", finished.stdout)
        assertEquals("err\n", finished.stderr)
        assertEquals(files, finished.outputFiles)
    }

    @Test
    fun aPythonExceptionIsTheProgramsError() {
        val outcome = PythonOutcome.from(
            """{"result": null, "error": "Traceback (most recent call last):\nValueError: no"}""",
            "",
            "",
            emptyList(),
        ) as CodeRunOutcome.Finished

        assertTrue(outcome.errorText!!.contains("ValueError: no"))
        assertNull(outcome.resultValue)
    }

    @Test
    fun missingPackagesAreNamed() {
        val outcome = PythonOutcome.from("""{"missingPackages": ["scipy"]}""", "", "", emptyList())

        assertEquals(CodeRunOutcome.MissingPackages(listOf("scipy")), outcome)
    }

    @Test
    fun aSetupErrorMeansPythonCouldNotRun() {
        val outcome = PythonOutcome.from("""{"setupError": "the WebView renderer crashed"}""", "", "", emptyList())

        val unavailable = outcome as CodeRunOutcome.Unavailable
        assertTrue(unavailable.reason, unavailable.reason.contains("the WebView renderer crashed"))
    }
}
