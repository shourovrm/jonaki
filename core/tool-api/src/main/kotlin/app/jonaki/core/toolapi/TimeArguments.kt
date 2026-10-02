package app.jonaki.core.toolapi

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Reads a time a model wrote in a tool argument (phone and schedule). The
 * user's message carries the current time (D-005), so models give absolute
 * times; this accepts the forms they use.
 */
object TimeArguments {
    private val dateWithSpace = Regex("""^(\d{4}-\d{2}-\d{2}) (\d)""")
    private val timeOfDay = DateTimeFormatter.ofPattern("H:mm[:ss]")

    /**
     * Accepts "2026-10-04T08:00" or "2026-10-04 08:00" (with or without
     * seconds) in [now]'s zone, the same with an offset ("Z", "+06:00"), a
     * date alone (its midnight) and a time alone ("08:00", the next such
     * time after [now]). Returns null for anything else.
     */
    fun parse(text: String, now: ZonedDateTime): ZonedDateTime? {
        val trimmed = text.trim().replace(dateWithSpace, "$1T$2")
        if (trimmed.isEmpty()) {
            return null
        }
        parseOrNull { OffsetDateTime.parse(trimmed) }?.let { withOffset ->
            return withOffset.atZoneSameInstant(now.zone)
        }
        parseOrNull { LocalDateTime.parse(trimmed) }?.let { local ->
            return local.atZone(now.zone)
        }
        parseOrNull { LocalDate.parse(trimmed) }?.let { date ->
            return date.atStartOfDay(now.zone)
        }
        parseOrNull { LocalTime.parse(trimmed, timeOfDay) }?.let { time ->
            return nextOccurrence(time, now)
        }
        return null
    }

    /** Today at [time] if that is still ahead of [now], otherwise tomorrow. */
    private fun nextOccurrence(time: LocalTime, now: ZonedDateTime): ZonedDateTime {
        val today = ZonedDateTime.of(now.toLocalDate(), time, now.zone)
        if (today.isAfter(now)) {
            return today
        }
        return ZonedDateTime.of(now.toLocalDate().plusDays(1), time, now.zone)
    }

    private fun <T> parseOrNull(parse: () -> T): T? =
        try {
            parse()
        } catch (notThisForm: DateTimeParseException) {
            null
        }
}
