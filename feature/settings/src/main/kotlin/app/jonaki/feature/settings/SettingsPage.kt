package app.jonaki.feature.settings

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import app.jonaki.core.ui.AnswerStyleChoice
import app.jonaki.core.ui.ApprovalModeChoice

/**
 * One sub-page of Settings (D-128). [key] is the part of the app's route
 * after "settings:", so it must stay stable across versions: a saved route
 * survives process death.
 */
enum class SettingsPage(val key: String, @StringRes val title: Int) {
    MODELS("models", R.string.settings_root_models),
    /** Drawn by [LocalModelsScreen], which keeps its own search and file list (D-133). */
    LOCAL_MODELS("local-models", R.string.settings_root_local_models),
    WEB("web", R.string.settings_root_web),
    TOOLS("tools", R.string.settings_root_tools),
    /** The Jev guard and what a thread does after it has read outside content (D-143, D-144). */
    GUARDRAILS("guardrails", R.string.settings_root_guardrails),
    /** Limits, the built-in types' models and the user's own types (D-138). */
    SUBAGENTS("subagents", R.string.settings_root_subagents),
    ANSWERS("answers", R.string.settings_root_answers),
    MEMORY_SKILLS("memory-skills", R.string.settings_root_memory_skills),
    FILES_SCHEDULE("files-schedule", R.string.settings_root_files_schedule),
    THEME("theme", R.string.settings_root_theme),
    PERMISSIONS("permissions", R.string.settings_root_permissions),
    ABOUT("about", R.string.settings_root_about),
    ;

    companion object {
        /** The first page's three groups, separated by space and without labels. */
        val GROUPS: List<List<SettingsPage>> = listOf(
            listOf(MODELS, LOCAL_MODELS, WEB, TOOLS, GUARDRAILS, SUBAGENTS),
            listOf(ANSWERS, MEMORY_SKILLS, FILES_SCHEDULE),
            listOf(THEME, PERMISSIONS, ABOUT),
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
