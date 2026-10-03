package app.jonaki.ui

import app.jonaki.core.toolapi.SubagentLimitSettings
import app.jonaki.feature.settings.SubagentLimit
import app.jonaki.feature.settings.SubagentLimitUi
import org.junit.Assert.assertEquals
import org.junit.Test

class SubagentLimitRowsTest {
    @Test
    fun theRowsShowTheSavedValuesWithTheirRanges() {
        val rows = SubagentLimitRows.of(SubagentLimitSettings(startedWithoutAsking = 3, costCapCents = 25))

        assertEquals(SubagentLimitUi.SAMPLE.map { it.limit }, rows.map { it.limit })
        assertEquals(SubagentLimitUi(SubagentLimit.WITHOUT_ASKING, value = 3, min = 0, max = 10), rows[0])
        // The warning may not go below the automatic limit.
        assertEquals(SubagentLimitUi(SubagentLimit.WARN_ABOVE, value = 5, min = 3, max = 20), rows[2])
        assertEquals(SubagentLimitUi(SubagentLimit.COST_CENTS, value = 25, min = 5, max = 100, step = 5), rows[4])
    }

    @Test
    fun theDefaultRowsMatchThePreviewSample() {
        assertEquals(SubagentLimitUi.SAMPLE, SubagentLimitRows.of(SubagentLimitSettings()))
    }

    @Test
    fun aChangeIsSavedWithinItsRange() {
        val defaults = SubagentLimitSettings()

        assertEquals(4, SubagentLimitRows.changed(defaults, SubagentLimit.PER_CALL, 4).perCall)
        assertEquals(6, SubagentLimitRows.changed(defaults, SubagentLimit.PER_CALL, 9).perCall)
        assertEquals(15, SubagentLimitRows.changed(defaults, SubagentLimit.COST_CENTS, 15).costCapCents)
        assertEquals(20, SubagentLimitRows.changed(defaults, SubagentLimit.MINUTES, 20).minutes)
        assertEquals(12, SubagentLimitRows.changed(defaults, SubagentLimit.TOOL_STEPS, 12).toolSteps)
    }

    @Test
    fun raisingTheAutomaticLimitAboveTheWarningRaisesTheWarning() {
        val changed = SubagentLimitRows.changed(SubagentLimitSettings(), SubagentLimit.WITHOUT_ASKING, 7)

        assertEquals(7, changed.startedWithoutAsking)
        assertEquals(7, changed.warnAbove)
    }
}
