package app.jonaki.feature.artifact

/**
 * A page that leaves Jonaki (Downloads, share, linked folder) cannot load
 * lib/chart.js from the viewer, so the library is copied into the page
 * itself before it goes (D-047). The copy is about 208 KB larger.
 */
object StandaloneHtml {
    const val CHART_LIBRARY_ASSET = "jonaki-lib/chart.umd.min.js"

    private val chartScriptTag = Regex(
        """<script\s+src\s*=\s*["'](?:\./)?lib/chart\.js["']\s*>\s*</script\s*>""",
        RegexOption.IGNORE_CASE,
    )

    /** The page with the library inside, or null when the page does not use it. */
    fun withLibraries(html: String, chartLibrary: () -> String): String? {
        if (!chartScriptTag.containsMatchIn(html)) {
            return null
        }
        val inlineScript = "<script>" + chartLibrary() + "</script>"
        // A lambda replacement, so "$" in the library is not read as a group reference.
        return chartScriptTag.replace(html) { inlineScript }
    }
}
