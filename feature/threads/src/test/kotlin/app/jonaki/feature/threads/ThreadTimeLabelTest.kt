package app.jonaki.feature.threads

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadTimeLabelTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private val now = LocalDateTime.of(2026, 10, 2, 21, 47).atZone(zone).toInstant().toEpochMilli()

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun underOneMinuteAgoIsNow() {
        assertEquals(ThreadTimeLabel.Now, ThreadTimeLabel.of(now - 30_000, now, zone))
    }

    @Test
    fun earlierTodayShowsTheClockTime() {
        assertEquals(ThreadTimeLabel.Text("21:12"), ThreadTimeLabel.of(at(2026, 10, 2, 21, 12), now, zone))
    }

    @Test
    fun withinTheLastWeekShowsTheWeekday() {
        assertEquals(ThreadTimeLabel.Text("Tue"), ThreadTimeLabel.of(at(2026, 9, 29, 10, 0), now, zone))
    }

    @Test
    fun olderShowsDayAndMonth() {
        assertEquals(ThreadTimeLabel.Text("24 Sep"), ThreadTimeLabel.of(at(2026, 9, 24, 10, 0), now, zone))
    }

    @Test
    fun anotherYearIncludesTheYear() {
        assertEquals(ThreadTimeLabel.Text("3 Oct 2025"), ThreadTimeLabel.of(at(2025, 10, 3, 10, 0), now, zone))
    }

    @Test
    fun searchMatchesTitleOrLastLineIgnoringCase() {
        val rows = listOf(
            ThreadRow("a", "Thesis — sample size", "Reading pubmed", 0, ThreadRunState.Idle),
            ThreadRow("b", "সিলেট ভ্রমণ", "ট্রেনের সময়সূচি", 0, ThreadRunState.Idle),
        )

        assertEquals(listOf("a"), filterThreads(rows, "PUBMED").map { it.id })
        assertEquals(listOf("b"), filterThreads(rows, "সিলেট").map { it.id })
        assertEquals(listOf("a", "b"), filterThreads(rows, "  ").map { it.id })
    }
}
