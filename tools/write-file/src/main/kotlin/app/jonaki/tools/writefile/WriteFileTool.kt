package app.jonaki.tools.writefile

import app.jonaki.core.toolapi.Capability
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
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

/** Creates or replaces a whole text file in the thread folder. */
class WriteFileTool : Tool {
    override val name: String = "write_file"
    override val promptLine: String = "write_file: create a text file in the thread folder, or replace all of it"
    override val guidelines: List<String> = listOf(
        "Use write_file for new files and full rewrites; use edit_file to change part of a file.",
    )
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "Path relative to the thread folder; missing folders are created")
            }
            putJsonObject("content") {
                put("type", "string")
                put("description", "The complete file content")
            }
        }
        putJsonArray("required") {
            add(JsonPrimitive("path"))
            add(JsonPrimitive("content"))
        }
    }
    override val sideEffect: SideEffect = SideEffect.CHANGES
    override val requiredCapabilities: Set<Capability> = emptySet()
    override val timeLimit: Duration = 10.seconds

    override suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput = withContext(Dispatchers.IO) {
        val path = arguments.stringArgument("path")
            ?: return@withContext ToolOutput.error("argument path is missing", "Call write_file with a path.")
        val content = arguments.stringArgument("content")
            ?: return@withContext ToolOutput.error(
                "argument content is missing",
                "Call write_file with the complete file content.",
            )
        val file = context.paths.resolve(path)
            ?: return@withContext ToolOutput.error(
                "$path is outside the thread folder",
                "Use a path inside the thread folder, for example work/notes.md.",
            )
        if (file.isDirectory) {
            return@withContext ToolOutput.error("$path is a folder", "Give a file name inside it, for example $path/notes.md.")
        }
        val existed = file.exists()
        file.parentFile?.mkdirs()
        file.writeText(content)

        val lineCount = if (content.isEmpty()) 0 else content.removeSuffix("\n").split("\n").size
        val verb = if (existed) "Replaced" else "Created"
        ToolOutput.success("$verb ${context.paths.relativePath(file)}: $lineCount lines, ${content.length} characters.")
    }
}
