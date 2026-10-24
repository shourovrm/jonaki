package app.jonaki.feature.settings

import androidx.compose.runtime.Immutable

/** The second line of a row on the first Settings page; it exists only when the user must act. */
@Immutable
data class PageSummary(
    val text: String,
    /** Drawn in the error colour. Every summary that exists today is a problem, so it is true. */
    val needsAttention: Boolean = true,
)

/**
 * The second lines on the first Settings page (D-187). A line appears when
 * the user has to act, and disappears when the thing is fine: a missing
 * chat key, a missing Gemini key for YouTube, or a blocked permission.
 */
object SettingsSummaries {
    /** Null means the row shows its title only. */
    fun of(page: SettingsPage, state: SettingsUiState, texts: SettingsTexts): PageSummary? = when (page) {
        SettingsPage.MODELS -> models(state, texts)
        SettingsPage.WEB -> web(state, texts)
        SettingsPage.PERMISSIONS -> permissions(state.permissions, texts)
        else -> null
    }

    /** A service with a saved key can answer; Jonaki Free and Ollama on this network have no key slot and are not counted. */
    private fun models(state: SettingsUiState, texts: SettingsTexts): PageSummary? {
        val hasKey = state.chatServices.any { card -> card.apiKey != null && card.apiKey.isSet }
        if (hasKey) {
            return null
        }
        return PageSummary(texts.string(R.string.settings_key_not_set))
    }

    private fun web(state: SettingsUiState, texts: SettingsTexts): PageSummary? {
        if (state.geminiKey.isSet) {
            return null
        }
        return PageSummary(texts.string(R.string.settings_summary_youtube_needs_key))
    }

    /**
     * Rows without access, named when there is one and counted when there
     * are more. Shown only when one is blocked: a "Not allowed" row is one
     * tap from Android's dialog and needs no warning.
     */
    private fun permissions(rows: List<PermissionRowUi>, texts: SettingsTexts): PageSummary? {
        val withoutAccess = rows.filter { item -> !hasAccess(item.status) }
        val anyBlocked = withoutAccess.any { item -> item.status == PermissionStatus.BLOCKED }
        if (!anyBlocked) {
            return null
        }
        if (withoutAccess.size > 1) {
            val count = withoutAccess.size
            return PageSummary(texts.plural(R.plurals.settings_summary_not_allowed, count, count))
        }
        val only = withoutAccess.single()
        return PageSummary(texts.string(R.string.settings_summary_one_blocked, texts.string(only.row.title)))
    }

    private fun hasAccess(status: PermissionStatus): Boolean =
        status == PermissionStatus.ALLOWED || status == PermissionStatus.SELECTED_PHOTOS
}
