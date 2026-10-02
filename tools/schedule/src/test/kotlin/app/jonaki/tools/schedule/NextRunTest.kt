package app.jonaki.tools.schedule

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextRunTest {
    private val dhaka = ZoneId.of("Asia/Dhaka")

    private fun dhaka(month: Int, day: Int, hour: Int, minute: Int, second: Int = 0) =
        ZonedDateTime.of(2026, month, day, hour, minute, second, 0, dhaka)

    /** Saturday 3 October 2026, 08:00. */
    private val anchor = LocalDateTime.of(2026, 10, 3, 8, 0)

    @Test
    fun aOneOffTaskRunsAtItsTime() {
        assertEquals(dhaka(10, 3, 8, 0), NextRun.after(anchor, Repeat.NONE, dhaka(10, 3, 7, 0)))
    }

    @Test
    fun aOneOffTaskWhoseTimeHasPassedHasNoNextRun() {
        assertNull(NextRun.after(anchor, Repeat.NONE, dhaka(10, 3, 8, 0)))
    }

    @Test
    fun aDailyTaskFirstRunsAtItsAnchor() {
        assertEquals(dhaka(10, 3, 8, 0), NextRun.after(anchor, Repeat.DAILY, dhaka(10, 2, 9, 0)))
    }

    @Test
    fun aDailyTaskRunsLaterTodayWhenTheTimeIsAhead() {
        assertEquals(dhaka(10, 9, 8, 0), NextRun.after(anchor, Repeat.DAILY, dhaka(10, 9, 7, 59)))
    }

    @Test
    fun aDailyTaskRunsTomorrowOnceTodaysTimeHasPassed() {
        assertEquals(dhaka(10, 10, 8, 0), NextRun.after(anchor, Repeat.DAILY, dhaka(10, 9, 8, 0)))
        assertEquals(dhaka(10, 10, 8, 0), NextRun.after(anchor, Repeat.DAILY, dhaka(10, 9, 23, 59)))
    }

    @Test
    fun aWeeklyTaskKeepsItsWeekday() {
        // The anchor is a Saturday; Wednesday 7 October is followed by Saturday 10 October.
        assertEquals(dhaka(10, 10, 8, 0), NextRun.after(anchor, Repeat.WEEKLY, dhaka(10, 7, 12, 0)))
        assertEquals(dhaka(10, 17, 8, 0), NextRun.after(anchor, Repeat.WEEKLY, dhaka(10, 10, 8, 0)))
    }

    @Test
    fun aDailyTaskKeepsItsClockTimeAcrossADaylightSavingChange() {
        val newYork = ZoneId.of("America/New_York")
        val sevenInTheMorning = LocalDateTime.of(2026, 11, 1, 7, 0)
        // Clocks go back on 1 November 2026; the next run is still 07:00 local time.
        val after = ZonedDateTime.of(2026, 11, 1, 7, 0, 0, 0, newYork)
        assertEquals(ZonedDateTime.of(2026, 11, 2, 7, 0, 0, 0, newYork), NextRun.after(sevenInTheMorning, Repeat.DAILY, after))
    }

    @Test
    fun aTimeInTheSpringGapMovesForwardByTheGap() {
        val newYork = ZoneId.of("America/New_York")
        val halfPastTwo = LocalDateTime.of(2026, 3, 1, 2, 30)
        // 02:30 does not exist on 8 March 2026 in New York; it becomes 03:30.
        val after = ZonedDateTime.of(2026, 3, 8, 1, 0, 0, 0, newYork)
        assertEquals(ZonedDateTime.of(2026, 3, 8, 3, 30, 0, 0, newYork), NextRun.after(halfPastTwo, Repeat.DAILY, after))
    }

    @Test
    fun theRunAfterThisOneSkipsTodayEvenWhenTheWorkerStartedEarly() {
        // WorkManager started the 08:00 run a second early; tomorrow must follow, not 08:00 today again.
        val thisRun = dhaka(10, 9, 8, 0)
        val now = dhaka(10, 9, 7, 59, 59)
        assertEquals(dhaka(10, 10, 8, 0), NextRun.following(anchor, Repeat.DAILY, thisRun, now))
    }

    @Test
    fun theRunAfterAMissedRunIsTheNextOneFromNowWithoutCatchingUp() {
        // The phone was off for three days; one late run happens now, the next is tomorrow.
        val missedRun = dhaka(10, 6, 8, 0)
        val now = dhaka(10, 9, 10, 0)
        assertEquals(dhaka(10, 10, 8, 0), NextRun.following(anchor, Repeat.DAILY, missedRun, now))
    }

    @Test
    fun aOneOffTaskHasNoFollowingRun() {
        assertNull(NextRun.following(anchor, Repeat.NONE, dhaka(10, 3, 8, 0), dhaka(10, 3, 8, 0, 1)))
    }
}
