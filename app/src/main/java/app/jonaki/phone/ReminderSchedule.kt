package app.jonaki.phone

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** What the "Later…" sheet offers besides a date and time of the user's own. */
enum class SnoozeChoice {
    ONE_HOUR,
    ONE_DAY,
}

/**
 * The rules for when a reminder rings next. Plain Kotlin, so the JVM tests
 * cover them; [Reminders] only sets the alarms these rules ask for.
 */
object ReminderSchedule {
    const val QUICK_SNOOZE_MINUTES = 10

    /**
     * The reminder after one of its rings, the first or a repeat. It rings
     * again after the policy's interval until [ReminderPolicy.maxRepeats]
     * repeats have happened; then no ring is planned and the notification
     * waits, silent, for Done.
     */
    fun afterRing(reminder: Reminder, policy: ReminderPolicy, nowMillis: Long): Reminder {
        val repeatsDone = if (reminder.hasRung) reminder.repeatsDone + 1 else 0
        val ringsAgain = repeatsDone < policy.maxRepeats
        return reminder.copy(
            hasRung = true,
            repeatsDone = repeatsDone,
            nextRingMillis = if (ringsAgain) nowMillis + policy.intervalMillis else null,
        )
    }

    /**
     * The reminder moved to [untilMillis]. It starts counting repeats from
     * zero again, because the user answered it. [Reminder.atMillis], the
     * time it was first set for, stays.
     */
    fun snoozed(reminder: Reminder, untilMillis: Long): Reminder =
        reminder.copy(hasRung = false, repeatsDone = 0, nextRingMillis = untilMillis)

    /** "1 day" keeps the clock time across a daylight-saving change, so it is a calendar day, not 24 hours. */
    fun snoozeTime(choice: SnoozeChoice, nowMillis: Long, zone: ZoneId): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val until = when (choice) {
            SnoozeChoice.ONE_HOUR -> now.plusHours(1)
            SnoozeChoice.ONE_DAY -> now.plusDays(1)
        }
        return until.toInstant().toEpochMilli()
    }

    /** A date and time picked on the phone, which are local, as milliseconds. */
    fun pickedTime(pickedLocal: LocalDateTime, zone: ZoneId): Long =
        pickedLocal.atZone(zone).toInstant().toEpochMilli()

    /** The user cannot snooze into the past; the sheet refuses such a pick. */
    fun isUsablePick(pickedMillis: Long, nowMillis: Long): Boolean = pickedMillis > nowMillis
}
