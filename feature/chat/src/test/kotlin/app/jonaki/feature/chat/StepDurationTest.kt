package app.jonaki.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class StepDurationTest {
    @Test
    fun underTenSecondsShowsOneDecimal() {
        assertEquals("2.1 s", formatStepDuration(2_140))
        assertEquals("0.4 s", formatStepDuration(380))
    }

    @Test
    fun tenSecondsToAMinuteShowsWholeSeconds() {
        assertEquals("14 s", formatStepDuration(14_499))
        assertEquals("59 s", formatStepDuration(59_400))
    }

    @Test
    fun aMinuteOrMoreShowsMinutesAndSeconds() {
        assertEquals("1:00", formatStepDuration(60_000))
        assertEquals("3:07", formatStepDuration(187_200))
    }

    @Test
    fun runSummaryCountsSteps() {
        val steps = listOf(
            StepUi("a", "web_search", StepUiStatus.DONE, "", durationMillis = 2_000),
            StepUi("b", "web_fetch", StepUiStatus.DONE, "", durationMillis = 3_000),
        )

        assertEquals(5_000L, totalDurationMillis(steps))
    }
}
