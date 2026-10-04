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
    fun aRestartKeepsTheRingStateOfAReminderThatWaitsForDone() {
        val book = ReminderBook(file)
        book.add(Reminder("a", "Pills", atMillis = 1_000, nextRingMillis = 8_000, repeatsDone = 2, hasRung = true))
        book.add(Reminder("b", "Call mum", atMillis = 2_000, nextRingMillis = null, repeatsDone = 5, hasRung = true))

        val afterRestart = ReminderBook(file).reminders.value.associateBy { it.id }

        assertEquals(Reminder("a", "Pills", 1_000, 8_000, 2, true), afterRestart.getValue("a"))
        assertEquals(Reminder("b", "Call mum", 2_000, null, 5, true), afterRestart.getValue("b"))
    }

    @Test
    fun remindersStoredBeforeRepeatsExistedStillLoadAsNotYetRung() {
        file.writeText("""[{"id":"old","text":"Old one","atMillis":4000}]""")

        val loaded = ReminderBook(file).reminders.value.single()

        assertEquals(Reminder("old", "Old one", atMillis = 4_000, nextRingMillis = 4_000, repeatsDone = 0, hasRung = false), loaded)
    }

    @Test
    fun updateChangesOneReminderAndAnswersNullForOneThatIsGone() {
        val book = ReminderBook(file)
        book.add(Reminder("a", "Pills", atMillis = 1_000))

        val changed = book.update("a") { it.copy(repeatsDone = 3) }

        assertEquals(3, changed?.repeatsDone)
        assertEquals(3, ReminderBook(file).reminders.value.single().repeatsDone)
        book.remove("a")
        assertNull(book.update("a") { it.copy(repeatsDone = 4) })
        assertTrue(book.reminders.value.isEmpty())
    }

    @Test
    fun aRestartSetsRepeatAlarmsAtTheirNextRingAndSkipsRemindersWithNoRingLeft() {
        val repeating = Reminder("r", "Repeating", atMillis = 1_000, nextRingMillis = 9_000, repeatsDone = 1, hasRung = true)
        val overdueRepeat = Reminder("o", "Overdue", atMillis = 1_000, nextRingMillis = 2_000, repeatsDone = 1, hasRung = true)
        val waitingForDone = Reminder("w", "Waiting", atMillis = 1_000, nextRingMillis = null, repeatsDone = 5, hasRung = true)

        val alarms = ReminderBook.alarmTimesAfterRestart(listOf(repeating, overdueRepeat, waitingForDone), nowMillis = 5_000)

        assertEquals(listOf(repeating to 9_000L, overdueRepeat to 5_000L), alarms)
    }

    @Test
    fun aRestartShowsAgainOnlyRemindersThatRangAndHaveNoRingLeft() {
        val notYetRung = Reminder("n", "Not yet", atMillis = 9_000)
        val repeating = Reminder("r", "Repeating", atMillis = 1_000, nextRingMillis = 9_000, repeatsDone = 1, hasRung = true)
        val waitingForDone = Reminder("w", "Waiting", atMillis = 1_000, nextRingMillis = null, repeatsDone = 5, hasRung = true)

        val shownAgain = ReminderBook.waitingWithoutRing(listOf(notYetRung, repeating, waitingForDone))

        assertEquals(listOf(waitingForDone), shownAgain)
    }

    @Test
    fun theListIsSortedByTheNextRingNotTheOriginalTime() {
        val book = ReminderBook(file)
        book.add(Reminder("early", "Snoozed", atMillis = 1_000, nextRingMillis = 9_000))
        book.add(Reminder("late", "Soon", atMillis = 5_000))

        assertEquals(listOf("late", "early"), book.reminders.value.map { it.id })
    }

    @Test
    fun anUnreadableFileGivesAnEmptyBookInsteadOfACrash() {
        file.writeText("not json")

        assertTrue(ReminderBook(file).reminders.value.isEmpty())
    }
}
