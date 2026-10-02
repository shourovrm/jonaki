package app.jonaki.phone

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderBookTest {
    private val folder = Files.createTempDirectory("reminders").toFile()
    private val file = File(folder, "reminders.json")

    @Test
    fun remindersSurviveARestartSoonestFirst() {
        val book = ReminderBook(file)
        book.add(Reminder("b", "Call mum", atMillis = 2_000))
        book.add(Reminder("a", "Pills", atMillis = 1_000))

        val afterRestart = ReminderBook(file)

        assertEquals(listOf("a", "b"), afterRestart.reminders.value.map { it.id })
    }

    @Test
    fun removeAnswersTheReminderOnlyOnce() {
        val book = ReminderBook(file)
        book.add(Reminder("a", "Pills", atMillis = 1_000))

        assertEquals("Pills", book.remove("a")?.text)
        assertNull(book.remove("a"))
        assertTrue(ReminderBook(file).reminders.value.isEmpty())
    }

    @Test
    fun aRestartSetsFutureRemindersAtTheirTimeAndMissedOnesAtOnce() {
        val missed = Reminder("missed", "Missed while off", atMillis = 1_000)
        val later = Reminder("later", "Still ahead", atMillis = 9_000)

        val alarms = ReminderBook.alarmTimesAfterRestart(listOf(missed, later), nowMillis = 5_000)

        assertEquals(listOf(missed to 5_000L, later to 9_000L), alarms)
    }

    @Test
    fun anUnreadableFileGivesAnEmptyBookInsteadOfACrash() {
        file.writeText("not json")

        assertTrue(ReminderBook(file).reminders.value.isEmpty())
    }
}
