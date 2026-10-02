package app.jonaki.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.jonaki.JonakiApplication
import app.jonaki.R

/** Posts a reminder's notification when its alarm goes off. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val application = context.applicationContext as JonakiApplication
        val reminder = application.reminders.takeDue(id) ?: return
        JonakiNotifications.post(
            context,
            JonakiNotifications.Channel.REMINDERS,
            notificationId = id.hashCode(),
            title = context.getString(R.string.reminder_notification_title),
            text = reminder.text,
        )
    }

    companion object {
        const val EXTRA_REMINDER_ID = "app.jonaki.REMINDER_ID"
    }
}

/**
 * Sets the reminders' alarms again after a restart or an app update, which
 * clear them, and after the user allows exact alarms, so the next ones fire
 * on time (D-M9-2). WorkManager restores scheduled tasks by itself.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val application = context.applicationContext as JonakiApplication
        application.reminders.setAllAlarmsAgain()
    }
}
