package app.jonaki.phone

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderScheduleTest {
    private val tenMinutes = 600_000L
    private val policy = ReminderPolicy(intervalMinutes = 10, maxRepeats = 5)
    private val fresh = Reminder("a", "Pills", atMillis = 1_000)

    @Test
    fun theFirstRingPlansTheFirstRepeatAfterTheInterval() {
        val afterFirstRing = ReminderSchedule.afterRing(fresh, policy, nowMillis = 2_000)

        assertTrue(afterFirstRing.hasRung)
        assertEquals(0, afterFirstRing.repeatsDone)
        assertEquals(2_000 + tenMinutes, afterFirstRing.nextRingMillis)
    }

    @Test
    fun theRepeatIntervalIsCountedFromWhenTheAlarmActuallyFired() {
        val late = ReminderSchedule.afterRing(fresh, policy, nowMillis = 5_000_000)

        assertEquals(5_000_000 + tenMinutes, late.nextRingMillis)
    }

    @Test
    fun repeatsStopAfterTheLastOneAndOnlyDoneIsLeft() {
        var reminder = fresh
        var rings = 0
        var now = 1_000L
        while (reminder.nextRingMillis != null || !reminder.hasRung) {
            reminder = ReminderSchedule.afterRing(reminder, policy, now)
            rings += 1
            now += tenMinutes
        }

        assertEquals("the first ring and five repeats", 6, rings)
        assertEquals(5, reminder.repeatsDone)
        assertNull(reminder.nextRingMillis)
    }

    @Test
    fun zeroMaximumRepeatsRingsOnceAndWaits() {
        val onlyOnce = ReminderPolicy(intervalMinutes = 10, maxRepeats = 0)

        val afterFirstRing = ReminderSchedule.afterRing(fresh, onlyOnce, nowMillis = 2_000)

        assertTrue(afterFirstRing.hasRung)
        assertNull(afterFirstRing.nextRingMillis)
    }

    @Test
    fun snoozingMovesTheRingAndStartsTheRepeatsOverButKeepsTheOriginalTime() {
        val rungThreeTimes = Reminder("a", "Pills", atMillis = 1_000, nextRingMillis = null, repeatsDone = 3, hasRung = true)

        val snoozed = ReminderSchedule.snoozed(rungThreeTimes, untilMillis = 9_000_000)

        assertEquals(1_000L, snoozed.atMillis)
        assertEquals(9_000_000L, snoozed.nextRingMillis)
        assertEquals(0, snoozed.repeatsDone)
        assertFalse(snoozed.hasRung)
    }

    @Test
    fun aSnoozedReminderRepeatsAgainAfterItsNextRing() {
        val snoozed = ReminderSchedule.snoozed(fresh.copy(hasRung = true, repeatsDone = 5), untilMillis = 9_000_000)

        val afterRing = ReminderSchedule.afterRing(snoozed, policy, nowMillis = 9_000_000)

        assertEquals(0, afterRing.repeatsDone)
        assertEquals(9_000_000 + tenMinutes, afterRing.nextRingMillis)
    }

    @Test
    fun oneHourAndOneDayKeepTheClockTimeAcrossASpringForward() {
        val zone = ZoneId.of("America/New_York")
        // 2026-03-07 12:00 in New York; the clocks go forward on the night after.
        val saturdayNoon = LocalDateTime.of(2026, 3, 7, 12, 0).atZone(zone).toInstant().toEpochMilli()

        val oneHour = ReminderSchedule.snoozeTime(SnoozeChoice.ONE_HOUR, saturdayNoon, zone)
        val oneDay = ReminderSchedule.snoozeTime(SnoozeChoice.ONE_DAY, saturdayNoon, zone)

        assertEquals(saturdayNoon + 3_600_000L, oneHour)
        val sundayNoon = LocalDateTime.of(2026, 3, 8, 12, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(sundayNoon, oneDay)
    }

    @Test
    fun aPickedLocalTimeBecomesMillisInTheGivenZone() {
        val zone = ZoneId.of("Asia/Dhaka")

        val picked = ReminderSchedule.pickedTime(LocalDateTime.of(2026, 10, 5, 8, 30), zone)

        // Dhaka is UTC+6, so 08:30 there is 02:30 UTC.
        assertEquals(LocalDateTime.of(2026, 10, 5, 2, 30).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli(), picked)
    }

    @Test
    fun aPickInThePastIsNotUsable() {
        assertTrue(ReminderSchedule.isUsablePick(pickedMillis = 2_000, nowMillis = 1_000))
        assertFalse(ReminderSchedule.isUsablePick(pickedMillis = 1_000, nowMillis = 1_000))
    }

    @Test
    fun policyValuesOutsideTheOptionsAreMovedIntoThem() {
        val stored = ReminderPolicy(intervalMinutes = 7, maxRepeats = 99).withinBounds()

        assertEquals(10, stored.intervalMinutes)
        assertEquals(10, stored.maxRepeats)
        assertEquals(60 * 60_000L, ReminderPolicy(intervalMinutes = 60).withinBounds().intervalMillis)
    }
}
