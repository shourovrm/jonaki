package app.jonaki.tools.phone

import java.time.ZonedDateTime

/**
 * The phone features the phone tool reaches (D-020). The app implements
 * this with Android's calendar, alarms, notifications, clipboard and
 * launcher; tests use a fake. Each call asks for its Android permission the
 * first time it is needed, and suspends while the user answers.
 */
interface Phone {
    /** Event occurrences that overlap [from] to [to], from every visible calendar, in start order. */
    suspend fun calendarEvents(from: ZonedDateTime, to: ZonedDateTime): PhoneAnswer<List<CalendarEvent>>

    /** Adds the event to the phone's main writable calendar and answers that calendar's name. */
    suspend fun addCalendarEvent(event: NewCalendarEvent): PhoneAnswer<String>

    /** Saves the reminder so it survives a restart and sets an alarm that posts it as a notification at [at]. */
    suspend fun setReminder(text: String, at: ZonedDateTime): PhoneAnswer<ReminderTiming>

    /** Posts a notification now. */
    suspend fun notify(title: String, text: String): PhoneAnswer<Unit>

    /** The clipboard's text; "" when it is empty. */
    suspend fun readClipboard(): PhoneAnswer<String>

    suspend fun writeClipboard(text: String): PhoneAnswer<Unit>

    /** Apps that have an icon in the launcher. */
    suspend fun launchableApps(): PhoneAnswer<List<LaunchableApp>>

    suspend fun openApp(packageName: String): PhoneAnswer<Unit>
}

sealed interface PhoneAnswer<out T> {
    data class Done<T>(val value: T) : PhoneAnswer<T>

    /** The user refused, or turned off, the permission named in plain words ("calendar", "notifications"). */
    data class PermissionDenied(val permission: String) : PhoneAnswer<Nothing>

    /** Android only lets Jonaki ask for a permission, read the clipboard or open an app while it is on screen. */
    data object AppNotOnScreen : PhoneAnswer<Nothing>

    data class Failed(val reason: String) : PhoneAnswer<Nothing>
}

data class CalendarEvent(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    /** All-day events start and end at midnight UTC, as Android's calendar stores them. */
    val allDay: Boolean,
    val location: String?,
    val calendarName: String,
)

data class NewCalendarEvent(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val location: String?,
    val description: String?,
    /** For example "Asia/Dhaka"; "UTC" for all-day events. */
    val timeZoneId: String,
)

/** Whether Android lets Jonaki fire the reminder on the minute. */
enum class ReminderTiming {
    EXACT,

    /** "Alarms & reminders" is off for Jonaki, so Android may fire it some minutes late. */
    INEXACT,
}

data class LaunchableApp(
    val label: String,
    val packageName: String,
)
