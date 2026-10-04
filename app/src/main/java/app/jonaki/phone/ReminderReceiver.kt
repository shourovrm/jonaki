package app.jonaki.phone

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import app.jonaki.JonakiApplication
import app.jonaki.R

/**
 * Handles a reminder's alarm (the first ring and each repeat) and the
 * notification's Done and "10 min" buttons. Swiping the notification away
 * does nothing here: the next repeat's alarm is already set.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val reminders = (context.applicationContext as JonakiApplication).reminders
        when (intent.action) {
            ACTION_DONE -> reminders.confirm(id)
            ACTION_SNOOZE_TEN_MINUTES -> reminders.snooze(id, System.currentTimeMillis() + QUICK_SNOOZE_MILLIS)
            else -> ring(context, reminders, id)
        }
    }

    private fun ring(context: Context, reminders: Reminders, id: String) {
        val reminder = reminders.ring(id) ?: return
        ReminderNotification.post(context, reminder)
    }

    companion object {
        const val EXTRA_REMINDER_ID = "app.jonaki.REMINDER_ID"
        const val ACTION_DONE = "app.jonaki.REMINDER_DONE"
        const val ACTION_SNOOZE_TEN_MINUTES = "app.jonaki.REMINDER_SNOOZE_TEN_MINUTES"
        private const val QUICK_SNOOZE_MILLIS = ReminderSchedule.QUICK_SNOOZE_MINUTES * 60_000L
    }
}

/** A reminder's notification with its three buttons. */
internal object ReminderNotification {
    fun post(context: Context, reminder: Reminder) {
        val actions = listOf(
            buttonThatSendsToReceiver(context, reminder.id, R.string.reminder_action_done, ReminderReceiver.ACTION_DONE),
            buttonThatSendsToReceiver(context, reminder.id, R.string.reminder_action_ten_minutes, ReminderReceiver.ACTION_SNOOZE_TEN_MINUTES),
            laterButton(context, reminder.id),
        )
        JonakiNotifications.post(
            context,
            JonakiNotifications.Channel.REMINDERS,
            notificationId = Reminders.notificationId(reminder.id),
            title = context.getString(R.string.reminder_notification_title),
            text = reminder.text,
            actions = actions,
            // Tapping the body opens the app but must not confirm, so the notification stays until Done.
            staysAfterTap = true,
        )
    }

    /** Intents differ by action and by the reminder's data URI, so no two buttons share a PendingIntent. */
    private fun buttonThatSendsToReceiver(context: Context, id: String, label: Int, action: String): Notification.Action {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            .setData(Reminders.reminderUri(id))
            .putExtra(ReminderReceiver.EXTRA_REMINDER_ID, id)
        val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return action(context, label, pending)
    }

    private fun laterButton(context: Context, id: String): Notification.Action {
        val intent = Intent(context, ReminderLaterActivity::class.java)
            .setData(Reminders.reminderUri(id))
            .putExtra(ReminderReceiver.EXTRA_REMINDER_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return action(context, R.string.reminder_action_later, pending)
    }

    private fun action(context: Context, label: Int, pending: PendingIntent): Notification.Action {
        val icon = Icon.createWithResource(context, R.drawable.ic_notification)
        return Notification.Action.Builder(icon, context.getString(label), pending).build()
    }
}

/**
 * Sets the reminders' alarms again after a restart or an app update, which
 * clear them, and after the user allows exact alarms, so the next ones fire
 * on time (D-097). Repeat alarms come back too. WorkManager restores
 * scheduled tasks by itself.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val application = context.applicationContext as JonakiApplication
        application.reminders.setAllAlarmsAgain()
        for (reminder in ReminderBook.waitingWithoutRing(application.reminders.book.reminders.value)) {
            ReminderNotification.post(context, reminder)
        }
    }
}
