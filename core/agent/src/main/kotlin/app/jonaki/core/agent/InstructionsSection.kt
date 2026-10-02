package app.jonaki.core.agent

/** How long answers should be: a global default in Settings, changed per thread (D-STY-2). */
enum class AnswerStyle {
    CONCISE,
    NORMAL,
    DETAILED,
}

/** A saved persona as the prompt shows it (D-STY-3). */
data class PromptPersona(
    val name: String,
    val instructions: String,
)

/**
 * Builds the user's part of the system prompt (D-STY-1): the answer style,
 * the general instructions from Settings, the thread's persona and the
 * thread's own instructions, in that order, so that a later, more specific
 * part wins over an earlier one. Every part changes only when the user edits
 * it, so the prompt stays byte-identical between requests (D-005). With
 * nothing set the section is empty and the prompt is the same as without it.
 */
object InstructionsSection {
    private const val HEADER = "User instructions (written by the user; where two parts disagree, the later part wins):"

    private const val CONCISE_LINE = "Answer style: concise. Lead with the answer and use as few words as it needs; " +
        "leave out background, caveats and summaries unless the user asks."

    private const val DETAILED_LINE = "Answer style: detailed. Explain the reasoning and the steps, give examples, " +
        "and cover what the user is likely to need next."

    fun build(
        answerStyle: AnswerStyle,
        generalInstructions: String,
        persona: PromptPersona?,
        threadInstructions: String,
    ): String {
        val parts = mutableListOf<String>()
        styleLine(answerStyle)?.let { line -> parts += line }
        if (generalInstructions.isNotBlank()) {
            parts += "General:\n" + generalInstructions.trim()
        }
        if (persona != null) {
            parts += personaPart(persona)
        }
        if (threadInstructions.isNotBlank()) {
            parts += "This thread:\n" + threadInstructions.trim()
        }
        if (parts.isEmpty()) {
            return ""
        }
        return (listOf(HEADER) + parts).joinToString("\n\n")
    }

    /** Normal adds no line, so a user who never picks a style keeps the prompt they had. */
    private fun styleLine(answerStyle: AnswerStyle): String? = when (answerStyle) {
        AnswerStyle.CONCISE -> CONCISE_LINE
        AnswerStyle.NORMAL -> null
        AnswerStyle.DETAILED -> DETAILED_LINE
    }

    private fun personaPart(persona: PromptPersona): String {
        // A line break inside the name would break the header line.
        val name = persona.name.replace('\n', ' ').trim()
        val header = "Persona \"$name\" (speak in its voice and follow its instructions; your tools and rules stay the same):"
        val instructions = persona.instructions.trim()
        if (instructions.isEmpty()) {
            return header
        }
        return header + "\n" + instructions
    }
}
