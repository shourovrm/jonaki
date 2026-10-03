package app.jonaki.core.toolapi

import org.junit.Assert.assertEquals
import org.junit.Test

class SubagentLimitSettingsTest {
    @Test
    fun theDefaultsAreTheUsersRulings() {
        val defaults = SubagentLimitSettings()

        assertEquals(2, defaults.startedWithoutAsking)
        assertEquals(3, defaults.perCall)
        assertEquals(5, defaults.warnAbove)
        assertEquals(10, defaults.toolSteps)
        assertEquals(0.10, defaults.costCapUsd, 0.0)
        assertEquals(10, defaults.minutes)
        assertEquals(defaults, defaults.withinBounds())
    }

    @Test
    fun valuesOutsideTheirRangesAreMovedIn() {
        val wild = SubagentLimitSettings(
            startedWithoutAsking = 40,
            perCall = 0,
            warnAbove = 99,
            toolSteps = 0,
            costCapCents = 500,
            minutes = 0,
        )

        assertEquals(SubagentLimitSettings(10, 1, 20, 1, 100, 1), wild.withinBounds())
    }

    @Test
    fun theWarningIsNeverBelowTheAutomaticLimit() {
        val settings = SubagentLimitSettings(startedWithoutAsking = 4, warnAbove = 1)

        assertEquals(4, settings.withinBounds().warnAbove)
    }
}
