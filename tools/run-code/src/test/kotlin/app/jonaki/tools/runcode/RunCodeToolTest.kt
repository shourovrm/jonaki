package app.jonaki.tools.runcode

import app.jonaki.core.runtimeapi.CodeJob
import app.jonaki.core.runtimeapi.CodeLanguage
import app.jonaki.core.runtimeapi.CodeRunOutcome
import app.jonaki.core.runtimeapi.CodeRuntime
import app.jonaki.core.runtimeapi.OutputFile
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import java.io.File
import java.nio.file.Files
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunCodeToolTest {
    private val threadFolder: File = Files.createTempDirectory("thread").toFile()
    private val context = ToolContext(threadFolder, OkHttpClient())

    /** Replays one outcome and remembers the job it was given. */
    private class FakeRuntime(
        override val language: CodeLanguage,
        private val outcome: CodeRunOutcome,
    ) : CodeRuntime {
        var lastJob: CodeJob? = null

        override suspend fun run(job: CodeJob): CodeRunOutcome {
            lastJob = job
            return outcome
        }
    }

    private fun finished(
        stdout: String = "",
        stderr: String = "",
        resultValue: String? = null,
        errorText: String? = null,
        outputFiles: List<OutputFile> = emptyList(),
    ) = CodeRunOutcome.Finished(stdout, stderr, resultValue, errorText, outputFiles)

    private fun run(tool: RunCodeTool, vararg arguments: Pair<String, JsonElement>): ToolOutput = runBlocking {
        tool.run(JsonObject(arguments.toMap()), context)
    }

    private fun text(value: String) = JsonPrimitive(value)

    private fun threadFile(path: String, content: String) {
        val file = File(threadFolder, path)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    @Test
    fun changesOnlyTheThreadFolder() {
        assertEquals(SideEffect.CHANGES_THREAD_FOLDER, RunCodeTool(emptyList()).sideEffect)
    }

    @Test
    fun runsTheCodeWithTheTimeLimitAndShowsWhatItPrinted() {
        val runtime = FakeRuntime(CodeLanguage.JAVASCRIPT, finished(stdout = "hello\n", resultValue = "42"))
        val tool = RunCodeTool(listOf(runtime))

        val output = run(tool, "language" to text("javascript"), "code" to text("console.log('hello'); 6 * 7"))

        assertFalse(output.text, output.isError)
        assertEquals("console.log('hello'); 6 * 7", runtime.lastJob!!.code)
        assertEquals(120.seconds, runtime.lastJob!!.timeLimit)
        assertTrue(output.text, output.text.startsWith("JavaScript finished."))
        assertTrue(output.text, output.text.contains("Printed:\nhello"))
        assertTrue(output.text, output.text.contains("Result: 42"))
    }

    @Test
    fun saysSoWhenNothingWasPrinted() {
        val tool = RunCodeTool(listOf(FakeRuntime(CodeLanguage.JAVASCRIPT, finished())))

        val output = run(tool, "language" to text("javascript"), "code" to text("let a = 1;"))

        assertTrue(output.text, output.text.contains("No output and no result."))
    }

    @Test
    fun choosesTheRuntimeForTheLanguage() {
        val javaScript = FakeRuntime(CodeLanguage.JAVASCRIPT, finished())
        val python = FakeRuntime(CodeLanguage.PYTHON, finished())
        val tool = RunCodeTool(listOf(javaScript, python))

        run(tool, "language" to text("Python"), "code" to text("print(1)"))

        assertNull(javaScript.lastJob)
        assertEquals("print(1)", python.lastJob!!.code)
    }

    @Test
    fun passesTheNamedFilesAsAList() {
        threadFile("inbox/sales.csv", "a,b")
        threadFile("inbox/notes.txt", "x")
        val runtime = FakeRuntime(CodeLanguage.PYTHON, finished())
        val tool = RunCodeTool(listOf(runtime))

        run(
            tool,
            "language" to text("python"),
            "code" to text("pass"),
            "files" to JsonArray(listOf(text("inbox/sales.csv"), text("inbox/notes.txt"))),
        )

        assertEquals(listOf("inbox/sales.csv", "inbox/notes.txt"), runtime.lastJob!!.inputFiles.map { it.relativePath })
    }

    @Test
    fun acceptsOnePathAsTextAndAListSentAsText() {
        threadFile("inbox/sales.csv", "a,b")
        threadFile("inbox/notes.txt", "x")
        val runtime = FakeRuntime(CodeLanguage.PYTHON, finished())
        val tool = RunCodeTool(listOf(runtime))

        run(tool, "language" to text("python"), "code" to text("pass"), "files" to text("inbox/sales.csv"))
        assertEquals(listOf("inbox/sales.csv"), runtime.lastJob!!.inputFiles.map { it.relativePath })

        run(
            tool,
            "language" to text("python"),
            "code" to text("pass"),
            "files" to text("[\"inbox/sales.csv\", \"inbox/notes.txt\"]"),
        )
        assertEquals(listOf("inbox/sales.csv", "inbox/notes.txt"), runtime.lastJob!!.inputFiles.map { it.relativePath })
    }

    @Test
    fun refusesAMissingFileWithoutRunning() {
        val runtime = FakeRuntime(CodeLanguage.PYTHON, finished())
        val tool = RunCodeTool(listOf(runtime))

        val output = run(
            tool,
            "language" to text("python"),
            "code" to text("pass"),
            "files" to JsonArray(listOf(text("inbox/missing.csv"))),
        )

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("inbox/missing.csv does not exist"))
        assertTrue(output.text, output.text.contains("find_files"))
        assertNull(runtime.lastJob)
    }

    @Test
    fun explainsAMissingOrUnknownLanguage() {
        val tool = RunCodeTool(listOf(FakeRuntime(CodeLanguage.JAVASCRIPT, finished())))

        val missing = run(tool, "code" to text("1"))
        val unknown = run(tool, "language" to text("ruby"), "code" to text("1"))

        assertTrue(missing.isError)
        assertTrue(missing.text, missing.text.contains("language is missing"))
        assertTrue(unknown.isError)
        assertTrue(unknown.text, unknown.text.contains("\"ruby\""))
        assertTrue(unknown.text, unknown.text.contains("javascript or python"))
    }

    @Test
    fun explainsMissingCode() {
        val tool = RunCodeTool(listOf(FakeRuntime(CodeLanguage.JAVASCRIPT, finished())))

        val output = run(tool, "language" to text("javascript"), "code" to text("  "))

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("code is missing"))
    }

    @Test
    fun saysWhenALanguageHasNoRuntime() {
        val tool = RunCodeTool(listOf(FakeRuntime(CodeLanguage.JAVASCRIPT, finished())))

        val output = run(tool, "language" to text("python"), "code" to text("print(1)"))

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("Python cannot run in this app"))
    }

    @Test
    fun savesNewFilesAndReportsThem() {
        val runtime = FakeRuntime(
            CodeLanguage.PYTHON,
            finished(
                outputFiles = listOf(
                    OutputFile("work/summary.csv", "north,15\n".toByteArray()),
                    OutputFile("inbox/sales.csv", "edited".toByteArray()),
                ),
            ),
        )
        val tool = RunCodeTool(listOf(runtime))

        val output = run(tool, "language" to text("python"), "code" to text("..."))

        assertEquals("north,15\n", File(threadFolder, "work/summary.csv").readText())
        assertFalse(File(threadFolder, "inbox/sales.csv").exists())
        assertTrue(output.text, output.text.contains("Saved: work/summary.csv (new, 9 bytes)"))
        assertTrue(
            output.text,
            output.text.contains("Not saved: inbox/sales.csv (only files under work/ and artifacts/ are saved)"),
        )
    }

    @Test
    fun aProgramErrorIsAnErrorThatStillSavesFiles() {
        val runtime = FakeRuntime(
            CodeLanguage.PYTHON,
            finished(
                stdout = "step 1\n",
                errorText = "Traceback (most recent call last):\nZeroDivisionError: division by zero",
                outputFiles = listOf(OutputFile("work/partial.txt", "x".toByteArray())),
            ),
        )
        val tool = RunCodeTool(listOf(runtime))

        val output = run(tool, "language" to text("python"), "code" to text("1/0"))

        assertTrue(output.isError)
        assertTrue(output.text, output.text.startsWith("Python stopped with an error."))
        assertTrue(output.text, output.text.contains("ZeroDivisionError"))
        assertTrue(output.text, output.text.contains("step 1"))
        assertTrue(File(threadFolder, "work/partial.txt").exists())
    }

    @Test
    fun aTimeoutKeepsWhatWasPrinted() {
        val runtime = FakeRuntime(CodeLanguage.PYTHON, CodeRunOutcome.TimedOut(stdout = "tick\n", stderr = ""))
        val tool = RunCodeTool(listOf(runtime))

        val output = run(tool, "language" to text("python"), "code" to text("while True: print('tick')"))

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("ran longer than 120 seconds"))
        assertTrue(output.text, output.text.contains("tick"))
    }

    @Test
    fun saysHowToInstallPythonWhenItIsMissing() {
        val runtime = FakeRuntime(CodeLanguage.PYTHON, CodeRunOutcome.NotInstalled(downloadBytes = 13_600_000))
        val tool = RunCodeTool(listOf(runtime))

        val output = run(tool, "language" to text("python"), "code" to text("print(1)"))

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("Python is not installed"))
        assertTrue(output.text, output.text.contains("13.6 MB"))
        assertTrue(output.text, output.text.contains("Settings > Python"))
        assertEquals(InstallNeed(emptyList()), InstallNeeds.of(output.text))
    }

    @Test
    fun suggestsJavaScriptOnlyWhenItIsOn() {
        val python = FakeRuntime(CodeLanguage.PYTHON, CodeRunOutcome.NotInstalled(downloadBytes = 13_532_188))
        val javaScript = FakeRuntime(CodeLanguage.JAVASCRIPT, finished())

        val alone = run(RunCodeTool(listOf(python)), "language" to text("python"), "code" to text("print(1)"))
        val withJavaScript = run(RunCodeTool(listOf(javaScript, python)), "language" to text("python"), "code" to text("print(1)"))

        assertFalse(alone.text, alone.text.contains("javascript"))
        assertTrue(withJavaScript.text, withJavaScript.text.contains("use javascript"))
    }

    @Test
    fun namesMissingPackages() {
        val runtime = FakeRuntime(
            CodeLanguage.PYTHON,
            CodeRunOutcome.MissingPackages(listOf("pandas", "numpy")),
        )
        val tool = RunCodeTool(listOf(runtime))

        val output = run(tool, "language" to text("python"), "code" to text("import pandas"))

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("pandas, numpy"))
        assertTrue(output.text, output.text.contains("Settings > Python"))
        assertEquals(InstallNeed(listOf("pandas", "numpy")), InstallNeeds.of(output.text))
    }

    @Test
    fun namesMissingDocumentsPackagesAsAnInstallNeed() {
        val names = listOf("python-docx", "lxml", "XlsxWriter")
        val runtime = FakeRuntime(CodeLanguage.PYTHON, CodeRunOutcome.MissingPackages(names))

        val output = run(RunCodeTool(listOf(runtime)), "language" to text("python"), "code" to text("import docx"))

        assertEquals(InstallNeed(names), InstallNeeds.of(output.text))
    }

    @Test
    fun pythonGuidelinesMentionTheDocumentsAddOnInUnder200Characters() {
        val tool = RunCodeTool(listOf(FakeRuntime(CodeLanguage.PYTHON, finished())))

        val line = tool.guidelines.single { guideline -> guideline.contains("documents add-on") }

        assertTrue(line, line.contains("jonaki_docs"))
        val addedSentence = line.substringAfter("There is no pip at run time. ")
        assertTrue(addedSentence, addedSentence.length < 200)
    }

    @Test
    fun offersOnlyTheLanguagesItHasRuntimesFor() {
        val pythonOnly = RunCodeTool(listOf(FakeRuntime(CodeLanguage.PYTHON, finished())))
        val both = RunCodeTool(
            listOf(FakeRuntime(CodeLanguage.JAVASCRIPT, finished()), FakeRuntime(CodeLanguage.PYTHON, finished())),
        )

        val pythonEnum = pythonOnly.parameterSchema.toString()
        assertTrue(pythonEnum, pythonEnum.contains("\"enum\":[\"python\"]"))
        assertTrue(pythonOnly.promptLine, pythonOnly.promptLine.contains("short Python program"))
        assertTrue(pythonOnly.guidelines.none { line -> line.contains("console.log") })
        assertTrue(both.parameterSchema.toString().contains("\"enum\":[\"javascript\",\"python\"]"))
        assertTrue(both.promptLine, both.promptLine.contains("JavaScript or Python"))
    }

    @Test
    fun otherResultsNeedNoInstall() {
        assertNull(InstallNeeds.of("Python finished.\nPrinted:\n1"))
        assertNull(InstallNeeds.of("Error: Python cannot run on this phone: Python's files are damaged"))
        assertNull(InstallNeeds.of("Error: JavaScript is not installed. Try again."))
    }

    @Test
    fun passesOnWhyARuntimeCannotRun() {
        val runtime = FakeRuntime(CodeLanguage.JAVASCRIPT, CodeRunOutcome.Unavailable("Android System WebView is too old"))
        val tool = RunCodeTool(listOf(runtime))

        val output = run(tool, "language" to text("javascript"), "code" to text("1"))

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("Android System WebView is too old"))
    }

    @Test
    fun longOutputIsCutAndSavedWhole() {
        val longOutput = (1..5_000).joinToString("\n") { "line $it" }
        val tool = RunCodeTool(listOf(FakeRuntime(CodeLanguage.JAVASCRIPT, finished(stdout = longOutput))))

        val output = run(tool, "language" to text("javascript"), "code" to text("..."))

        assertTrue(output.text, output.text.contains("Output truncated"))
        assertTrue(File(threadFolder, "work/tool-output").listFiles()!!.isNotEmpty())
    }
}
