package app.jonaki.ui

/** The one plain line the thread list shows under a thread's title. */
object PreviewText {
    private val userTimeLine = Regex("""^\[[^\]\n]*]\n""")
    private val link = Regex("""\[([^\]]*)]\([^)]*\)""")
    private val emphasis = Regex("""(\*\*|__|\*|_|`)""")
    private val lineStart = Regex("""^(#{1,6}\s+|[-*+]\s+|\d+\.\s+|>\s*)""")

    fun of(text: String, isUserMessage: Boolean): String {
        val withoutTimeLine = if (isUserMessage) text.replaceFirst(userTimeLine, "") else text
        val firstLine = withoutTimeLine.lineSequence().firstOrNull { line -> line.isNotBlank() } ?: return ""
        val withoutLineMark = firstLine.trim().replaceFirst(lineStart, "")
        val withoutLinks = withoutLineMark.replace(link) { match -> match.groupValues[1] }
        return withoutLinks.replace(emphasis, "").trim()
    }
}
