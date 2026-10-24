package app.jonaki.feature.settings

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import app.jonaki.core.ui.AnswerStyleChoice
import app.jonaki.core.ui.ApprovalModeChoice

/** The six titled groups of the Settings home (D-187). */
enum class SettingsGroup(@StringRes val title: Int) {
    MODELS(R.string.settings_group_models),
    CAPABILITIES(R.string.settings_group_capabilities),
    MEMORY(R.string.settings_group_memory),
    SAFETY(R.string.settings_group_safety),
    LOOK(R.string.settings_group_look),
    ABOUT(R.string.settings_group_about),
}

/**
 * One sub-page of Settings (D-128). [key] is the part of the app's route
 * after "settings:", so it must stay stable across versions: a saved route
 * survives process death.
 */
enum class SettingsPage(val key: String, @StringRes val title: Int, val group: SettingsGroup) {
    MODELS("models", R.string.settings_root_models, SettingsGroup.MODELS),
    /** Drawn by [LocalModelsScreen], which keeps its own search and file list (D-133). */
    LOCAL_MODELS("local-models", R.string.settings_root_local_models, SettingsGroup.MODELS),
    WEB("web", R.string.settings_root_web, SettingsGroup.CAPABILITIES),
    TOOLS("tools", R.string.settings_root_tools, SettingsGroup.CAPABILITIES),
    /** Limits, the built-in types' models and the user's own types (D-138). */
    SUBAGENTS("subagents", R.string.settings_root_subagents, SettingsGroup.CAPABILITIES),
    REMINDERS("reminders", R.string.settings_root_reminders, SettingsGroup.CAPABILITIES),
    FILES("files", R.string.settings_root_files, SettingsGroup.CAPABILITIES),
    MEMORY("memory", R.string.settings_root_memory, SettingsGroup.MEMORY),
    SKILLS("skills", R.string.settings_root_skills, SettingsGroup.MEMORY),
    /** Approvals, the safety check and what a thread does after it has read web content (D-143, D-144). */
    SAFETY("safety", R.string.settings_root_safety, SettingsGroup.SAFETY),
    PERMISSIONS("permissions", R.string.settings_root_permissions, SettingsGroup.SAFETY),
    THEME("theme", R.string.settings_root_theme, SettingsGroup.LOOK),
    ANSWERS("answers", R.string.settings_root_answers, SettingsGroup.LOOK),
    ABOUT("about", R.string.settings_root_about, SettingsGroup.ABOUT),
    ;

    companion object {
        /** The home page's groups in order; the order inside a group is the order of its rows. */
        val GROUPS: List<Pair<SettingsGroup, List<SettingsPage>>> = listOf(
            SettingsGroup.MODELS to listOf(MODELS, LOCAL_MODELS),
            SettingsGroup.CAPABILITIES to listOf(WEB, TOOLS, SUBAGENTS, REMINDERS, FILES),
            SettingsGroup.MEMORY to listOf(MEMORY, SKILLS),
            SettingsGroup.SAFETY to listOf(SAFETY, PERMISSIONS),
            SettingsGroup.LOOK to listOf(THEME, ANSWERS),
            SettingsGroup.ABOUT to listOf(ABOUT),
        )

        fun byKey(key: String): SettingsPage? = entries.firstOrNull { page -> page.key == key }
    }
}

/**
 * The text lookups the summaries and the search need. On the phone they come
 * from [Resources]; the JVM tests read the English values files instead.
 */
interface SettingsTexts {
    fun string(@StringRes id: Int, vararg args: Any): String

    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String

    /** Lives in core/ui, beside the approval options themselves. */
    fun approvalMode(choice: ApprovalModeChoice): String

    /** Lives in core/ui, beside the answer style row itself. */
    fun answerStyle(choice: AnswerStyleChoice): String
}

class ResourceSettingsTexts(private val resources: Resources) : SettingsTexts {
    override fun string(id: Int, vararg args: Any): String = resources.getString(id, *args)

    override fun plural(id: Int, count: Int, vararg args: Any): String = resources.getQuantityString(id, count, *args)

    override fun approvalMode(choice: ApprovalModeChoice): String {
        val id = when (choice) {
            ApprovalModeChoice.ASK -> app.jonaki.core.ui.R.string.ui_approval_ask
            ApprovalModeChoice.AUTO -> app.jonaki.core.ui.R.string.ui_approval_auto
            ApprovalModeChoice.BYPASS -> app.jonaki.core.ui.R.string.ui_approval_bypass
        }
        return resources.getString(id)
    }

    override fun answerStyle(choice: AnswerStyleChoice): String {
        val id = when (choice) {
            AnswerStyleChoice.DEFAULT -> app.jonaki.core.ui.R.string.ui_style_default
            AnswerStyleChoice.CONCISE -> app.jonaki.core.ui.R.string.ui_style_concise
            AnswerStyleChoice.NORMAL -> app.jonaki.core.ui.R.string.ui_style_normal
            AnswerStyleChoice.DETAILED -> app.jonaki.core.ui.R.string.ui_style_detailed
        }
        return resources.getString(id)
    }
}
