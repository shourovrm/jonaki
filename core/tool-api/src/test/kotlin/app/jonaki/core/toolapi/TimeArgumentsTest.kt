package app.jonaki.core.toolapi

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeArgumentsTest {
    private val dhaka = ZoneId.of("Asia/Dhaka")

    /** Saturday 3 October 2026, 14:30 in Dhaka (UTC+6). */
    private val now = ZonedDateTime.of(2026, 10, 3, 14, 30, 0, 0, dhaka)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, dhaka)

    @Test
    fun aDateAndTimeWithoutOffsetIsLocal() {
        assertEquals(at(2026, 10, 4, 8, 0), TimeArguments.parse("2026-10-04T08:00", now))
    }

    @Test
    fun aSpaceMaySeparateDateAndTime() {
        assertEquals(at(2026, 10, 4, 8, 0), TimeArguments.parse("2026-10-04 08:00", now))
    }

    @Test
    fun secondsAreAccepted() {
        assertEquals(at(2026, 10, 4, 8, 0), TimeArguments.parse("2026-10-04T08:00:00", now))
    }

    @Test
    fun anOffsetIsConvertedToTheLocalZone() {
        assertEquals(at(2026, 10, 4, 8, 0), TimeArguments.parse("2026-10-04T02:00Z", now))
        assertEquals(at(2026, 10, 4, 8, 0), TimeArguments.parse("2026-10-04T04:00+02:00", now))
    }

    @Test
    fun aDateAloneIsItsMidnight() {
        assertEquals(at(2026, 10, 5, 0, 0), TimeArguments.parse("2026-10-05", now))
    }

    @Test
    fun aTimeLaterTodayIsToday() {
        assertEquals(at(2026, 10, 3, 18, 15), TimeArguments.parse("18:15", now))
    }

    @Test
    fun aTimeThatHasPassedTodayIsTomorrow() {
        assertEquals(at(2026, 10, 4, 8, 0), TimeArguments.parse("08:00", now))
        assertEquals(at(2026, 10, 4, 14, 30), TimeArguments.parse("14:30", now))
    }

    @Test
    fun aOneDigitHourIsAccepted() {
        assertEquals(at(2026, 10, 4, 8, 5), TimeArguments.parse("8:05", now))
    }

    @Test
    fun textThatIsNoTimeGivesNull() {
        assertNull(TimeArguments.parse("tomorrow morning", now))
        assertNull(TimeArguments.parse("", now))
        assertNull(TimeArguments.parse("2026-13-40T08:00", now))
    }
}
