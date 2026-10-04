package app.jonaki.settings

import app.jonaki.core.toolapi.SubagentBudget
import app.jonaki.core.toolapi.SubagentLimitSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class SubagentBudgetsTest {
    private val typeNames = listOf("researcher", "scout", "writer", "worker", "price-checker")

    @Test
    fun budgetsSurviveBeingSavedAsText() {
        val budgets = mapOf("scout" to SubagentBudget(4, 25, 3), "price-checker" to SubagentBudget(30, 100, 30))

        assertEquals(budgets, SubagentBudgets.fromText(SubagentBudgets.toText(budgets)))
    }

    @Test
    fun brokenLinesAreSkipped() {
        val text = "scout\t4\t25\t3\nwriter\tx\t10\t10\nworker\t5\t10\n\nresearcher\t1\t2\t3\t4"

        assertEquals(mapOf("scout" to SubagentBudget(4, 25, 3)), SubagentBudgets.fromText(text))
    }

    @Test
    fun noSavedLimitsAtAllKeepsEveryDefault() {
        val settings = SubagentBudgets.migrated(legacy = null, typeNames = typeNames)

        assertEquals(emptyMap<String, SubagentBudget>(), settings)
    }

    @Test
    fun aChangedOldGlobalBudgetIsKeptForEveryTypeButTheResearcher() {
        val legacy = SubagentBudget(toolSteps = 15, costCapCents = 30, minutes = 20)

        val budgets = SubagentBudgets.migrated(legacy, typeNames)

        assertEquals(setOf("scout", "writer", "worker", "price-checker"), budgets.keys)
        assertEquals(legacy, budgets["scout"])
        assertEquals(legacy, budgets["price-checker"])
        // The researcher has its own, larger default (20 steps, $0.20, 15 minutes).
        val settings = SubagentLimitSettings(budgets = budgets)
        assertEquals(SubagentLimitSettings.RESEARCHER_BUDGET, settings.budgetFor("researcher"))
    }

    @Test
    fun anOldGlobalBudgetThatIsTheOldDefaultAddsNothing() {
        assertEquals(emptyMap<String, SubagentBudget>(), SubagentBudgets.migrated(SubagentLimitSettings.DEFAULT_BUDGET, typeNames))
    }
}
