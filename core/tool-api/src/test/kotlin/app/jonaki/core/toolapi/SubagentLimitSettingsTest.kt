package app.jonaki.core.toolapi

import org.junit.Assert.assertEquals
import org.junit.Test

class SubagentLimitSettingsTest {
    @Test
    fun theDefaultsAreTheUsersRulings() {
        val defaults = SubagentLimitSettings()

        assertEquals(2, defaults.startedWithoutAsking)
        assertEquals(3, defaults.perCall)
        assertEquals(5, defaults.maxPerMessage)
        assertEquals(SubagentBudget(toolSteps = 10, costCapCents = 10, minutes = 10), defaults.budgetFor("scout"))
        assertEquals(defaults, defaults.withinBounds())
    }

    @Test
    fun theResearcherHasTheLargerDefaultBudget() {
        val researcher = SubagentLimitSettings().budgetFor("researcher")

        assertEquals(20, researcher.toolSteps)
        assertEquals(0.20, researcher.costCapUsd, 0.0)
        assertEquals(15, researcher.minutes)
    }

    @Test
    fun everyOtherTypeIncludingACustomOneHasTheStandardBudget() {
        val settings = SubagentLimitSettings()

        for (typeName in listOf("scout", "writer", "worker", "translator")) {
            assertEquals(typeName, SubagentLimitSettings.DEFAULT_BUDGET, settings.budgetFor(typeName))
        }
    }

    @Test
    fun aBudgetTheUserChangedAppliesToThatTypeOnly() {
        val settings = SubagentLimitSettings(budgets = mapOf("scout" to SubagentBudget(4, 25, 3)))

        assertEquals(SubagentBudget(4, 25, 3), settings.budgetFor("scout"))
        assertEquals(SubagentLimitSettings.DEFAULT_BUDGET, settings.budgetFor("writer"))
        assertEquals(SubagentLimitSettings.RESEARCHER_BUDGET, settings.budgetFor("researcher"))
    }

    @Test
    fun theLongestMinutesAreThoseOfTheSlowestType() {
        val settings = SubagentLimitSettings(budgets = mapOf("writer" to SubagentBudget(10, 10, 25)))

        assertEquals(25, settings.longestMinutes(listOf("researcher", "writer")))
        assertEquals(15, settings.longestMinutes(listOf("researcher", "scout")))
    }

    @Test
    fun valuesOutsideTheirRangesAreMovedIn() {
        val wild = SubagentLimitSettings(
            startedWithoutAsking = 40,
            perCall = 0,
            maxPerMessage = 99,
            budgets = mapOf("scout" to SubagentBudget(toolSteps = 0, costCapCents = 500, minutes = 0)),
        )

        val expected = SubagentLimitSettings(
            startedWithoutAsking = 10,
            perCall = 1,
            maxPerMessage = 20,
            budgets = mapOf("scout" to SubagentBudget(toolSteps = 1, costCapCents = 100, minutes = 1)),
        )
        assertEquals(expected, wild.withinBounds())
    }

    @Test
    fun theCapIsNeverBelowTheAutomaticLimit() {
        val settings = SubagentLimitSettings(startedWithoutAsking = 4, maxPerMessage = 1)

        assertEquals(4, settings.withinBounds().maxPerMessage)
    }

    @Test
    fun theCapIsAtLeastOne() {
        assertEquals(1, SubagentLimitSettings(startedWithoutAsking = 0, maxPerMessage = 0).withinBounds().maxPerMessage)
    }
}
