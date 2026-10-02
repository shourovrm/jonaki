package app.jonaki.tools.findfiles

import app.jonaki.core.toolapi.Capability
import app.jonaki.core.toolapi.GlobFilter
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import app.jonaki.core.toolapi.stringArgument
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Lists files in the thread folder whose path matches a glob pattern. */
class FindFilesTool : Tool {
    override val name: String = "find_files"
    override val promptLine: String = "find_files: list files in the thread folder by glob pattern, e.g. *.md"
    override val guidelines: List<String> = emptyList()
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("pattern") {
                put("type", "string")
                put("description", "Glob such as *.csv or work/**/*.md. Default: every file.")
            }
            putJsonObject("path") {
                put("type", "string")
                put("description", "Folder to search, relative to the thread folder. Default: the whole thread.")
            }
        }
    }
    override val sideEffect: SideEffect = SideEffect.READ_ONLY
    override val requiredCapabilities: Set<Capability> = emptySet()
    override val timeLimit: Duration = 30.seconds

    override suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput = withContext(Dispatchers.IO) {
        val pattern = arguments.stringArgument("pattern") ?: "**"
        val path = arguments.stringArgument("path") ?: "."
        val folder = context.paths.resolve(path)
            ?: return@withContext ToolOutput.error("$path is outside the thread folder", "Use a folder inside it.")
        if (!folder.isDirectory) {
            return@withContext ToolOutput.error("$path is not a folder", "Use find_files without a path to list everything.")
        }
        val filter = try {
            GlobFilter(pattern)
        } catch (exception: IllegalArgumentException) {
            return@withContext ToolOutput.error("pattern $pattern is not a valid glob", "Use a pattern such as *.md.")
        }

        val matches = mutableListOf<String>()
        for (file in folder.walkTopDown().filter(File::isFile)) {
            ensureActive()
            val pathInFolder = file.relativeTo(folder).toPath()
            if (filter.matches(pathInFolder)) {
                matches += context.paths.relativePath(file)
            }
        }
        if (matches.isEmpty()) {
            return@withContext ToolOutput.success("No files match $pattern in $path.")
        }
        matches.sort()
        val shown = matches.take(MAX_RESULTS)
        val listing = shown.joinToString("\n")
        if (matches.size <= MAX_RESULTS) {
            return@withContext ToolOutput.success(listing)
        }
        ToolOutput.success(
            "$listing\n\n[Showing $MAX_RESULTS of ${matches.size} files. Narrow the pattern or the path to see the rest.]",
        )
    }

    private companion object {
        const val MAX_RESULTS = 200
    }
}
