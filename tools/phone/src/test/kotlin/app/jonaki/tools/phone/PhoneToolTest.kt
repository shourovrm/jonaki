package app.jonaki.tools.phone

import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneToolTest {
    /** Records what was asked; [denial] replaces every answer when set. */
    private class FakePhone : Phone {
        var denial: PhoneAnswer<Nothing>? = null
        var events = emptyList<CalendarEvent>()
        var listedRange: Pair<ZonedDateTime, ZonedDateTime>? = null
        val addedEvents = mutableListOf<NewCalendarEvent>()
        val reminders = mutableListOf<Pair<String, ZonedDateTime>>()
        var reminderTiming = ReminderTiming.EXACT
        val notifications = mutableListOf<Pair<String, String>>()
        var clipboard = ""
        var apps = emptyList<LaunchableApp>()
        val openedPackages = mutableListOf<String>()

        private fun <T> answer(value: T): PhoneAnswer<T> = denial ?: PhoneAnswer.Done(value)

        override suspend fun calendarEvents(from: ZonedDateTime, to: ZonedDateTime): PhoneAnswer<List<CalendarEvent>> {
            listedRange = from to to
            return answer(events)
        }

        override suspend fun addCalendarEvent(event: NewCalendarEvent): PhoneAnswer<String> {
            denial?.let { return it }
            addedEvents += event
            return PhoneAnswer.Done("Personal")
        }

        override suspend fun setReminder(text: String, at: ZonedDateTime): PhoneAnswer<ReminderTiming> {
            denial?.let { return it }
            reminders += text to at
            return PhoneAnswer.Done(reminderTiming)
        }

        override suspend fun notify(title: String, text: String): PhoneAnswer<Unit> {
            denial?.let { return it }
            notifications += title to text
            return PhoneAnswer.Done(Unit)
        }

        override suspend fun readClipboard(): PhoneAnswer<String> = answer(clipboard)

        override suspend fun writeClipboard(text: String): PhoneAnswer<Unit> {
            denial?.let { return it }
            clipboard = text
            return PhoneAnswer.Done(Unit)
        }

        override suspend fun launchableApps(): PhoneAnswer<List<LaunchableApp>> = answer(apps)

        override suspend fun openApp(packageName: String): PhoneAnswer<Unit> {
            denial?.let { return it }
            openedPackages += packageName
            return PhoneAnswer.Done(Unit)
        }
    }

    private val dhaka = ZoneId.of("Asia/Dhaka")

    /** Saturday 3 October 2026, 14:30. */
    private val now = ZonedDateTime.of(2026, 10, 3, 14, 30, 0, 0, dhaka)
    private val phone = FakePhone()
    private val tool = PhoneTool(phone, clock = { now })
    private val context = ToolContext(Files.createTempDirectory("thread").toFile(), OkHttpClient())

    private fun run(vararg arguments: Pair<String, Any>): ToolOutput = runBlocking {
        val json = arguments.associate { (key, value) ->
            key to when (value) {
                is Boolean -> JsonPrimitive(value)
                is Int -> JsonPrimitive(value)
                else -> JsonPrimitive(value.toString())
            }
        }
        tool.run(JsonObject(json), context)
    }

    private fun dhaka(day: Int, hour: Int, minute: Int) = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, dhaka)

    private fun millis(time: ZonedDateTime) = time.toInstant().toEpochMilli()

    @Test
    fun readingActionsRunWithoutApprovalAndTheRestAsk() {
        fun costOf(action: String) = tool.sideEffectOf(JsonObject(mapOf("action" to JsonPrimitive(action))))

        assertEquals(SideEffect.READ_ONLY, costOf("calendar_list"))
        assertEquals(SideEffect.READ_ONLY, costOf("clipboard_read"))
        for (action in listOf("calendar_add", "reminder", "notify", "clipboard_write", "open_app")) {
            assertEquals(action, SideEffect.CHANGES, costOf(action))
        }
    }

    @Test
    fun calendarListDefaultsToTheNextSevenDaysFromMidnight() {
        run("action" to "calendar_list")

        assertEquals(dhaka(3, 0, 0) to dhaka(10, 0, 0), phone.listedRange)
    }

    @Test
    fun calendarListShowsTimedAndAllDayEvents() {
        val holiday = LocalDate.of(2026, 10, 5).atStartOfDay(ZoneOffset.UTC)
        phone.events = listOf(
            CalendarEvent("Dentist", millis(dhaka(4, 9, 0)), millis(dhaka(4, 10, 0)), false, "Clinic", "Personal"),
            CalendarEvent("Holiday", millis(holiday), millis(holiday.plusDays(1)), true, null, "Holidays"),
        )

        val output = run("action" to "calendar_list")

        assertFalse(output.text, output.isError)
        assertTrue(output.text, output.text.contains("Sun 4 Oct 2026 09:00–10:00 Dentist · at Clinic · calendar Personal"))
        assertTrue(output.text, output.text.contains("Mon 5 Oct 2026 all day Holiday · calendar Holidays"))
    }

    @Test
    fun anEmptyCalendarSaysSo() {
        val output = run("action" to "calendar_list", "start" to "2026-10-04", "end" to "2026-10-05")

        assertFalse(output.isError)
        assertTrue(output.text, output.text.startsWith("No events from Sun 4 Oct 2026 00:00 to Mon 5 Oct 2026 00:00"))
    }

    @Test
    fun calendarAddDefaultsToOneHour() {
        val output = run("action" to "calendar_add", "title" to "Dentist", "start" to "2026-10-04T09:00", "location" to "Clinic")

        val event = phone.addedEvents.single()
        assertEquals(millis(dhaka(4, 9, 0)), event.startMillis)
        assertEquals(millis(dhaka(4, 10, 0)), event.endMillis)
        assertEquals("Asia/Dhaka", event.timeZoneId)
        assertEquals("Clinic", event.location)
        assertEquals("Added \"Dentist\" on Sun 4 Oct 2026 09:00–10:00 to calendar Personal.", output.text)
    }

    @Test
    fun anAllDayEventCoversItsLastDayAtUtcMidnights() {
        run("action" to "calendar_add", "title" to "Trip", "start" to "2026-10-04", "end" to "2026-10-06", "all_day" to true)

        val event = phone.addedEvents.single()
        assertTrue(event.allDay)
        assertEquals("UTC", event.timeZoneId)
        assertEquals(LocalDate.of(2026, 10, 4).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), event.startMillis)
        assertEquals(LocalDate.of(2026, 10, 7).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), event.endMillis)
    }

    @Test
    fun calendarAddWithAnEndBeforeTheStartIsRefused() {
        val output = run("action" to "calendar_add", "title" to "X", "start" to "2026-10-04T09:00", "end" to "2026-10-04T08:00")

        assertTrue(output.isError)
        assertTrue(phone.addedEvents.isEmpty())
    }

    @Test
    fun aReminderInMinutesCountsFromNow() {
        val output = run("action" to "reminder", "text" to "Take the pills", "in_minutes" to 20)

        assertEquals("Take the pills" to dhaka(3, 14, 50), phone.reminders.single())
        assertEquals("Reminder set for Sat 3 Oct 2026 14:50 (Asia/Dhaka): \"Take the pills\".", output.text)
    }

    @Test
    fun anInexactReminderSaysItMayBeLate() {
        phone.reminderTiming = ReminderTiming.INEXACT

        val output = run("action" to "reminder", "text" to "Call mum", "at" to "18:00")

        assertFalse(output.isError)
        assertTrue(output.text, output.text.contains("some minutes late"))
    }

    @Test
    fun aReminderInThePastIsRefused() {
        val output = run("action" to "reminder", "text" to "Late", "at" to "2026-10-03T09:00")

        assertTrue(output.isError)
        assertTrue(phone.reminders.isEmpty())
    }

    @Test
    fun aDeniedPermissionNamesItAndSaysWhereToAllowIt() {
        phone.denial = PhoneAnswer.PermissionDenied("calendar")

        val output = run("action" to "calendar_list")

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("not allowed Jonaki to use calendar"))
        assertTrue(output.text, output.text.contains("Android settings"))
    }

    @Test
    fun anActionThatNeedsTheScreenSaysToOpenJonaki() {
        phone.denial = PhoneAnswer.AppNotOnScreen

        val output = run("action" to "clipboard_read")

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("open Jonaki"))
    }

    @Test
    fun notifyUsesJonakiAsTheDefaultTitle() {
        run("action" to "notify", "text" to "Done")

        assertEquals("Jonaki" to "Done", phone.notifications.single())
    }

    @Test
    fun theClipboardIsReadAndWritten() {
        assertEquals("The clipboard is empty.", run("action" to "clipboard_read").text)

        run("action" to "clipboard_write", "text" to "hello")

        assertEquals("Clipboard:\nhello", run("action" to "clipboard_read").text)
    }

    @Test
    fun openAppPrefersAnExactName() {
        phone.apps = listOf(LaunchableApp("Maps", "com.google.android.apps.maps"), LaunchableApp("Maps Go", "com.example.mapsgo"))

        val output = run("action" to "open_app", "app" to "maps")

        assertEquals("Opened Maps.", output.text)
        assertEquals(listOf("com.google.android.apps.maps"), phone.openedPackages)
    }

    @Test
    fun openAppWithSeveralPartialMatchesListsThem() {
        phone.apps = listOf(LaunchableApp("Google Maps", "a.maps"), LaunchableApp("Maps Go", "b.maps"))

        val output = run("action" to "open_app", "app" to "map")

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("Google Maps, Maps Go"))
        assertTrue(phone.openedPackages.isEmpty())
    }

    @Test
    fun openAppByPackageNameAndAnUnknownApp() {
        phone.apps = listOf(LaunchableApp("Calculator", "com.android.calculator2"))

        assertEquals("Opened Calculator.", run("action" to "open_app", "app" to "com.android.calculator2").text)
        assertTrue(run("action" to "open_app", "app" to "Banking").isError)
    }

    @Test
    fun anUnknownActionListsTheActions() {
        val output = run("action" to "sms")

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("calendar_list"))
    }
}
