package app.jonaki.feature.settings

import androidx.compose.runtime.Immutable
import app.jonaki.core.ui.ThemeMode

/** The second line of a row on the first Settings page. */
@Immutable
data class PageSummary(
    val text: String,
    /** Drawn in the error colour: only Permissions sets it, and only when something is denied. */
    val needsAttention: Boolean = false,
)

/**
 * The live one-line summaries on the first Settings page (D-128), so most
 * states can be read without opening a page.
 */
object SettingsSummaries {
    private const val SEPARATOR = " · "

    fun of(page: SettingsPage, state: SettingsUiState, texts: SettingsTexts): PageSummary = when (page) {
        SettingsPage.MODELS -> PageSummary(models(state, texts))
        SettingsPage.LOCAL_MODELS -> PageSummary(localModels(state.localModels, texts))
        SettingsPage.WEB -> PageSummary(web(state, texts))
        SettingsPage.TOOLS -> PageSummary(tools(state, texts))
        SettingsPage.SUBAGENTS -> PageSummary(subagents(state, texts))
        SettingsPage.ANSWERS -> PageSummary(answers(state, texts))
        SettingsPage.MEMORY_SKILLS -> PageSummary(memoryAndSkills(state, texts))
        SettingsPage.FILES_SCHEDULE -> PageSummary(filesAndSchedule(state, texts))
        SettingsPage.THEME -> PageSummary(theme(state.themeMode, texts))
        SettingsPage.PERMISSIONS -> permissions(state.permissions, texts)
        SettingsPage.ABOUT -> PageSummary(texts.string(R.string.settings_about_version, state.appVersion))
    }

    /** The first service that can answer: one with a saved key, or one that needs none (Ollama on this network). */
    private fun models(state: SettingsUiState, texts: SettingsTexts): String {
        if (state.chatServices.isEmpty()) {
            return texts.string(R.string.settings_summary_no_chat_service)
        }
        val service = state.chatServices.firstOrNull { card -> card.apiKey == null || card.apiKey.isSet }
            ?: return texts.string(R.string.settings_key_not_set)
        val modelCount = service.models.size
        val balance = service.apiKey?.balance
        if (balance == null) {
            return texts.plural(R.plurals.settings_summary_models_no_balance, modelCount, service.displayName, modelCount)
        }
        return texts.plural(R.plurals.settings_summary_models, modelCount, service.displayName, modelCount, balance)
    }

    /** "2 downloaded · Qwen3.5-2B fits": what is on the phone, and the largest recommended model it can run now. */
    private fun localModels(summary: LocalModelsSummaryUi, texts: SettingsTexts): String {
        val count = summary.downloadedCount
        val downloaded = if (count == 0) {
            texts.string(R.string.settings_summary_local_none_downloaded)
        } else {
            texts.plural(R.plurals.settings_summary_local_downloaded, count, count)
        }
        val fittingName = summary.largestFittingName
        val fits = if (fittingName == null) {
            texts.string(R.string.settings_summary_local_none_fits)
        } else {
            texts.string(R.string.settings_summary_local_fits, fittingName)
        }
        return downloaded + SEPARATOR + fits
    }

    /** Search services in the order web_search tries them, only those with a key. */
    private fun web(state: SettingsUiState, texts: SettingsTexts): String {
        val usable = state.searchServices.filter { service -> service.apiKey.isSet }.map { service -> service.displayName }
        val search = if (usable.isEmpty()) {
            texts.string(R.string.settings_summary_search_off)
        } else {
            usable.reduce { order, next -> texts.string(R.string.settings_summary_web_then, order, next) }
        }
        if (state.geminiKey.isSet) {
            return search
        }
        return search + SEPARATOR + texts.string(R.string.settings_summary_no_gemini_key)
    }

    private fun tools(state: SettingsUiState, texts: SettingsTexts): String = texts.plural(
        R.plurals.settings_summary_tools,
        state.toolGroupCount,
        state.toolGroupsOn,
        state.toolGroupCount,
        texts.approvalMode(state.approvalMode),
    )

    /** "2 without asking · 1 custom": the limit that decides when the user is asked, and the user's own types. */
    private fun subagents(state: SettingsUiState, texts: SettingsTexts): String {
        val customCount = state.customSubagents.size
        val custom = if (customCount == 0) {
            texts.string(R.string.settings_summary_subagents_no_custom)
        } else {
            texts.plural(R.plurals.settings_summary_subagents_custom, customCount, customCount)
        }
        val withoutAsking = state.subagentLimits.firstOrNull { limit -> limit.limit == SubagentLimit.WITHOUT_ASKING }
            ?: return custom
        val automatic = texts.plural(R.plurals.settings_summary_subagents_auto, withoutAsking.value, withoutAsking.value)
        return automatic + SEPARATOR + custom
    }

    private fun answers(state: SettingsUiState, texts: SettingsTexts): String {
        val personaCount = state.personas.size
        val personas = if (personaCount == 0) {
            texts.string(R.string.settings_summary_no_personas)
        } else {
            texts.plural(R.plurals.settings_summary_personas, personaCount, personaCount)
        }
        return texts.answerStyle(state.answerStyle) + SEPARATOR + personas
    }

    private fun memoryAndSkills(state: SettingsUiState, texts: SettingsTexts): String {
        val facts = texts.plural(R.plurals.settings_summary_facts, state.factCount, state.factCount)
        val skills = texts.plural(R.plurals.settings_summary_skills, state.skillCount, state.skillCount)
        return facts + SEPARATOR + skills
    }

    private fun filesAndSchedule(state: SettingsUiState, texts: SettingsTexts): String {
        val folderName = state.linkedFolderName
        val folder = if (folderName == null) {
            texts.string(R.string.settings_summary_no_folder)
        } else {
            texts.string(R.string.settings_summary_folder, folderName)
        }
        val scheduledCount = state.scheduledItems.size
        val scheduled = if (scheduledCount == 0) {
            texts.string(R.string.settings_summary_nothing_scheduled)
        } else {
            texts.plural(R.plurals.settings_summary_scheduled, scheduledCount, scheduledCount)
        }
        return folder + SEPARATOR + scheduled
    }

    private fun theme(mode: ThemeMode, texts: SettingsTexts): String = when (mode) {
        ThemeMode.SYSTEM -> texts.string(R.string.settings_summary_theme_phone)
        ThemeMode.LIGHT -> texts.string(R.string.settings_theme_light)
        ThemeMode.DARK -> texts.string(R.string.settings_theme_dark)
    }

    /**
     * Rows without access, named when there is one and counted when there
     * are more. Red only when one is blocked, as on the page itself: a
     * "Not allowed" row is one tap from Android's dialog.
     */
    private fun permissions(rows: List<PermissionRowUi>, texts: SettingsTexts): PageSummary {
        val withoutAccess = rows.filter { item -> !hasAccess(item.status) }
        val anyBlocked = withoutAccess.any { item -> item.status == PermissionStatus.BLOCKED }
        if (withoutAccess.isEmpty()) {
            return PageSummary(texts.string(R.string.settings_summary_all_allowed))
        }
        if (withoutAccess.size > 1) {
            val count = withoutAccess.size
            return PageSummary(texts.plural(R.plurals.settings_summary_not_allowed, count, count), needsAttention = anyBlocked)
        }
        val only = withoutAccess.single()
        val title = texts.string(only.row.title)
        val text = if (only.status == PermissionStatus.BLOCKED) {
            texts.string(R.string.settings_summary_one_blocked, title)
        } else {
            texts.string(R.string.settings_summary_one_not_allowed, title)
        }
        return PageSummary(text, needsAttention = anyBlocked)
    }

    private fun hasAccess(status: PermissionStatus): Boolean =
        status == PermissionStatus.ALLOWED || status == PermissionStatus.SELECTED_PHOTOS
}
