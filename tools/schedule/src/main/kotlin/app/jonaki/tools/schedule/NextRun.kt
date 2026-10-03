package app.jonaki.tools.schedule

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** How often a scheduled task runs. */
enum class Repeat(val argument: String) {
    NONE("none"),
    DAILY("daily"),
    WEEKLY("weekly"),
    ;

    companion object {
        fun fromArgument(argument: String?): Repeat? =
            entries.firstOrNull { repeat -> repeat.argument == argument?.trim()?.lowercase() }
    }
}

/**
 * When a task runs. A task keeps the wall-clock time of its anchor ("08:00
 * every day"), so a daylight-saving change or a trip to another time zone
 * moves the run with the local clock. WorkManager timing is inexact, so each
 * run is scheduled on its own from the anchor, and late runs never add up
 * into drift (D-099).
 */
object NextRun {
    /**
     * The first run strictly after [after], in [after]'s zone; null when a
     * one-off task's time has passed.
     */
    fun after(anchor: LocalDateTime, repeat: Repeat, after: ZonedDateTime): ZonedDateTime? {
        val zone = after.zone
        val anchorRun = ZonedDateTime.of(anchor, zone)
        if (anchorRun.isAfter(after)) {
            return anchorRun
        }
        return when (repeat) {
            Repeat.NONE -> null
            Repeat.DAILY -> firstRunFrom(after.toLocalDate(), anchor, after, daysBetweenRuns = 1)
            Repeat.WEEKLY -> {
                val sameWeekday = after.toLocalDate().with(TemporalAdjusters.nextOrSame(anchor.dayOfWeek))
                firstRunFrom(sameWeekday, anchor, after, daysBetweenRuns = 7)
            }
        }
    }

    /**
     * The run that follows the one planned for [thisRun]. Counting from the
     * later of [thisRun] and [now] means a worker that starts a moment early
     * does not run twice, and a run missed while the phone was off is not
     * followed by a burst of catch-up runs.
     */
    fun following(anchor: LocalDateTime, repeat: Repeat, thisRun: ZonedDateTime, now: ZonedDateTime): ZonedDateTime? {
        val thisRunInNowsZone = thisRun.withZoneSameInstant(now.zone)
        val from = if (thisRunInNowsZone.isAfter(now)) thisRunInNowsZone else now
        return after(anchor, repeat, from)
    }

    private fun firstRunFrom(
        firstDate: LocalDate,
        anchor: LocalDateTime,
        after: ZonedDateTime,
        daysBetweenRuns: Long,
    ): ZonedDateTime {
        val candidate = ZonedDateTime.of(firstDate, anchor.toLocalTime(), after.zone)
        if (candidate.isAfter(after)) {
            return candidate
        }
        return ZonedDateTime.of(firstDate.plusDays(daysBetweenRuns), anchor.toLocalTime(), after.zone)
    }
}
