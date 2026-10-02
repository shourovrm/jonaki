package app.jonaki.ui

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
                "delegate" -> StepDetail(query = null, target = delegateTarget(arguments))
                "request_tool" -> StepDetail(query = null, target = arguments.text("name"))
                "ask_parent" -> StepDetail(query = arguments.text("question"), target = null)
                else -> StepDetail(query = null, target = arguments.text("path"))
            }
        }

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
