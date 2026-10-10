package app.jonaki.run

import app.jonaki.core.agent.PromptBuilder

/**
 * A new thread's name: the first line of its first message until the model
 * writes a short one after the first answer (see [ThreadNamer]), or the user
 * renames the thread.
 */
object ThreadTitles {
    /** Long enough for a whole question; the list shows one line of it and ends in "…" (D-029), the chat's rename dialog shows it whole. */
    private const val MAX_LENGTH = 100

    /** The longest generated name, "…" included. */
    private const val MAX_GENERATED_LENGTH = 60

    private const val ELLIPSIS = "…"

    /** Marks a model puts around or after a name; a question mark stays because it can be part of the name. */
    private const val SURROUNDING_QUOTES = "\"'`“”‘’„«»"
    private const val TRAILING_MARKS = ".。।…,;:!"

    /** "Title:", "**Title:**" and the Bangla "শিরোনাম:" at the start of the answer. */
    private val LABEL = Regex("""^\**\s*(thread title|title|name|শিরোনাম)\s*\**\s*[:：]\s*\**\s*""", RegexOption.IGNORE_CASE)

    fun fromMessage(text: String): String {
        val withoutTimeLine = PromptBuilder.userTextOf(text)
        val firstLine = withoutTimeLine.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (firstLine.length <= MAX_LENGTH) {
            return firstLine
        }
        return firstLine.take(MAX_LENGTH).substringBeforeLast(' ').trimEnd() + ELLIPSIS
    }

    /**
     * Cleans the background model's answer into a thread name: its first
     * non-blank line without quotes, label and closing marks, in single
     * spaces, at most 60 characters. Null when nothing usable is left.
     */
    fun fromGenerated(answer: String): String? {
        val firstLine = answer.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        // The label goes before the closing marks, or the colon of a bare "Title:" would go with them.
        val withoutLabel = LABEL.replace(firstLine.trim { character -> character in SURROUNDING_QUOTES || character.isWhitespace() }, "")
        val cleaned = stripQuotesAndMarks(withoutLabel).replace(Regex("""\s+"""), " ")
        if (cleaned.isEmpty()) {
            return null
        }
        return cutAtWord(cleaned)
    }

    /** Repeats until stable, because a closing mark can sit inside or outside the quotes. */
    private fun stripQuotesAndMarks(text: String): String {
        var current = text.trim()
        while (true) {
            val stripped = current.trim { character -> character in SURROUNDING_QUOTES || character.isWhitespace() }
                .trimEnd { character -> character in TRAILING_MARKS || character.isWhitespace() }
            if (stripped == current) {
                return current
            }
            current = stripped
        }
    }

    private fun cutAtWord(name: String): String {
        if (name.length <= MAX_GENERATED_LENGTH) {
            return name
        }
        val room = MAX_GENERATED_LENGTH - ELLIPSIS.length
        val candidate = name.take(room)
        val endsOnWordBoundary = name[room] == ' '
        val wholeWords = if (endsOnWordBoundary) candidate else candidate.substringBeforeLast(' ', candidate)
        return wholeWords.trimEnd { character -> character in TRAILING_MARKS || character.isWhitespace() } + ELLIPSIS
    }

    /** Version 0.1.0 cut names at 40 characters and ended them in "…". */
    fun looksCutByOldVersion(title: String): Boolean = title.endsWith("…") && title.length <= 41
}
