package app.jonaki.settings

import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.Tool
import app.jonaki.feature.settings.ApprovalRuleChoiceUi

/**
 * The actions Settings offers an "always allow" rule for, read from what the
 * tools declare. A tool whose calls only read, or only change Jonaki's own
 * records, never asks, so a rule would do nothing; delegate asks only above
 * the subagent cap, which no rule covers.
 */
object ApprovalRuleChoices {
    private val ASKING_EFFECTS = setOf(
        SideEffect.CHANGES,
        SideEffect.CHANGES_REVERSIBLE,
        SideEffect.CHANGES_THREAD_FOLDER,
    )

    fun of(tools: List<Tool>): List<ApprovalRuleChoiceUi> {
        val choices = mutableListOf<ApprovalRuleChoiceUi>()
        for (tool in tools.sortedBy { tool -> tool.name }) {
            if (tool.sideEffect !in ASKING_EFFECTS) {
                continue
            }
            if (tool.ruleActions.isEmpty()) {
                choices += ApprovalRuleChoiceUi(tool.name, action = null, detailName = tool.ruleDetailName)
                continue
            }
            for (action in tool.ruleActions) {
                choices += ApprovalRuleChoiceUi(tool.name, action, detailName = tool.ruleDetailName)
            }
        }
        return choices
    }
}
