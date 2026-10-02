package app.jonaki.schedule

import app.jonaki.tools.schedule.Repeat
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleBookTest {
    private val file = File(Files.createTempDirectory("schedule").toFile(), "scheduled-tasks.json")

    private val daily = StoredTask(
        id = "3f2a1b9c",
        threadId = "thread-1",
        title = "News",
        prompt = "Summarise the news on X",
        repeat = Repeat.DAILY,
        anchor = LocalDateTime.of(2026, 10, 4, 8, 0),
        nextRunAtMillis = 2_000,
    )

    @Test
    fun tasksSurviveARestartWithEveryField() {
        ScheduleBook(file).put(daily)

        assertEquals(listOf(daily), ScheduleBook(file).tasks.value)
    }

    @Test
    fun putReplacesATaskWithTheSameId() {
        val book = ScheduleBook(file)
        book.put(daily)
        book.put(daily.copy(nextRunAtMillis = 9_000))

        assertEquals(9_000L, ScheduleBook(file).find(daily.id)?.nextRunAtMillis)
        assertEquals(1, book.tasks.value.size)
    }

    @Test
    fun removeTakesTheTaskOut() {
        val book = ScheduleBook(file)
        book.put(daily)

        assertEquals(daily, book.remove(daily.id))
        assertNull(book.remove(daily.id))
        assertTrue(ScheduleBook(file).tasks.value.isEmpty())
    }

    @Test
    fun aWorkerStartedAgainAfterItsNextRunWasPlannedDoesNotRun() {
        assertTrue(daily.isPlannedFor(2_000))
        // The first worker moved the task on to its next run, then the process died.
        val movedOn = daily.copy(nextRunAtMillis = 86_402_000)
        assertFalse(movedOn.isPlannedFor(2_000))
    }
}
