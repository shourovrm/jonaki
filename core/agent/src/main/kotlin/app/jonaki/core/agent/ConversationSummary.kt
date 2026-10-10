package app.jonaki.core.agent

/**
 * The request that asks a model for the structured summary replacing the
 * older part of a long thread (M4 step 6, D-033). The caller sends it on the
 * background model.
 */
object ConversationSummary {
    val SECTIONS = listOf(
        "Goal",
        "Constraints and Preferences",
        "Progress",
        "Key Decisions",
        "Next Steps",
        "Critical Context",
    )

    /** Enough for a dense summary; a summary cut off at this limit is discarded. */
    const val MAX_SUMMARY_TOKENS = 4_000

    val SYSTEM_PROMPT = """
        You summarise a conversation between a user and an AI assistant so that the assistant can continue it without the original messages.
        Write these sections as Markdown headings, in this order: ${SECTIONS.joinToString(", ")}.
        Under each heading, write short bullet points. Keep names, numbers, file paths, URLs, dates and the user's exact wording of requirements, including any language the user asked answers to be written in. Leave out greetings and anything already finished that no later step depends on.
        If a summary so far is given, merge the new conversation into it and return one complete summary.
        Write in the language the user writes in. Return only the summary.
    """.trimIndent()

    /** The earlier summary comes first, so the model merges the new part into it. */
    fun requestText(previousSummary: String?, transcript: String): String {
        val parts = mutableListOf<String>()
        if (previousSummary != null) {
            parts += "Summary so far:\n$previousSummary"
        }
        parts += "Conversation to add:\n$transcript"
        return parts.joinToString("\n\n")
    }
}
