package app.jonaki.run

/** A new thread's name, taken from its first message until the user renames it. */
object ThreadTitles {
    /** Long enough for a whole question; the list wraps it (D-029). */
    private const val MAX_LENGTH = 100

    /** The "[date, time zone]" line saved in front of each user message for the model. */
    private val timeLine = Regex("""^\[[^\]\n]*]\n""")

    fun fromMessage(text: String): String {
        val withoutTimeLine = text.replaceFirst(timeLine, "")
        val firstLine = withoutTimeLine.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (firstLine.length <= MAX_LENGTH) {
            return firstLine
        }
        return firstLine.take(MAX_LENGTH).substringBeforeLast(' ').trimEnd() + "…"
    }

    /** Version 0.1.0 cut names at 40 characters and ended them in "…". */
    fun looksCutByOldVersion(title: String): Boolean = title.endsWith("…") && title.length <= 41
}
