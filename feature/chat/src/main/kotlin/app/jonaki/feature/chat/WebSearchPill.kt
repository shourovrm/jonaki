package app.jonaki.feature.chat

import androidx.annotation.StringRes

/**
 * The globe pill in the status strip (D-123): one tap switches web search for
 * this thread. On, the globe is drawn like the other pills; off, it is dimmed
 * and crossed out.
 */
internal data class WebSearchPillState(
    @StringRes val descriptionResource: Int,
    val crossedOut: Boolean,
    /** What a tap sets web search to. */
    val enabledAfterTap: Boolean,
) {
    companion object {
        fun of(webSearchEnabled: Boolean): WebSearchPillState = if (webSearchEnabled) {
            WebSearchPillState(R.string.chat_status_web_on, crossedOut = false, enabledAfterTap = false)
        } else {
            WebSearchPillState(R.string.chat_status_web_off, crossedOut = true, enabledAfterTap = true)
        }
    }
}
