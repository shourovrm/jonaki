package app.jonaki.core.agent

/**
 * The last line of a user message that brought files: "Attached:
 * inbox/a.csv, inbox/b.jpg" (D-042). The model reads the paths from it, and
 * [ImageMessages] finds the images in it again before each request (D-049).
 */
object AttachmentLine {
    private const val PREFIX = "Attached: "
    private const val INBOX_PREFIX = "inbox/"
    private const val SEPARATOR = ", "

    fun appendTo(text: String, inboxPaths: List<String>): String {
        if (inboxPaths.isEmpty()) {
            return text
        }
        val line = PREFIX + inboxPaths.joinToString(SEPARATOR)
        if (text.isBlank()) {
            return line
        }
        return text.trimEnd() + "\n\n" + line
    }

    /**
     * The paths of the message's attachment line, empty when it has none.
     * File names may hold ", " but never "/" (IncomingFiles makes it "_"),
     * so the line splits safely at ", inbox/".
     */
    fun pathsIn(text: String): List<String> {
        val line = text.lineSequence().lastOrNull { candidate -> candidate.startsWith(PREFIX + INBOX_PREFIX) }
            ?: return emptyList()
        val joinedPaths = line.removePrefix(PREFIX + INBOX_PREFIX)
        return joinedPaths.split(SEPARATOR + INBOX_PREFIX).map { name -> INBOX_PREFIX + name }
    }
}
