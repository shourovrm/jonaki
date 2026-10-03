package app.jonaki.tools.webfetch

import kotlin.time.Duration

/**
 * Loads a page in a browser engine, runs its JavaScript and hands back the
 * resulting HTML, for pages that show nothing without JavaScript. The app
 * implements it with an off-screen WebView; this module sees only the
 * interface, as the phone tool sees only Phone.
 */
interface PageRenderer {
    /**
     * Returns the page as the browser built it, or throws
     * [PageRenderException] with a reason the model can read. Gives up
     * after [timeLimit].
     */
    suspend fun render(url: String, timeLimit: Duration): RenderedPage
}

data class RenderedPage(
    /** The address after redirects. */
    val finalUrl: String,
    val title: String,
    /** document.documentElement.outerHTML after the scripts ran. */
    val html: String,
)

class PageRenderException(reason: String) : Exception(reason)
