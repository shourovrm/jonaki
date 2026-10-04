package app.jonaki.ui

import android.content.Context
import app.jonaki.R
import app.jonaki.feature.settings.ScheduledItemUi
import app.jonaki.phone.Reminder
import app.jonaki.schedule.StoredTask
import app.jonaki.tools.schedule.Repeat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Reminders and scheduled tasks as Settings lists them, soonest first (plan M9 step 3). */
object ScheduledItems {
    private const val REMINDER_PREFIX = "reminder:"
    private const val TASK_PREFIX = "task:"

    fun of(context: Context, reminders: List<Reminder>, tasks: List<StoredTask>): List<ScheduledItemUi> {
        val reminderItems = reminders.map { reminder ->
            reminder.listedAtMillis to ScheduledItemUi(
                id = REMINDER_PREFIX + reminder.id,
                title = reminder.text,
                detail = detail(context, reminderKind(reminder), reminder.listedAtMillis),
            )
        }
        val taskItems = tasks.map { task ->
            task.nextRunAtMillis to ScheduledItemUi(
                id = TASK_PREFIX + task.id,
                title = task.title,
                detail = detail(context, kindOf(task.repeat), task.nextRunAtMillis),
            )
        }
        return (reminderItems + taskItems).sortedBy { (atMillis, _) -> atMillis }.map { (_, item) -> item }
    }

    /** The reminder id when [itemId] names a reminder, else null. */
    fun reminderId(itemId: String): String? = itemId.removePrefix(REMINDER_PREFIX).takeIf { itemId.startsWith(REMINDER_PREFIX) }

    /** The task id when [itemId] names a scheduled task, else null. */
    fun taskId(itemId: String): String? = itemId.removePrefix(TASK_PREFIX).takeIf { itemId.startsWith(TASK_PREFIX) }

    /** A reminder that has rung stays in the list until the user taps Done, so the row says so. */
    private fun reminderKind(reminder: Reminder): Int =
        if (reminder.hasRung) R.string.scheduled_waiting_for_done else R.string.reminder_notification_title

    private fun kindOf(repeat: Repeat): Int = when (repeat) {
        Repeat.NONE -> R.string.scheduled_kind_once
        Repeat.DAILY -> R.string.scheduled_kind_daily
        Repeat.WEEKLY -> R.string.scheduled_kind_weekly
    }

    private fun detail(context: Context, kind: Int, atMillis: Long): String {
        val time = Instant.ofEpochMilli(atMillis).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
        return context.getString(R.string.scheduled_item_detail, context.getString(kind), time)
    }
}
