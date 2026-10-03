package app.jonaki.tools.webfetch

/**
 * Decides whether a page fetched over plain HTTP needs to be rendered with
 * JavaScript before its text can be read.
 */
internal object RenderDecision {
    /**
     * A page with scripts and less text than this is taken for an app shell,
     * for example a React page whose body is only <div id="root">. A page
     * without scripts is never rendered, because rendering cannot add text
     * to it (example.com has about 130 characters and no script).
     */
    private const val SHELL_MAXIMUM_CHARACTERS = 200

    /**
     * Above this, a page that mentions JavaScript has real content of its
     * own, such as an article about JavaScript or a page with a small
     * "enable JavaScript to see comments" note.
     */
    private const val WARNING_PAGE_MAXIMUM_CHARACTERS = 1_500

    private val javaScriptWarnings = listOf(
        "enable javascript",
        "javascript is required",
        "javascript is disabled",
        "javascript must be enabled",
        "requires javascript",
        "turn on javascript",
        "javascript is not enabled",
        "javascript is turned off",
    )

    fun needsRendering(page: ExtractedPage, html: String): Boolean {
        val text = page.text
        val hasScripts = html.contains("<script", ignoreCase = true)
        if (hasScripts && text.length < SHELL_MAXIMUM_CHARACTERS) {
            return true
        }
        if (text.length >= WARNING_PAGE_MAXIMUM_CHARACTERS) {
            return false
        }
        val lowercaseText = text.lowercase()
        return javaScriptWarnings.any { warning -> warning in lowercaseText }
    }
}
