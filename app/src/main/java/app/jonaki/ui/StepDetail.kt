package app.jonaki.ui

import java.util.Locale
import kotlinx.serialization.json.Json
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
                else -> StepDetail(query = null, target = arguments.text("path"))
            }
        }

        fun duration(millis: Long): String {
            if (millis < 60_000) {
                return String.format(Locale.ENGLISH, "%.1f s", millis / 1000.0)
            }
            val totalSeconds = millis / 1000
            return String.format(Locale.ENGLISH, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
        }

        private fun parse(argumentsJson: String): JsonObject? =
            runCatching { Json.parseToJsonElement(argumentsJson) as? JsonObject }.getOrNull()

        private fun JsonObject.text(key: String): String? =
            (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

        private fun withoutScheme(url: String): String =
            url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
    }
}
