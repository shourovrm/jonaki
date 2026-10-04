package app.jonaki.phone

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import app.jonaki.tools.phone.ReminderTiming
import java.util.UUID

/**
 * Reminders as alarms that post a notification (plan M9 step 1). The book
 * keeps every reminder until the user confirms it, so a restart can set the
 * alarms again, repeats included (D-097). [ReminderSchedule] decides when
 * the next ring is; this class sets the alarms.
 */
class Reminders(
    private val context: Context,
    val book: ReminderBook,
    private val policy: () -> ReminderPolicy,
) {
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    fun add(text: String, atMillis: Long): ReminderTiming {
        val reminder = Reminder(UUID.randomUUID().toString(), text, atMillis)
        book.add(reminder)
        return setAlarm(reminder.id, atMillis)
    }

    /** Removes the reminder, its alarm (the next repeat too) and its notification. */
    fun cancel(id: String) {
        book.remove(id)
        alarmManager.cancel(alarmIntent(id))
        context.getSystemService(NotificationManager::class.java).cancel(notificationId(id))
    }

    /** The user's Done: the reminder is answered for good. */
    fun confirm(id: String) = cancel(id)

    /** Rings again at [untilMillis] and starts counting repeats over; false when the reminder is already gone. */
    fun snooze(id: String, untilMillis: Long): Boolean {
        val snoozed = book.update(id) { reminder -> ReminderSchedule.snoozed(reminder, untilMillis) } ?: return false
        context.getSystemService(NotificationManager::class.java).cancel(notificationId(id))
        setAlarm(snoozed.id, untilMillis)
        return true
    }

    /** After a restart or an app update, which clear Android's alarms. */
    fun setAllAlarmsAgain() {
        val alarms = ReminderBook.alarmTimesAfterRestart(book.reminders.value, System.currentTimeMillis())
        for ((reminder, atMillis) in alarms) {
            setAlarm(reminder.id, atMillis)
        }
    }

    /**
     * Called when an alarm goes off, for the first ring and for each repeat.
     * Sets the next repeat's alarm when the policy allows one. Null when the
     * reminder was confirmed or cancelled meanwhile.
     */
    fun ring(id: String): Reminder? {
        val rung = book.update(id) { reminder -> ReminderSchedule.afterRing(reminder, policy(), System.currentTimeMillis()) }
            ?: return null
        val nextRingMillis = rung.nextRingMillis
        if (nextRingMillis != null) {
            setAlarm(rung.id, nextRingMillis)
        }
        return rung
    }

    /** The reminder's text, for the "Later…" sheet; null when it was confirmed meanwhile. */
    fun waiting(id: String): Reminder? = book.reminders.value.firstOrNull { it.id == id }

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

    /**
     * The data URI makes each reminder's intent distinct, so one alarm never
     * replaces another. A repeat uses the same intent, so setting the next
     * alarm replaces the one that just fired, and cancelling removes it.
     */
    private fun alarmIntent(id: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setData(reminderUri(id))
            .putExtra(ReminderReceiver.EXTRA_REMINDER_ID, id)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        /** The same reminder always gets the same notification, so a repeat replaces the one on screen. */
        fun notificationId(id: String): Int = id.hashCode()

        fun reminderUri(id: String): Uri = Uri.parse("jonaki-reminder:$id")
    }
}
