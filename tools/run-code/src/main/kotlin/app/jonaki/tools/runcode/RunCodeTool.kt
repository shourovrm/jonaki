package app.jonaki.tools.runcode

import app.jonaki.core.runtimeapi.CodeJob
import app.jonaki.core.runtimeapi.CodeLanguage
import app.jonaki.core.runtimeapi.CodeRunOutcome
import app.jonaki.core.runtimeapi.CodeRuntime
import app.jonaki.core.toolapi.Capability
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import app.jonaki.core.toolapi.stringArgument
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Runs a short JavaScript or Python program without internet access (D-014,
 * M8). The program sees the thread files the model names, at their thread
 * paths; afterwards new or changed files under work/ and artifacts/ are
 * saved back ([ThreadFileExchange]). The engines come in through the
 * constructor, one per language.
 */
class RunCodeTool(private val runtimes: List<CodeRuntime>) : Tool {
    override val name: String = "run_code"

    override val promptLine: String =
        "run_code: run a short JavaScript or Python program on the phone, without internet; " +
            "it reads the thread files you name and can save files to work/ and artifacts/"

    override val guidelines: List<String> = listOf(
        "run_code sees only the files you list in files, at the same paths (for example inbox/sales.csv). " +
            "New or changed files under work/ and artifacts/ are saved to the thread; changes anywhere else, " +
            "inbox/ included, are dropped.",
        "In JavaScript, print with console.log, read a listed file with files.read(path), write text with " +
            "files.write(path, text); the last expression's value is returned. No modules, no DOM, no fetch.",
        "In Python, use open() with the same paths and print(); numpy and pandas work when installed. " +
            "There is no pip at run time.",
        "A run stops after ${CODE_TIME_LIMIT.inWholeSeconds} seconds. Prefer JavaScript for small calculations; " +
            "Python starts slower.",
    )

    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("language") {
                put("type", "string")
                putJsonArray("enum") {
                    add(CodeLanguage.JAVASCRIPT.argumentValue)
                    add(CodeLanguage.PYTHON.argumentValue)
                }
            }
            putJsonObject("code") {
                put("type", "string")
                put("description", "The whole program")
            }
            putJsonObject("files") {
                put("type", "array")
                putJsonObject("items") { put("type", "string") }
                put("description", "Thread files or folders the program reads, for example [\"inbox/sales.csv\"]")
            }
        }
        putJsonArray("required") {
            add("language")
            add("code")
        }
    }

    override val sideEffect: SideEffect = SideEffect.CHANGES_THREAD_FOLDER

    /**
     * Empty although Python needs Capability.PYTHON: the need depends on the
     * language argument of each call, which this static set cannot express.
     * A missing Python is reported per call instead (CodeRunOutcome.NotInstalled).
     */
    override val requiredCapabilities: Set<Capability> = emptySet()

    /** The program's own limit plus time for Python to start and load its packages. */
    override val timeLimit: Duration = CODE_TIME_LIMIT + STARTUP_ALLOWANCE

    override suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput {
        val languageText = arguments.stringArgument("language")
            ?: return ToolOutput.error("argument language is missing", "Call run_code with language javascript or python.")
        val language = CodeLanguage.fromArgument(languageText)
            ?: return ToolOutput.error(
                "language \"$languageText\" is not supported",
                "Use javascript or python.",
            )
        val code = arguments.stringArgument("code")
        if (code.isNullOrBlank()) {
            return ToolOutput.error("argument code is missing", "Call run_code with the whole program in code.")
        }
        val runtime = runtimes.firstOrNull { candidate -> candidate.language == language }
            ?: return ToolOutput.error(
                "${language.displayName} cannot run in this app",
                "Use another language.",
            )

        val exchange = ThreadFileExchange(context.threadFolder)
        val inputFiles = when (val selection = withContext(Dispatchers.IO) { exchange.inputsFor(requestedPaths(arguments)) }) {
            is InputSelection.Refused -> return ToolOutput.error(
                selection.problem,
                "Check the paths with find_files and call run_code again.",
            )
            is InputSelection.Ready -> selection.files
        }

        val outcome = runtime.run(CodeJob(code, inputFiles, CODE_TIME_LIMIT))
        return report(language, outcome, exchange, context)
    }

    /** Models send a list, one path as text, or a list written as text; all three are accepted. */
    private fun requestedPaths(arguments: JsonObject): List<String> {
        val files = arguments["files"] ?: return emptyList()
        val listed = when {
            files is JsonArray -> files.mapNotNull { element -> (element as? JsonPrimitive)?.content }
            files is JsonPrimitive && files.content.trim().startsWith("[") -> pathsFromText(files.content)
            files is JsonPrimitive -> listOf(files.content)
            else -> emptyList()
        }
        return listed.map { path -> path.trim() }.filter { path -> path.isNotEmpty() }
    }

    private fun pathsFromText(text: String): List<String> {
        val parsed = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonArray ?: return listOf(text)
        return parsed.mapNotNull { element -> (element as? JsonPrimitive)?.content }
    }

    private suspend fun report(
        language: CodeLanguage,
        outcome: CodeRunOutcome,
        exchange: ThreadFileExchange,
        context: ToolContext,
    ): ToolOutput = when (outcome) {
        is CodeRunOutcome.Finished -> finishedReport(language, outcome, exchange, context)
        is CodeRunOutcome.TimedOut -> {
            val text = "Error: the program ran longer than ${CODE_TIME_LIMIT.inWholeSeconds} seconds and was stopped; " +
                "files it wrote were not saved. Make it do less, or split the work into several runs." +
                printedSection(outcome.stdout, outcome.stderr)
            ToolOutput(context.outputLimiter.limit(text, MAX_OUTPUT_CHARACTERS, name), isError = true)
        }
        is CodeRunOutcome.NotInstalled -> ToolOutput.error(
            "${language.displayName} is not installed",
            "Tell the user that ${language.displayName} is a ${megabytes(outcome.downloadBytes)} MB download " +
                "they can install in Settings, ${language.displayName}; until then use javascript if it can do the job.",
        )
        is CodeRunOutcome.MissingPackages -> ToolOutput.error(
            "the ${language.displayName} packages ${outcome.packageNames.joinToString(", ")} are not installed",
            "Tell the user they can install them in Settings, ${language.displayName}; " +
                "or solve the task without them.",
        )
        is CodeRunOutcome.Unavailable -> ToolOutput.error(
            "${language.displayName} cannot run on this phone: ${outcome.reason}",
            "Tell the user; use the other language if it can do the job.",
        )
    }

    private suspend fun finishedReport(
        language: CodeLanguage,
        outcome: CodeRunOutcome.Finished,
        exchange: ThreadFileExchange,
        context: ToolContext,
    ): ToolOutput {
        val saveReport = withContext(Dispatchers.IO) { exchange.save(outcome.outputFiles) }
        val errorText = outcome.errorText
        val failed = errorText != null
        val builder = StringBuilder()
        if (failed) {
            builder.append("${language.displayName} stopped with an error.")
        } else {
            builder.append("${language.displayName} finished.")
        }
        val printed = printedSection(outcome.stdout, outcome.stderr)
        builder.append(printed)
        if (outcome.resultValue != null) {
            builder.append("\nResult: ${outcome.resultValue}")
        }
        if (errorText != null) {
            builder.append("\nError:\n${errorText.trimEnd()}")
        }
        if (printed.isEmpty() && outcome.resultValue == null && !failed) {
            builder.append("\nNo output and no result.")
        }
        builder.append(filesSection(saveReport))
        if (failed) {
            builder.append("\nFix the program and call run_code again.")
        }
        val text = context.outputLimiter.limit(builder.toString(), MAX_OUTPUT_CHARACTERS, name)
        return ToolOutput(text, isError = failed)
    }

    private fun printedSection(stdout: String, stderr: String): String {
        val builder = StringBuilder()
        if (stdout.isNotEmpty()) {
            builder.append("\nPrinted:\n${stdout.trimEnd()}")
        }
        if (stderr.isNotEmpty()) {
            builder.append("\nPrinted to stderr:\n${stderr.trimEnd()}")
        }
        return builder.toString()
    }

    private fun filesSection(saveReport: SaveReport): String {
        val builder = StringBuilder()
        for (saved in saveReport.saved) {
            val state = if (saved.wasNew) "new" else "replaced"
            builder.append("\nSaved: ${saved.relativePath} ($state, ${saved.sizeBytes} bytes)")
        }
        for (refused in saveReport.refused) {
            builder.append("\nNot saved: ${refused.relativePath} (${refused.reason})")
        }
        return builder.toString()
    }

    private fun megabytes(bytes: Long): Long = (bytes / BYTES_PER_MEGABYTE).roundToLong()

    companion object {
        val CODE_TIME_LIMIT: Duration = 120.seconds
        private val STARTUP_ALLOWANCE: Duration = 60.seconds
        private const val MAX_OUTPUT_CHARACTERS = 20_000
        private const val BYTES_PER_MEGABYTE = 1024.0 * 1024.0
    }
}
