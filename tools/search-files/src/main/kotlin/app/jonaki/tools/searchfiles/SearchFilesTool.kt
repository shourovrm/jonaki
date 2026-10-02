package app.jonaki.tools.searchfiles

import app.jonaki.core.toolapi.Capability
import app.jonaki.core.toolapi.GlobFilter
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import app.jonaki.core.toolapi.booleanArgument
import app.jonaki.core.toolapi.looksBinary
import app.jonaki.core.toolapi.stringArgument
import java.io.File
import java.util.regex.PatternSyntaxException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Finds lines matching a regular expression in the thread's text files. */
class SearchFilesTool : Tool {
    override val name: String = "search_files"
    override val promptLine: String = "search_files: find lines matching a regular expression in the thread's files"
    override val guidelines: List<String> = emptyList()
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("pattern") {
                put("type", "string")
                put("description", "Regular expression, for example invoice|bill")
            }
            putJsonObject("path") {
                put("type", "string")
                put("description", "File or folder to search, relative to the thread folder. Default: everything.")
            }
            putJsonObject("glob") {
                put("type", "string")
                put("description", "Only search files matching this glob, for example *.md")
            }
            putJsonObject("ignore_case") { put("type", "boolean") }
        }
        putJsonArray("required") { add(JsonPrimitive("pattern")) }
    }
    override val sideEffect: SideEffect = SideEffect.READ_ONLY
    override val requiredCapabilities: Set<Capability> = emptySet()
    override val timeLimit: Duration = 30.seconds

    override suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput = withContext(Dispatchers.IO) {
        val pattern = arguments.stringArgument("pattern")
            ?: return@withContext ToolOutput.error("argument pattern is missing", "Call search_files with a pattern.")
        val path = arguments.stringArgument("path") ?: "."
        val target = context.paths.resolve(path)
            ?: return@withContext ToolOutput.error("$path is outside the thread folder", "Search inside the thread folder.")
        if (!target.exists()) {
            return@withContext ToolOutput.error("$path does not exist", "Use find_files to list the files.")
        }
        val ignoreCase = arguments.booleanArgument("ignore_case") ?: false
        val regex = try {
            if (ignoreCase) Regex(pattern, RegexOption.IGNORE_CASE) else Regex(pattern)
        } catch (exception: PatternSyntaxException) {
            return@withContext ToolOutput.error(
                "pattern $pattern is not a valid regular expression (${exception.description})",
                "Escape special characters such as ( [ . with a backslash.",
            )
        }
        val globFilter = arguments.stringArgument("glob")?.let(::GlobFilter)

        val matchLines = mutableListOf<String>()
        var totalMatches = 0
        for (file in filesToSearch(target, globFilter)) {
            ensureActive()
            file.useLines { lines ->
                for ((index, line) in lines.withIndex()) {
                    if (regex.containsMatchIn(line)) {
                        totalMatches += 1
                        if (matchLines.size < MAX_MATCHES) {
                            matchLines += "${context.paths.relativePath(file)}:${index + 1}: ${shortened(line.trim())}"
                        }
                    }
                }
            }
        }
        if (totalMatches == 0) {
            return@withContext ToolOutput.success("No lines match $pattern in $path.")
        }
        val listing = matchLines.joinToString("\n")
        val text = if (totalMatches > MAX_MATCHES) {
            "$listing\n\n[Showing $MAX_MATCHES of $totalMatches matching lines. Narrow the pattern, path or glob.]"
        } else {
            listing
        }
        ToolOutput.success(context.outputLimiter.limit(text, MAX_CHARACTERS, name))
    }

    private fun filesToSearch(target: File, globFilter: GlobFilter?): List<File> {
        if (target.isFile) {
            return listOf(target)
        }
        return target.walkTopDown()
            .filter { file -> file.isFile && file.length() <= MAX_FILE_BYTES }
            .filter { file -> globFilter == null || globFilter.matches(file.relativeTo(target).toPath()) }
            .filter { file -> !looksBinary(file) }
            .sortedBy { file -> file.path }
            .toList()
    }

    private fun shortened(line: String): String {
        if (line.length <= MAX_LINE_CHARACTERS) {
            return line
        }
        return line.substring(0, MAX_LINE_CHARACTERS) + " …"
    }

    private companion object {
        const val MAX_MATCHES = 100
        const val MAX_LINE_CHARACTERS = 300
        const val MAX_CHARACTERS = 20_000
        const val MAX_FILE_BYTES = 5L * 1024 * 1024
    }
}
