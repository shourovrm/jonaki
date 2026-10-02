package app.jonaki.tools.editfile

import app.jonaki.core.toolapi.Capability
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import app.jonaki.core.toolapi.looksBinary
import app.jonaki.core.toolapi.stringArgument
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Replaces exact pieces of a text file; all edits apply or none does. */
class EditFileTool : Tool {
    override val name: String = "edit_file"
    override val promptLine: String = "edit_file: replace exact text in a file; several edits in one call"
    override val guidelines: List<String> = listOf(
        "Copy old_text exactly from read_file output, with enough lines around it to occur only once.",
    )
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "Path relative to the thread folder")
            }
            putJsonObject("edits") {
                put("type", "array")
                put("description", "Applied in order; each old_text must occur exactly once")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("old_text") { put("type", "string") }
                        putJsonObject("new_text") { put("type", "string") }
                    }
                    putJsonArray("required") {
                        add(JsonPrimitive("old_text"))
                        add(JsonPrimitive("new_text"))
                    }
                }
            }
        }
        putJsonArray("required") {
            add(JsonPrimitive("path"))
            add(JsonPrimitive("edits"))
        }
    }
    override val sideEffect: SideEffect = SideEffect.CHANGES
    override val requiredCapabilities: Set<Capability> = emptySet()
    override val timeLimit: Duration = 10.seconds

    override suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput = withContext(Dispatchers.IO) {
        val path = arguments.stringArgument("path")
            ?: return@withContext ToolOutput.error("argument path is missing", "Call edit_file with a path.")
        val edits = readEdits(arguments)
            ?: return@withContext ToolOutput.error(
                "argument edits is missing or malformed",
                "Pass edits as a list of objects with old_text and new_text.",
            )
        val file = context.paths.resolve(path)
            ?: return@withContext ToolOutput.error(
                "$path is outside the thread folder",
                "Use a path inside the thread folder.",
            )
        if (!file.isFile) {
            return@withContext ToolOutput.error(
                "$path does not exist",
                "Use find_files to find it, or write_file to create it.",
            )
        }
        if (looksBinary(file)) {
            return@withContext ToolOutput.error("$path is a binary file", "edit_file changes text files only.")
        }

        when (val result = applyEdits(file.readText(), edits)) {
            is EditResult.Failed -> ToolOutput.error(
                "edit ${result.editNumber} of ${edits.size} failed: ${result.reason}",
                "No edit was applied. Read the file with read_file and try again.",
            )
            is EditResult.Applied -> {
                file.writeText(result.newContent)
                ToolOutput.success(summary(context.paths.relativePath(file), result.matches))
            }
        }
    }

    /** Accepts the documented list, and also one top-level old_text/new_text pair that models often send. */
    private fun readEdits(arguments: JsonObject): List<TextEdit>? {
        val editArray = arguments["edits"] as? JsonArray
        if (editArray == null) {
            val oldText = arguments.stringArgument("old_text") ?: return null
            val newText = arguments.stringArgument("new_text") ?: return null
            return listOf(TextEdit(oldText, newText))
        }
        val edits = mutableListOf<TextEdit>()
        for (element in editArray) {
            val editObject = element as? JsonObject ?: return null
            val oldText = editObject.stringArgument("old_text") ?: return null
            val newText = editObject.stringArgument("new_text") ?: return null
            edits += TextEdit(oldText, newText)
        }
        if (edits.isEmpty()) {
            return null
        }
        return edits
    }

    private fun summary(path: String, matches: List<EditMatch>): String {
        val details = matches.mapIndexed { index, match ->
            val how = when (match.kind) {
                MatchKind.EXACT -> "exact"
                MatchKind.FUZZY -> "matched ignoring spacing or quote style"
            }
            "edit ${index + 1} at line ${match.firstLine} ($how)"
        }
        val noun = if (matches.size == 1) "edit" else "edits"
        return "Applied ${matches.size} $noun to $path: ${details.joinToString("; ")}."
    }
}
