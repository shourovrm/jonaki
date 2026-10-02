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
import java.util.Locale
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

    /** Only the languages that are switched on are named, so a switched-off one costs no tokens (M8). */
    private val languages: List<CodeLanguage> = CodeLanguage.entries.filter { language ->
        runtimes.any { runtime -> runtime.language == language }
    }

    private val languageNames: String = languages.joinToString(" or ") { language -> language.displayName }

    override val promptLine: String =
        "run_code: run a short $languageNames program on the phone, without internet; " +
            "it reads the thread files you name and can save files to work/ and artifacts/"

    override val guidelines: List<String> = buildList {
        add(
            "run_code sees only the files you list in files, at the same paths (for example inbox/sales.csv). " +
                "New or changed files under work/ and artifacts/ are saved to the thread; changes anywhere else, " +
                "inbox/ included, are dropped.",
        )
        if (CodeLanguage.JAVASCRIPT in languages) {
            add(
                "In JavaScript, print with console.log, read a listed file with files.read(path), write text with " +
                    "files.write(path, text); the last expression's value is returned. No modules, no DOM, no fetch.",
            )
        }
        if (CodeLanguage.PYTHON in languages) {
            add(
                "In Python, use open() with the same paths and print(); numpy and pandas work when installed. " +
                    "There is no pip at run time.",
            )
        }
        if (languages.size > 1) {
            add(
                "A run stops after ${CODE_TIME_LIMIT.inWholeSeconds} seconds. Prefer JavaScript for small " +
                    "calculations; Python starts slower.",
            )
        } else {
            add("A run stops after ${CODE_TIME_LIMIT.inWholeSeconds} seconds.")
        }
    }

    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("language") {
                put("type", "string")
                putJsonArray("enum") {
                    for (language in languages) {
                        add(language.argumentValue)
                    }
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
            val text = "${RunCodeReport.TIMED_OUT_START} ${CODE_TIME_LIMIT.inWholeSeconds} seconds and was stopped; " +
                "files it wrote were not saved. Make it do less, or split the work into several runs." +
                printedSection(outcome.stdout, outcome.stderr)
            ToolOutput(context.outputLimiter.limit(text, MAX_OUTPUT_CHARACTERS, name), isError = true)
        }
        is CodeRunOutcome.NotInstalled -> ToolOutput.error(
            InstallNeeds.notInstalledText(language),
            "Tell the user that ${language.displayName} is a ${megabytes(outcome.downloadBytes)} download: the chat " +
                "shows them an Install button, and it is also in Settings > ${language.displayName}. After installing " +
                "they can ask again." + otherLanguageHint(language),
        )
        is CodeRunOutcome.MissingPackages -> ToolOutput.error(
            InstallNeeds.missingPackagesText(language, outcome.packageNames),
            "The chat shows the user an Install button for them, and they are also in Settings > " +
                "${language.displayName}. After installing they can ask again; or solve the task without them.",
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
            builder.append("${language.displayName} ${RunCodeReport.STOPPED_WITH_ERROR}")
        } else {
            builder.append("${language.displayName} ${RunCodeReport.FINISHED}")
        }
        val printed = printedSection(outcome.stdout, outcome.stderr)
        builder.append(printed)
        if (outcome.resultValue != null) {
            builder.append("${RunCodeReport.RESULT}${outcome.resultValue}")
        }
        if (errorText != null) {
            builder.append("${RunCodeReport.ERROR}${errorText.trimEnd()}")
        }
        if (printed.isEmpty() && outcome.resultValue == null && !failed) {
            builder.append("\n${RunCodeReport.NO_OUTPUT}")
        }
        builder.append(filesSection(saveReport))
        if (failed) {
            builder.append("\n${RunCodeReport.FIX_AND_RETRY}")
        }
        val text = context.outputLimiter.limit(builder.toString(), MAX_OUTPUT_CHARACTERS, name)
        return ToolOutput(text, isError = failed)
    }

    private fun printedSection(stdout: String, stderr: String): String {
        val builder = StringBuilder()
        if (stdout.isNotEmpty()) {
            builder.append("${RunCodeReport.PRINTED}${stdout.trimEnd()}")
        }
        if (stderr.isNotEmpty()) {
            builder.append("${RunCodeReport.PRINTED_TO_STDERR}${stderr.trimEnd()}")
        }
        return builder.toString()
    }

    private fun filesSection(saveReport: SaveReport): String {
        val builder = StringBuilder()
        for (saved in saveReport.saved) {
            val state = if (saved.wasNew) "new" else "replaced"
            builder.append("\n${RunCodeReport.SAVED}${saved.relativePath} ($state, ${saved.sizeBytes} bytes)")
        }
        for (refused in saveReport.refused) {
            builder.append("\n${RunCodeReport.NOT_SAVED}${refused.relativePath} (${refused.reason})")
        }
        return builder.toString()
    }

    /** "13.5 MB", the size the install card and Settings show. */
    private fun megabytes(bytes: Long): String = String.format(Locale.ENGLISH, "%.1f MB", bytes / BYTES_PER_MEGABYTE)

    private fun otherLanguageHint(language: CodeLanguage): String {
        val other = languages.firstOrNull { candidate -> candidate != language } ?: return ""
        return " Until then use ${other.argumentValue} if it can do the job."
    }

    companion object {
        val CODE_TIME_LIMIT: Duration = 120.seconds
        private val STARTUP_ALLOWANCE: Duration = 60.seconds
        private const val MAX_OUTPUT_CHARACTERS = 20_000
        private const val BYTES_PER_MEGABYTE = 1_000_000.0
    }
}
