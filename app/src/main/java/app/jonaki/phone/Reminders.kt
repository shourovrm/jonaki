package app.jonaki.phone

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import app.jonaki.tools.phone.ReminderTiming
import java.util.UUID

/**
 * Reminders as alarms that post a notification (plan M9 step 1). The book
 * keeps every reminder until it fires, so a restart can set the alarms
 * again (D-097).
 */
class Reminders(
    private val context: Context,
    val book: ReminderBook,
) {
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    fun add(text: String, atMillis: Long): ReminderTiming {
        val reminder = Reminder(UUID.randomUUID().toString(), text, atMillis)
        book.add(reminder)
        return setAlarm(reminder.id, atMillis)
    }

    fun cancel(id: String) {
        book.remove(id)
        alarmManager.cancel(alarmIntent(id))
    }

    /** After a restart or an app update, which clear Android's alarms. */
    fun setAllAlarmsAgain() {
        val alarms = ReminderBook.alarmTimesAfterRestart(book.reminders.value, System.currentTimeMillis())
        for ((reminder, atMillis) in alarms) {
            setAlarm(reminder.id, atMillis)
        }
    }

    /** Called when an alarm goes off; null when the reminder was cancelled meanwhile. */
    fun takeDue(id: String): Reminder? = book.remove(id)

    private fun setAlarm(id: String, atMillis: Long): ReminderTiming {
        val intent = alarmIntent(id)
        if (!mayUseExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
            return ReminderTiming.INEXACT
        }
        return try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
            ReminderTiming.EXACT
        } catch (revoked: SecurityException) {
            // The user can take "Alarms & reminders" back between the check and the call.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
            ReminderTiming.INEXACT
        }
    }

    /** From Android 12, exact alarms need "Alarms & reminders", which Android 14 leaves off for new installs. */
    private fun mayUseExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }
        return alarmManager.canScheduleExactAlarms()
    }

    /** The data URI makes each reminder's intent distinct, so one alarm never replaces another. */
    private fun alarmIntent(id: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setData(Uri.parse("jonaki-reminder:$id"))
            .putExtra(ReminderReceiver.EXTRA_REMINDER_ID, id)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
