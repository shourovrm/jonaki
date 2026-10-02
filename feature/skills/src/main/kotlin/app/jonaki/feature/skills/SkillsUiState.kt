package app.jonaki.feature.skills

/**
 * The skills screen (D-037 to D-041). From Settings it lists the library;
 * from a thread's ⋮ menu it adds a switch per skill for that thread.
 */
data class SkillsUiState(
    /** Null when opened from Settings. */
    val threadTitle: String?,
    val skills: List<SkillRowUi>,
    /** Built-in skills the user deleted exist, so "Restore built-in skills" is offered. */
    val canRestoreBuiltIns: Boolean,
    val import: ImportUi,
) {
    val isThreadView: Boolean
        get() = threadTitle != null
}

data class SkillRowUi(
    val name: String,
    /** Empty when [problem] is set. */
    val description: String,
    /** Why SKILL.md cannot be used; such a skill is never in the prompt. */
    val problem: String?,
    val isBuiltIn: Boolean,
    val isEdited: Boolean,
    /** On in the thread; only shown in the thread view. */
    val enabledInThread: Boolean,
)

/** The add-skill dialog: closed, open, working, failed or asking to replace. */
data class ImportUi(
    val isOpen: Boolean = false,
    val isWorking: Boolean = false,
    val error: String? = null,
    /** A skill of this name exists; the dialog asks before replacing it. */
    val replaceName: String? = null,
)

class SkillsActions(
    val onBack: () -> Unit,
    val onOpenSkill: (name: String) -> Unit,
    val onEnabledChange: (name: String, enabled: Boolean) -> Unit,
    val onRestoreBuiltIns: () -> Unit,
    val onOpenImport: () -> Unit,
    val onCloseImport: () -> Unit,
    val onImportLink: (link: String) -> Unit,
    val onChooseFile: () -> Unit,
    val onConfirmReplace: () -> Unit,
)

/** The skill editor: SKILL.md as text. */
data class SkillEditorUiState(
    val name: String,
    /** Null while the file is being read. */
    val savedText: String?,
    val isBuiltIn: Boolean,
    val isEdited: Boolean,
    /** Why the last save failed. */
    val error: String?,
)

class SkillEditorActions(
    val onBack: () -> Unit,
    val onSave: (text: String) -> Unit,
    val onDelete: () -> Unit,
    val onReset: () -> Unit,
)

/** Small labels after a skill's name. */
enum class SkillBadge { BUILT_IN, EDITED }

object SkillBadges {
    fun of(row: SkillRowUi): List<SkillBadge> {
        val badges = mutableListOf<SkillBadge>()
        if (row.isBuiltIn) {
            badges += SkillBadge.BUILT_IN
        }
        if (row.isEdited) {
            badges += SkillBadge.EDITED
        }
        return badges
    }
}
