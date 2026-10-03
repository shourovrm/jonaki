package app.jonaki.run

import app.jonaki.core.agent.PromptBuilder

/** A new thread's name, taken from its first message until the user renames it. */
object ThreadTitles {
    /** Long enough for a whole question; the list cuts it to one line (D-127), the chat shows it whole. */
    private const val MAX_LENGTH = 100

    fun fromMessage(text: String): String {
        val withoutTimeLine = PromptBuilder.userTextOf(text)
        val firstLine = withoutTimeLine.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (firstLine.length <= MAX_LENGTH) {
            return firstLine
        }
        return firstLine.take(MAX_LENGTH).substringBeforeLast(' ').trimEnd() + "…"
    }

    /** Version 0.1.0 cut names at 40 characters and ended them in "…". */
    fun looksCutByOldVersion(title: String): Boolean = title.endsWith("…") && title.length <= 41
}
