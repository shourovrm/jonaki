package app.jonaki.settings

import app.jonaki.core.toolapi.SubagentBudget
import app.jonaki.core.toolapi.SubagentLimitSettings

/** How the budgets of the subagent types are saved, and how the one global budget of older versions becomes them. */
object SubagentBudgets {
    // One line per type: name, steps, cents and minutes, split at tabs like the model choices.
    fun toText(budgets: Map<String, SubagentBudget>): String = budgets.entries.joinToString("\n") { (typeName, budget) ->
        "$typeName\t${budget.toolSteps}\t${budget.costCapCents}\t${budget.minutes}"
    }

    fun fromText(text: String): Map<String, SubagentBudget> =
        text.lines().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size != 4 || parts[0].isBlank()) {
                return@mapNotNull null
            }
            val numbers = parts.drop(1).map { part -> part.toIntOrNull() }
            if (numbers.any { number -> number == null }) {
                return@mapNotNull null
            }
            parts[0] to SubagentBudget(numbers[0]!!, numbers[1]!!, numbers[2]!!)
        }.toMap()

    /**
     * The budgets for a user who has the single budget of earlier versions
     * (the same steps, cost and minutes for every type). Every type but the
     * researcher keeps it, so that a value the user changed is not lost; the
     * researcher gets its own default. [legacy] is null when the user never
     * saved one, and a legacy budget equal to the default adds nothing.
     */
    fun migrated(legacy: SubagentBudget?, typeNames: List<String>): Map<String, SubagentBudget> {
        if (legacy == null || legacy == SubagentLimitSettings.DEFAULT_BUDGET) {
            return emptyMap()
        }
        return typeNames
            .filter { typeName -> typeName != SubagentLimitSettings.RESEARCHER }
            .associateWith { legacy }
    }
}
