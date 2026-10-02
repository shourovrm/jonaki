package app.jonaki.phone

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import app.jonaki.files.VisibleActivity
import app.jonaki.tools.phone.CalendarEvent
import app.jonaki.tools.phone.LaunchableApp
import app.jonaki.tools.phone.NewCalendarEvent
import app.jonaki.tools.phone.Phone
import app.jonaki.tools.phone.PhoneAnswer
import app.jonaki.tools.phone.ReminderTiming
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The phone tool's view of Android (D-020): calendar, alarms, notifications, clipboard, launcher. */
class AndroidPhone(
    private val context: Context,
    private val permissions: RuntimePermissions,
    private val visibleActivity: VisibleActivity,
    private val reminders: Reminders,
) : Phone {
    private val calendar = PhoneCalendar(context.contentResolver)

    /** Notifications from the notify action each get their own id, so one does not replace the last. */
    private val nextNotificationId = AtomicInteger(NOTIFY_ID_START)

    override suspend fun calendarEvents(from: ZonedDateTime, to: ZonedDateTime): PhoneAnswer<List<CalendarEvent>> {
        val denial = calendarDenial(listOf(Manifest.permission.READ_CALENDAR))
        if (denial != null) {
            return denial
        }
        return withContext(Dispatchers.IO) {
            PhoneAnswer.Done(calendar.events(from.toInstant().toEpochMilli(), to.toInstant().toEpochMilli()))
        }
    }

    override suspend fun addCalendarEvent(event: NewCalendarEvent): PhoneAnswer<String> {
        // Finding a calendar to write to reads the calendar list, so both are needed.
        val denial = calendarDenial(listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
        if (denial != null) {
            return denial
        }
        val calendarName = withContext(Dispatchers.IO) { calendar.add(event) }
            ?: return PhoneAnswer.Failed("no calendar on the phone accepts new events")
        return PhoneAnswer.Done(calendarName)
    }

    override suspend fun setReminder(text: String, at: ZonedDateTime): PhoneAnswer<ReminderTiming> {
        val denial = notificationDenial()
        if (denial != null) {
            return denial
        }
        val timing = withContext(Dispatchers.IO) { reminders.add(text, at.toInstant().toEpochMilli()) }
        return PhoneAnswer.Done(timing)
    }

    override suspend fun notify(title: String, text: String): PhoneAnswer<Unit> {
        val denial = notificationDenial()
        if (denial != null) {
            return denial
        }
        JonakiNotifications.post(context, JonakiNotifications.Channel.REMINDERS, nextNotificationId.getAndIncrement(), title, text)
        return PhoneAnswer.Done(Unit)
    }

    /** Android 10 and later give the clipboard only to the app the user is looking at. */
    override suspend fun readClipboard(): PhoneAnswer<String> {
        if (!visibleActivity.isOnScreen()) {
            return PhoneAnswer.AppNotOnScreen
        }
        return withContext(Dispatchers.Main) {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            val clip = clipboard.primaryClip
            val text = if (clip == null || clip.itemCount == 0) "" else clip.getItemAt(0).coerceToText(context).toString()
            PhoneAnswer.Done(text)
        }
    }

    override suspend fun writeClipboard(text: String): PhoneAnswer<Unit> = withContext(Dispatchers.Main) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Jonaki", text))
        PhoneAnswer.Done(Unit)
    }

    /** Only launcher apps are visible to Jonaki, through the manifest's queries entry; no QUERY_ALL_PACKAGES. */
    override suspend fun launchableApps(): PhoneAnswer<List<LaunchableApp>> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launcherIntent, 0).map { info ->
            LaunchableApp(info.loadLabel(packageManager).toString(), info.activityInfo.packageName)
        }
        PhoneAnswer.Done(apps.distinctBy { app -> app.packageName })
    }

    /** Android lets an app start another app only while it is on screen. */
    override suspend fun openApp(packageName: String): PhoneAnswer<Unit> {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return PhoneAnswer.Failed("$packageName has no screen to open")
        return when (visibleActivity.start(launch)) {
            is VisibleActivity.Answer.Result -> PhoneAnswer.Done(Unit)
            VisibleActivity.Answer.NotOnScreen -> PhoneAnswer.AppNotOnScreen
            VisibleActivity.Answer.NoAppToHandle -> PhoneAnswer.Failed("$packageName could not be opened")
        }
    }

    private suspend fun calendarDenial(needed: List<String>): PhoneAnswer<Nothing>? =
        denialFor(permissions.request(needed), "the calendar")

    private suspend fun notificationDenial(): PhoneAnswer<Nothing>? =
        denialFor(permissions.requestNotifications(), "notifications")

    private fun denialFor(answer: RuntimePermissions.Answer, permission: String): PhoneAnswer<Nothing>? = when (answer) {
        RuntimePermissions.Answer.GRANTED -> null
        RuntimePermissions.Answer.DENIED -> PhoneAnswer.PermissionDenied(permission)
        RuntimePermissions.Answer.NOT_ON_SCREEN -> PhoneAnswer.AppNotOnScreen
    }

    private companion object {
        /** Above the agent service's id 1. A clash with a reminder's hashed id would only replace one notification. */
        const val NOTIFY_ID_START = 1_000
    }
}
