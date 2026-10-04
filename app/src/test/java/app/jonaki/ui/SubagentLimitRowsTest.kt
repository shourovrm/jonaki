package app.jonaki.ui

import app.jonaki.core.toolapi.SubagentBudget
import app.jonaki.core.toolapi.SubagentLimitSettings
import app.jonaki.feature.settings.SubagentBudgetUi
import app.jonaki.feature.settings.SubagentLimit
import app.jonaki.feature.settings.SubagentLimitUi
import org.junit.Assert.assertEquals
import org.junit.Test

class SubagentLimitRowsTest {
    private val builtInTypes = listOf("researcher", "scout", "writer", "worker")

    @Test
    fun thePerMessageRowsShowTheSavedValuesWithTheirRanges() {
        val rows = SubagentLimitRows.of(SubagentLimitSettings(startedWithoutAsking = 3, maxPerMessage = 8))

        assertEquals(listOf(SubagentLimit.WITHOUT_ASKING, SubagentLimit.PER_CALL, SubagentLimit.MAX_PER_MESSAGE), rows.map { it.limit })
        assertEquals(SubagentLimitUi(SubagentLimit.WITHOUT_ASKING, value = 3, min = 0, max = 10), rows[0])
        assertEquals(SubagentLimitUi(SubagentLimit.MAX_PER_MESSAGE, value = 8, min = 3, max = 20), rows[2])
    }

    @Test
    fun theCapMayNotGoBelowTheAutomaticLimitOrBelowOne() {
        val none = SubagentLimitRows.of(SubagentLimitSettings(startedWithoutAsking = 0)).last()

        assertEquals(1, none.min)
    }

    @Test
    fun theDefaultRowsMatchThePreviewSample() {
        assertEquals(SubagentLimitUi.SAMPLE, SubagentLimitRows.of(SubagentLimitSettings()))
        assertEquals(SubagentBudgetUi.SAMPLE, SubagentLimitRows.budgetsOf(SubagentLimitSettings(), builtInTypes))
    }

    @Test
    fun everyTypeGetsItsOwnBudgetRows() {
        val settings = SubagentLimitSettings(budgets = mapOf("scout" to SubagentBudget(4, 25, 3)))

        val budgets = SubagentLimitRows.budgetsOf(settings, builtInTypes + "price-checker")

        assertEquals(builtInTypes + "price-checker", budgets.map { it.agentType })
        val researcher = budgets.first { it.agentType == "researcher" }.limits
        assertEquals(listOf(20, 20, 15), researcher.map { it.value })
        val scout = budgets.first { it.agentType == "scout" }.limits
        assertEquals(SubagentLimitUi(SubagentLimit.TOOL_STEPS, value = 4, min = 1, max = 30), scout[0])
        assertEquals(SubagentLimitUi(SubagentLimit.COST_CENTS, value = 25, min = 5, max = 100, step = 5), scout[1])
        assertEquals(SubagentLimitUi(SubagentLimit.MINUTES, value = 3, min = 1, max = 30), scout[2])
        assertEquals(listOf(10, 10, 10), budgets.first { it.agentType == "price-checker" }.limits.map { it.value })
    }

    @Test
    fun aPerMessageChangeIsSavedWithinItsRange() {
        val defaults = SubagentLimitSettings()

        assertEquals(4, SubagentLimitRows.changed(defaults, SubagentLimit.PER_CALL, 4).perCall)
        assertEquals(6, SubagentLimitRows.changed(defaults, SubagentLimit.PER_CALL, 9).perCall)
        assertEquals(20, SubagentLimitRows.changed(defaults, SubagentLimit.MAX_PER_MESSAGE, 50).maxPerMessage)
    }

    @Test
    fun raisingTheAutomaticLimitAboveTheCapRaisesTheCap() {
        val changed = SubagentLimitRows.changed(SubagentLimitSettings(), SubagentLimit.WITHOUT_ASKING, 7)

        assertEquals(7, changed.startedWithoutAsking)
        assertEquals(7, changed.maxPerMessage)
    }

    @Test
    fun aBudgetChangeAppliesToThatTypeAndKeepsItsOtherValues() {
        val defaults = SubagentLimitSettings()

        val changed = SubagentLimitRows.budgetChanged(defaults, "researcher", SubagentLimit.TOOL_STEPS, 25)

        assertEquals(SubagentBudget(toolSteps = 25, costCapCents = 20, minutes = 15), changed.budgetFor("researcher"))
        assertEquals(SubagentLimitSettings.DEFAULT_BUDGET, changed.budgetFor("scout"))
    }

    @Test
    fun aBudgetChangeIsMovedIntoItsRange() {
        val defaults = SubagentLimitSettings()

        assertEquals(15, SubagentLimitRows.budgetChanged(defaults, "scout", SubagentLimit.COST_CENTS, 15).budgetFor("scout").costCapCents)
        assertEquals(30, SubagentLimitRows.budgetChanged(defaults, "scout", SubagentLimit.MINUTES, 99).budgetFor("scout").minutes)
        assertEquals(1, SubagentLimitRows.budgetChanged(defaults, "scout", SubagentLimit.TOOL_STEPS, 0).budgetFor("scout").toolSteps)
    }

    @Test
    fun aBudgetChangeOfACustomTypeAddsItsEntry() {
        val changed = SubagentLimitRows.budgetChanged(SubagentLimitSettings(), "price-checker", SubagentLimit.MINUTES, 12)

        assertEquals(SubagentBudget(toolSteps = 10, costCapCents = 10, minutes = 12), changed.budgetFor("price-checker"))
    }
}
