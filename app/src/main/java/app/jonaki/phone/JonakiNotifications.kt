package app.jonaki.phone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.jonaki.MainActivity
import app.jonaki.R

/** Notifications the agent posts: reminders, the phone tool's notify, and scheduled task results. */
object JonakiNotifications {
    enum class Channel(val id: String, val nameResource: Int, val importance: Int) {
        REMINDERS("reminders", R.string.reminders_channel_name, NotificationManager.IMPORTANCE_HIGH),
        SCHEDULED_TASKS("scheduled_tasks", R.string.scheduled_tasks_channel_name, NotificationManager.IMPORTANCE_DEFAULT),
    }

    /**
     * Posts one notification that opens Jonaki when tapped. The same
     * [notificationId] replaces an earlier notification. Android drops it
     * silently when notifications are off, so callers check first.
     */
    fun post(
        context: Context,
        channel: Channel,
        notificationId: Int,
        title: String,
        text: String,
        actions: List<Notification.Action> = emptyList(),
        /** True keeps the notification on screen after a tap, for one that only a button may remove. */
        staysAfterTap: Boolean = false,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channel.id, context.getString(channel.nameResource), channel.importance))
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setAutoCancel(!staysAfterTap)
        for (action in actions) {
            builder.addAction(action)
        }
        manager.notify(notificationId, builder.build())
    }
}
