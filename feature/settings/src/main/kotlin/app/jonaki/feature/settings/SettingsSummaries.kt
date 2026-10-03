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
        SettingsPage.WEB -> PageSummary(web(state, texts))
        SettingsPage.TOOLS -> PageSummary(tools(state, texts))
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

    /** Denied and Off both need a trip to system settings, so both count as denied here. */
    private fun permissions(rows: List<PermissionRowUi>, texts: SettingsTexts): PageSummary {
        val denied = rows.filter { item -> item.status == PermissionStatus.DENIED || item.status == PermissionStatus.OFF }
        return when (denied.size) {
            0 -> PageSummary(texts.string(R.string.settings_summary_all_allowed))
            1 -> PageSummary(
                texts.string(R.string.settings_summary_one_denied, texts.string(denied.single().row.title)),
                needsAttention = true,
            )
            else -> PageSummary(texts.plural(R.plurals.settings_summary_denied, denied.size, denied.size), needsAttention = true)
        }
    }
}
