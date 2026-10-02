package app.jonaki.tools.readfile

import app.jonaki.core.toolapi.Capability
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import app.jonaki.core.toolapi.intArgument
import app.jonaki.core.toolapi.looksBinary
import app.jonaki.core.toolapi.stringArgument
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Reads a text file from the thread folder, a window of lines at a time. */
class ReadFileTool : Tool {
    override val name: String = "read_file"
    override val promptLine: String = "read_file: read a text file in the thread folder (offset and limit in lines)"
    override val guidelines: List<String> = listOf(
        "Read a file before you edit it, and continue long files with the offset the notice names.",
    )
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "Path relative to the thread folder, for example work/notes.md")
            }
            putJsonObject("offset") {
                put("type", "integer")
                put("description", "First line to show, counting from 1. Default 1.")
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "Most lines to show. Default $DEFAULT_LINE_LIMIT.")
            }
        }
        putJsonArray("required") { add(JsonPrimitive("path")) }
    }
    override val sideEffect: SideEffect = SideEffect.READ_ONLY
    override val requiredCapabilities: Set<Capability> = emptySet()
    override val timeLimit: Duration = 10.seconds

    override suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput = withContext(Dispatchers.IO) {
        val path = arguments.stringArgument("path")
            ?: return@withContext ToolOutput.error("argument path is missing", "Call read_file with a path.")
        val file = context.paths.resolve(path)
            ?: return@withContext ToolOutput.error(
                "$path is outside the thread folder",
                "Use a path inside the thread folder, for example work/notes.md.",
            )
        if (!file.exists()) {
            return@withContext ToolOutput.error("$path does not exist", "Use find_files to list the files.")
        }
        if (file.isDirectory) {
            return@withContext ToolOutput.error(
                "$path is a folder",
                "Use find_files path=\"$path\" to list its files.",
            )
        }
        if (looksBinary(file)) {
            return@withContext ToolOutput.error("$path is a binary file", "read_file reads text files only.")
        }

        val offset = (arguments.intArgument("offset") ?: 1).coerceAtLeast(1)
        val limit = (arguments.intArgument("limit") ?: DEFAULT_LINE_LIMIT).coerceIn(1, MAX_LINE_LIMIT)
        val allLines = file.readText().removeSuffix("\n").split("\n").map { line -> line.removeSuffix("\r") }
        val totalLines = if (file.length() == 0L) 0 else allLines.size

        if (totalLines == 0) {
            return@withContext ToolOutput.success("($path is empty)")
        }
        if (offset > totalLines) {
            return@withContext ToolOutput.error(
                "offset $offset is past the end; $path has $totalLines lines",
                "Use an offset between 1 and $totalLines.",
            )
        }
        ToolOutput.success(window(path, allLines, offset, limit))
    }

    private fun window(path: String, allLines: List<String>, offset: Int, limit: Int): String {
        val shown = StringBuilder()
        var lastShownLine = offset - 1
        val lastAllowedLine = minOf(allLines.size, offset + limit - 1)
        for (lineNumber in offset..lastAllowedLine) {
            val line = shortened(allLines[lineNumber - 1])
            // Stop before the character budget, but always show at least one line.
            if (lastShownLine >= offset && shown.length + line.length + 1 > MAX_CHARACTERS) {
                break
            }
            shown.append(line).append('\n')
            lastShownLine = lineNumber
        }
        val text = shown.toString().removeSuffix("\n")
        if (lastShownLine >= allLines.size) {
            return text
        }
        return "$text\n\n[Showing lines $offset-$lastShownLine of ${allLines.size}. " +
            "Use read_file path=\"$path\" offset=${lastShownLine + 1} to continue.]"
    }

    private fun shortened(line: String): String {
        if (line.length <= MAX_LINE_CHARACTERS) {
            return line
        }
        return line.substring(0, MAX_LINE_CHARACTERS) + " … [line cut at $MAX_LINE_CHARACTERS characters]"
    }

    private companion object {
        const val DEFAULT_LINE_LIMIT = 400
        const val MAX_LINE_LIMIT = 2_000
        const val MAX_LINE_CHARACTERS = 2_000
        const val MAX_CHARACTERS = 30_000
    }
}
