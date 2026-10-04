package app.jonaki.ui

import app.jonaki.core.toolapi.SubagentBudget
import app.jonaki.core.toolapi.SubagentLimitSettings
import app.jonaki.feature.settings.SubagentBudgetUi
import app.jonaki.feature.settings.SubagentLimit
import app.jonaki.feature.settings.SubagentLimitUi

/** Settings > Subagents' limit rows from the saved limits, and a row's change back into them (D-138). */
object SubagentLimitRows {
    /** The limits that count subagents per message and per call. */
    fun of(settings: SubagentLimitSettings): List<SubagentLimitUi> = listOf(
        row(SubagentLimit.WITHOUT_ASKING, settings.startedWithoutAsking, SubagentLimitSettings.STARTED_WITHOUT_ASKING_RANGE),
        row(SubagentLimit.PER_CALL, settings.perCall, SubagentLimitSettings.PER_CALL_RANGE),
        // A cap below the automatic limit would leave calls waiting for no reason, so the row stops there.
        SubagentLimitUi(
            SubagentLimit.MAX_PER_MESSAGE,
            value = settings.maxPerMessage,
            min = maxOf(settings.startedWithoutAsking, SubagentLimitSettings.MAX_PER_MESSAGE_RANGE.first),
            max = SubagentLimitSettings.MAX_PER_MESSAGE_RANGE.last,
        ),
    )

    /** One block of step, cost and minute rows for each type in [typeNames], in that order. */
    fun budgetsOf(settings: SubagentLimitSettings, typeNames: List<String>): List<SubagentBudgetUi> =
        typeNames.map { typeName -> SubagentBudgetUi(typeName, budgetRows(settings.budgetFor(typeName))) }

    private fun budgetRows(budget: SubagentBudget): List<SubagentLimitUi> = listOf(
        row(SubagentLimit.TOOL_STEPS, budget.toolSteps, SubagentLimitSettings.TOOL_STEPS_RANGE),
        SubagentLimitUi(
            SubagentLimit.COST_CENTS,
            value = budget.costCapCents,
            min = SubagentLimitSettings.COST_CAP_CENTS_RANGE.first,
            max = SubagentLimitSettings.COST_CAP_CENTS_RANGE.last,
            step = SubagentLimitSettings.COST_CAP_STEP_CENTS,
        ),
        row(SubagentLimit.MINUTES, budget.minutes, SubagentLimitSettings.MINUTES_RANGE),
    )

    /** [value] moved into its range; raising the automatic limit above the cap raises the cap too. */
    fun changed(settings: SubagentLimitSettings, limit: SubagentLimit, value: Int): SubagentLimitSettings {
        val updated = when (limit) {
            SubagentLimit.WITHOUT_ASKING -> settings.copy(startedWithoutAsking = value)
            SubagentLimit.PER_CALL -> settings.copy(perCall = value)
            SubagentLimit.MAX_PER_MESSAGE -> settings.copy(maxPerMessage = value)
            SubagentLimit.TOOL_STEPS, SubagentLimit.COST_CENTS, SubagentLimit.MINUTES ->
                error("$limit belongs to one subagent type; use budgetChanged")
        }
        return updated.withinBounds()
    }

    /** One value of one type's budget changed; the type's other two values stay as they are. */
    fun budgetChanged(settings: SubagentLimitSettings, typeName: String, limit: SubagentLimit, value: Int): SubagentLimitSettings {
        val budget = settings.budgetFor(typeName)
        val updated = when (limit) {
            SubagentLimit.TOOL_STEPS -> budget.copy(toolSteps = value)
            SubagentLimit.COST_CENTS -> budget.copy(costCapCents = value)
            SubagentLimit.MINUTES -> budget.copy(minutes = value)
            SubagentLimit.WITHOUT_ASKING, SubagentLimit.PER_CALL, SubagentLimit.MAX_PER_MESSAGE ->
                error("$limit counts subagents of a message; use changed")
        }
        return settings.copy(budgets = settings.budgets + (typeName to updated)).withinBounds()
    }

    private fun row(limit: SubagentLimit, value: Int, range: IntRange): SubagentLimitUi =
        SubagentLimitUi(limit, value = value, min = range.first, max = range.last)
}
