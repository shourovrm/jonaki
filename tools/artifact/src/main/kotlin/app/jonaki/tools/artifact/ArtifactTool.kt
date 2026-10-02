package app.jonaki.tools.artifact

import app.jonaki.core.toolapi.ArtifactVersions
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

/**
 * Shows a finished HTML file from artifacts/ to the user and keeps a
 * version of it (D-018, D-047). The file itself is written with write_file
 * or edit_file; this tool only presents it.
 */
class ArtifactTool : Tool {
    override val name: String = "artifact"
    override val promptLine: String = "artifact: show an HTML file from artifacts/ to the user in a viewer, keeping a version"
    override val guidelines: List<String> = listOf(
        "After writing or changing an HTML report or deck in artifacts/, call artifact with its path so the user can open it.",
        "The viewer has no network: put CSS and scripts inside the file; for charts use <script src=\"lib/chart.js\"></script> (Chart.js 4).",
    )
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "Path of an .html file inside artifacts/, for example artifacts/report.html")
            }
        }
        putJsonArray("required") { add(JsonPrimitive("path")) }
    }

    // It writes only version copies inside the thread's own artifacts/ folder.
    override val sideEffect: SideEffect = SideEffect.CHANGES_APP_DATA
    override val requiredCapabilities: Set<Capability> = emptySet()
    override val timeLimit: Duration = 10.seconds

    override suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput = withContext(Dispatchers.IO) {
        val path = arguments.stringArgument("path")?.trim()
            ?: return@withContext ToolOutput.error("argument path is missing", "Call artifact with a path such as artifacts/report.html.")
        val file = context.paths.resolve(path)
            ?: return@withContext ToolOutput.error("$path is outside the thread folder", "Use a path inside artifacts/.")
        val relativePath = context.paths.relativePath(file)
        val isInArtifacts = relativePath.startsWith(ArtifactVersions.ARTIFACTS_FOLDER + "/") &&
            !relativePath.startsWith("${ArtifactVersions.ARTIFACTS_FOLDER}/${ArtifactVersions.VERSIONS_FOLDER}/")
        if (!isInArtifacts || !relativePath.endsWith(".html")) {
            return@withContext ToolOutput.error(
                "$relativePath is not an .html file in artifacts/",
                "Write the page to artifacts/<name>.html first, then call artifact with that path.",
            )
        }
        if (!file.isFile) {
            return@withContext ToolOutput.error("$relativePath does not exist", "Write it with write_file, then call artifact again.")
        }
        if (file.length() > MAX_BYTES) {
            return@withContext ToolOutput.error(
                "$relativePath is ${file.length() / 1_000_000} MB, over the 5 MB limit",
                "Make the page smaller, for example by removing embedded images.",
            )
        }
        val version = ArtifactVersions(context.threadFolder).record(relativePath, file)
        val shown = "Showing $relativePath, version ${version.number}. The user can open it from the chat."
        val webResources = WebResources.loadedFromTheWeb(file.readText())
        if (webResources.isEmpty()) {
            return@withContext ToolOutput.success(shown)
        }
        ToolOutput.success(
            shown + "\nThese files come from the web and will not load in the viewer: " +
                webResources.joinToString(", ") +
                ". Put their content inside the page, or use lib/chart.js for Chart.js.",
        )
    }

    private companion object {
        const val MAX_BYTES = 5_000_000L
    }
}

/** Scripts, styles, images and fonts the page would fetch from the internet. */
internal object WebResources {
    // src= on any tag, and href= on <link> tags; a plain <a href> is a link the user taps.
    private val sourceAttribute = Regex("""\ssrc\s*=\s*["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)
    private val linkTag = Regex("""<link\b[^>]*\shref\s*=\s*["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)
    private val cssImport = Regex("""@import\s+(?:url\()?["']?(https?://[^"')\s]+)""", RegexOption.IGNORE_CASE)

    fun loadedFromTheWeb(html: String): List<String> {
        val found = linkedSetOf<String>()
        for (pattern in listOf(sourceAttribute, linkTag, cssImport)) {
            pattern.findAll(html).forEach { match -> found += match.groupValues[1] }
        }
        return found.toList()
    }
}
