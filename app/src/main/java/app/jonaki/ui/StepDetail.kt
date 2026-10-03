package app.jonaki.ui

import app.jonaki.core.runtimeapi.CodeLanguage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The short line under a step in the step track. */
data class StepDetail(
    /** For web_search: the exact text sent to the search service (D-011 amendment). */
    val query: String?,
    /** A path, link or site the step works on. */
    val target: String?,
) {
    companion object {
        fun of(toolName: String, argumentsJson: String): StepDetail {
            val arguments = parse(argumentsJson) ?: return StepDetail(query = null, target = null)
            return when (toolName) {
                "web_search" -> StepDetail(query = arguments.text("query"), target = arguments.text("site"))
                "web_fetch", "youtube_summarize" -> StepDetail(query = null, target = arguments.text("url")?.let(::withoutScheme))
                "find_files", "search_files" -> StepDetail(query = null, target = arguments.text("pattern") ?: arguments.text("path"))
                "share_file" -> StepDetail(query = null, target = shareFileTarget(arguments.text("action"), arguments.text("path")))
                "phone" -> StepDetail(query = null, target = phoneTarget(arguments))
                "schedule" -> StepDetail(query = null, target = scheduleTarget(arguments))
                "mcp" -> mcpDetail(arguments)
                "delegate" -> StepDetail(query = null, target = delegateTarget(arguments))
                "request_tool" -> StepDetail(query = null, target = arguments.text("name"))
                "ask_parent" -> StepDetail(query = arguments.text("question"), target = null)
                "run_code" -> StepDetail(query = null, target = runCodeTarget(arguments))
                else -> StepDetail(query = null, target = arguments.text("path"))
            }
        }

        /** The approval card names what the phone action does and to what (plan M9). */
        private fun phoneTarget(arguments: JsonObject): String? = when (arguments.text("action")) {
            "calendar_list" -> "Read calendar"
            "calendar_add" -> labelled("Add to calendar", arguments.text("title"))
            "reminder" -> labelled("Reminder", arguments.text("text"))
            "notify" -> labelled("Notify", arguments.text("title") ?: arguments.text("text"))
            "clipboard_read" -> "Read clipboard"
            "clipboard_write" -> "Copy to clipboard"
            "open_app" -> labelled("Open app", arguments.text("app"))
            else -> null
        }

        private fun scheduleTarget(arguments: JsonObject): String? = when (arguments.text("action")) {
            "create" -> labelled("Schedule", arguments.text("title") ?: arguments.text("prompt"))
            "cancel" -> labelled("Cancel task", arguments.text("id"))
            "list" -> "List tasks"
            else -> null
        }

        private fun labelled(label: String, subject: String?): String = if (subject == null) label else "$label: $subject"

        /** The approval card must say where the file goes, not only which file (D-044). */
        private fun shareFileTarget(action: String?, path: String?): String? {
            val where = when (action) {
                "downloads" -> "To Downloads"
                "save_as" -> "Save as"
                "share" -> "Share"
                "linked_folder" -> "To linked folder"
                "list_linked" -> "List linked folder"
                "import_linked" -> "From linked folder"
                else -> null
            }
            if (where == null) {
                return path
            }
            if (path == null) {
                return where
            }
            return "$where: $path"
        }

        /** search shows its words; describe and call show "server: tool", so the approval card names what runs where. */
        private fun mcpDetail(arguments: JsonObject): StepDetail {
            if (arguments.text("action") == "search") {
                return StepDetail(query = arguments.text("query"), target = null)
            }
            val server = arguments.text("server")
            val tool = arguments.text("tool")
            val target = if (server == null) tool else "$server: ${tool.orEmpty()}"
            return StepDetail(query = null, target = target)
        }

        /** "Python · 12 lines": the code itself is too long for one line, and its sheet shows it (D-090). */
        private fun runCodeTarget(arguments: JsonObject): String? {
            val code = arguments.text("code") ?: return null
            val lineCount = programLineCount(code)
            val lines = if (lineCount == 1) "1 line" else "$lineCount lines"
            val language = arguments.text("language")?.let(CodeLanguage::fromArgument) ?: return lines
            return "${language.displayName} · $lines"
        }

        /** Trailing line breaks are not lines of the program; the code sheet counts the same way. */
        fun programLineCount(code: String): Int = code.trimEnd('\n', '\r').lines().size

        /** "researcher, scout" for parallel tasks, else the one agent type. */
        private fun delegateTarget(arguments: JsonObject): String? {
            val tasks = arguments["tasks"] as? JsonArray
            if (tasks != null && tasks.isNotEmpty()) {
                return tasks.mapNotNull { task -> (task as? JsonObject)?.text("agent") }.joinToString(", ")
            }
            return arguments.text("agent")
        }

        private fun parse(argumentsJson: String): JsonObject? =
            runCatching { Json.parseToJsonElement(argumentsJson) as? JsonObject }.getOrNull()

        private fun JsonObject.text(key: String): String? =
            (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

        private fun withoutScheme(url: String): String =
            url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
    }
}
