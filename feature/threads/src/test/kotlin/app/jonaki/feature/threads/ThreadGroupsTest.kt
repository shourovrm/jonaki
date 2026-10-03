package app.jonaki.feature.threads

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadGroupsTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private val now = LocalDateTime.of(2026, 10, 2, 21, 47).atZone(zone).toInstant().toEpochMilli()

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun thread(id: String, updatedAtMillis: Long, incognito: Boolean = false) =
        ThreadRow(id, id, "", updatedAtMillis, ThreadRunState.Idle, incognito = incognito)

    @Test
    fun earlyThisMorningIsToday() {
        assertEquals(ThreadGroup.TODAY, ThreadGroup.of(at(2026, 10, 2, 0, 5), now, zone))
    }

    @Test
    fun lateLastNightIsYesterday() {
        assertEquals(ThreadGroup.YESTERDAY, ThreadGroup.of(at(2026, 10, 1, 23, 59), now, zone))
    }

    @Test
    fun twoToSixDaysAgoIsTheLastSevenDays() {
        assertEquals(ThreadGroup.LAST_SEVEN_DAYS, ThreadGroup.of(at(2026, 9, 30, 12, 0), now, zone))
        assertEquals(ThreadGroup.LAST_SEVEN_DAYS, ThreadGroup.of(at(2026, 9, 26, 12, 0), now, zone))
    }

    @Test
    fun aWeekAgoIsOlder() {
        assertEquals(ThreadGroup.OLDER, ThreadGroup.of(at(2026, 9, 25, 12, 0), now, zone))
    }

    @Test
    fun aTimeInTheFutureCountsAsToday() {
        assertEquals(ThreadGroup.TODAY, ThreadGroup.of(at(2026, 10, 3, 9, 0), now, zone))
    }

    @Test
    fun aLabelGoesBeforeEachGroupOnce() {
        val threads = listOf(
            thread("a", at(2026, 10, 2, 20, 0)),
            thread("b", at(2026, 10, 2, 8, 0)),
            thread("c", at(2026, 10, 1, 8, 0)),
            thread("d", at(2026, 9, 1, 8, 0)),
        )

        val entries = withGroupLabels(threads, now, zone)

        val shape = entries.map { entry ->
            when (entry) {
                is ThreadListEntry.Label -> entry.group.name
                is ThreadListEntry.Thread -> entry.row.id
            }
        }
        assertEquals(listOf("TODAY", "a", "b", "YESTERDAY", "c", "OLDER", "d"), shape)
    }

    @Test
    fun labelKeysStayUniqueWhenAGroupComesBack() {
        val threads = listOf(
            thread("a", at(2026, 10, 2, 20, 0)),
            thread("b", at(2026, 10, 1, 8, 0)),
            thread("c", at(2026, 10, 2, 8, 0)),
        )

        val keys = withGroupLabels(threads, now, zone).map { entry -> entry.key }

        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun noThreadsGiveNoLabels() {
        assertEquals(emptyList<ThreadListEntry>(), withGroupLabels(emptyList(), now, zone))
    }

    @Test
    fun theIncognitoViewKeepsOnlyIncognitoThreads() {
        val threads = listOf(thread("a", now), thread("b", now, incognito = true), thread("c", now))

        assertEquals(listOf("b"), incognitoThreads(threads).map { thread -> thread.id })
    }
}
