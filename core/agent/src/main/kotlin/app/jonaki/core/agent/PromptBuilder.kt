package app.jonaki.core.agent

import app.jonaki.core.toolapi.Tool
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds the system prompt and the per-message context line. The system
 * prompt depends only on the base text and the active tools, never on the
 * time, so it is byte-identical between requests and the provider's prompt
 * cache keeps working (D-005).
 */
class PromptBuilder(private val basePrompt: String) {

    /**
     * [memorySection] comes from [MemorySection.build]; it goes last, so that
     * a changed fact leaves the base text and the tool list as a cached prefix.
     */
    fun systemPrompt(activeTools: List<Tool>, memorySection: String = ""): String {
        val withTools = basePromptWithTools(activeTools)
        if (memorySection.isBlank()) {
            return withTools
        }
        return withTools + "\n\n" + memorySection.trim()
    }

    private fun basePromptWithTools(activeTools: List<Tool>): String {
        // Sorted so that the order tools were registered in cannot change the bytes.
        val sortedTools = activeTools.sortedBy { tool -> tool.name }
        val prompt = StringBuilder(basePrompt.trimEnd())
        if (sortedTools.isEmpty()) {
            return prompt.toString()
        }
        prompt.append("\n\nTools:\n")
        for (tool in sortedTools) {
            prompt.append("- ").append(tool.promptLine).append('\n')
        }
        val guidelines = sortedTools.flatMap { tool -> tool.guidelines }
        if (guidelines.isNotEmpty()) {
            prompt.append("\nGuidelines:\n")
            for (guideline in guidelines) {
                prompt.append("- ").append(guideline).append('\n')
            }
        }
        return prompt.toString().trimEnd()
    }

    /**
     * The user's text with the current time in front. The result is stored as
     * the message, so that later requests resend exactly the same bytes.
     */
    fun userMessageWithContext(text: String, now: ZonedDateTime): String {
        val formatted = CONTEXT_FORMAT.format(now)
        return "[$formatted ${now.zone.id}]\n$text"
    }

    private companion object {
        val CONTEXT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm", Locale.ENGLISH)
    }
}
