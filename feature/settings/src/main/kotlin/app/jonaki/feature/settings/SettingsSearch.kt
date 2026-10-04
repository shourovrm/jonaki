package app.jonaki.feature.settings

import androidx.compose.runtime.Immutable

/** A setting the search can find, and the page that holds it. */
@Immutable
data class SettingsSearchEntry(
    val title: String,
    val page: SettingsPage,
)

/**
 * "Search settings" on the first page (D-128): an in-memory list of every
 * page title and every setting title on the pages, matched by substring.
 */
object SettingsSearch {
    /** The titles each page shows, beside the page's own title. */
    private val settingTitles: Map<SettingsPage, List<Int>> = mapOf(
        SettingsPage.MODELS to listOf(
            R.string.settings_section_chat,
            R.string.settings_add_service,
            R.string.settings_add_model,
            R.string.settings_routing,
        ),
        SettingsPage.LOCAL_MODELS to listOf(
            R.string.local_models_section_downloaded,
            R.string.local_models_section_recommended,
            R.string.local_models_search_hint,
            R.string.local_models_free_storage,
        ),
        SettingsPage.WEB to listOf(
            R.string.settings_section_search,
            R.string.settings_search_order,
            R.string.settings_search_off_new,
            R.string.settings_section_youtube,
            R.string.settings_gemini_key,
        ),
        SettingsPage.TOOLS to listOf(
            R.string.settings_section_approvals,
            R.string.settings_jev_guard,
            R.string.settings_section_tools,
            R.string.settings_tools_all,
            R.string.settings_python,
            R.string.settings_section_mcp,
            R.string.settings_mcp_add,
        ),
        SettingsPage.SUBAGENTS to listOf(
            R.string.settings_subagents_section_limits,
            R.string.settings_subagents_without_asking,
            R.string.settings_subagents_per_call,
            R.string.settings_subagents_warn_above,
            R.string.settings_subagents_section_each,
            R.string.settings_subagents_tool_steps,
            R.string.settings_subagents_cost,
            R.string.settings_subagents_minutes,
            R.string.settings_subagent_researcher,
            R.string.settings_subagent_scout,
            R.string.settings_subagent_writer,
            R.string.settings_subagent_worker,
            R.string.settings_subagents_section_custom,
            R.string.settings_subagents_add,
        ),
        SettingsPage.ANSWERS to listOf(
            R.string.settings_custom_instructions,
            R.string.settings_section_personas,
            R.string.settings_add_persona,
        ),
        SettingsPage.MEMORY_SKILLS to listOf(
            R.string.settings_section_memory,
            R.string.settings_memory_open,
            R.string.settings_section_skills,
            R.string.settings_skills_open,
        ),
        SettingsPage.FILES_SCHEDULE to listOf(
            R.string.settings_section_files,
            R.string.settings_link_folder,
            R.string.settings_linked_folder,
            R.string.settings_section_scheduled,
        ),
        SettingsPage.THEME to listOf(
            R.string.settings_theme_light,
            R.string.settings_theme_dark,
            R.string.settings_status_icons,
            R.string.settings_show_status_strip,
        ),
        SettingsPage.PERMISSIONS to permissionTitles(),
        SettingsPage.ABOUT to listOf(
            R.string.settings_about_github,
        ),
    )

    private fun permissionTitles(): List<Int> {
        val live = PermissionRow.entries.map { row -> row.title }
        val alwaysOn = AlwaysOnRow.entries.map { row -> row.title }
        return live + listOf(R.string.settings_permissions_always_on_header) + alwaysOn
    }

    /**
     * Every searchable title, with the names of the user's chat and search
     * services so that "Tavily" finds Web and YouTube, and of the user's
     * own subagents.
     */
    fun entries(texts: SettingsTexts, state: SettingsUiState): List<SettingsSearchEntry> {
        val entries = mutableListOf<SettingsSearchEntry>()
        for (page in SettingsPage.entries) {
            entries += SettingsSearchEntry(texts.string(page.title), page)
            for (title in settingTitles[page].orEmpty()) {
                entries += SettingsSearchEntry(texts.string(title), page)
            }
        }
        for (service in state.chatServices) {
            entries += SettingsSearchEntry(service.displayName, SettingsPage.MODELS)
        }
        for (service in state.searchServices) {
            entries += SettingsSearchEntry(service.displayName, SettingsPage.WEB)
        }
        for (subagent in state.customSubagents) {
            entries += SettingsSearchEntry(subagent.name, SettingsPage.SUBAGENTS)
        }
        return entries.distinct()
    }

    /** Titles that contain [query], ignoring case; nothing for a blank query. */
    fun filter(entries: List<SettingsSearchEntry>, query: String): List<SettingsSearchEntry> {
        val wanted = query.trim()
        if (wanted.isEmpty()) {
            return emptyList()
        }
        return entries.filter { entry -> entry.title.contains(wanted, ignoreCase = true) }
    }
}
