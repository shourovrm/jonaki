package app.jonaki.tools.phone

import app.jonaki.core.toolapi.Capability
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.TimeArguments
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import app.jonaki.core.toolapi.booleanArgument
import app.jonaki.core.toolapi.intArgument
import app.jonaki.core.toolapi.stringArgument
import java.time.Duration as JavaDuration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Calendar, reminders, notifications, the clipboard and opening apps (D-020).
 * Reading the calendar and the clipboard runs at once; every other action
 * changes something outside the app and asks the user first (D-096).
 */
class PhoneTool(
    private val phone: Phone,
    private val clock: () -> ZonedDateTime = ZonedDateTime::now,
) : Tool {
    override val name: String = "phone"

    override val promptLine: String =
        "phone: read or add calendar events, set reminders, post a notification, read or set the clipboard, open an app"

    override val guidelines: List<String> = listOf(
        "phone times are the user's local time, for example 2026-10-04T08:00; a reminder may use in_minutes instead.",
        "A phone reminder is a notification at a time; to have the agent do work at a time, use schedule.",
    )

    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") {
                    for (action in Action.entries) {
                        add(action.argument)
                    }
                }
            }
            putJsonObject("start") {
                put("type", "string")
                put("description", "calendar_list: from (default today); calendar_add: event start")
            }
            putJsonObject("end") {
                put("type", "string")
                put("description", "calendar_list: until (default 7 days on); calendar_add: event end (default 1 hour on)")
            }
            putJsonObject("all_day") { put("type", "boolean") }
            putJsonObject("title") {
                put("type", "string")
                put("description", "calendar_add, notify")
            }
            putJsonObject("location") { put("type", "string") }
            putJsonObject("text") {
                put("type", "string")
                put("description", "reminder, notify, clipboard_write; calendar_add: the event's notes")
            }
            putJsonObject("at") {
                put("type", "string")
                put("description", "reminder: when")
            }
            putJsonObject("in_minutes") {
                put("type", "integer")
                put("description", "reminder: minutes from now, instead of at")
            }
            putJsonObject("app") {
                put("type", "string")
                put("description", "open_app: the app's name as shown on the phone, or its package name")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override val sideEffect: SideEffect = SideEffect.CHANGES

    override fun sideEffectOf(arguments: JsonObject): SideEffect {
        val action = Action.fromArgument(arguments.stringArgument("action"))
        if (action == Action.CALENDAR_LIST || action == Action.CLIPBOARD_READ) {
            return SideEffect.READ_ONLY
        }
        return SideEffect.CHANGES
    }

    override val requiredCapabilities: Set<Capability> = emptySet()

    /** Long enough for the user to answer Android's permission dialog. */
    override val timeLimit: Duration = 3.minutes

    private enum class Action(val argument: String) {
        CALENDAR_LIST("calendar_list"),
        CALENDAR_ADD("calendar_add"),
        REMINDER("reminder"),
        NOTIFY("notify"),
        CLIPBOARD_READ("clipboard_read"),
        CLIPBOARD_WRITE("clipboard_write"),
        OPEN_APP("open_app"),
        ;

        companion object {
            fun fromArgument(argument: String?): Action? = entries.firstOrNull { action -> action.argument == argument }
        }
    }

    override suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput {
        val actionArgument = arguments.stringArgument("action")
        return when (Action.fromArgument(actionArgument)) {
            Action.CALENDAR_LIST -> calendarList(arguments, context)
            Action.CALENDAR_ADD -> calendarAdd(arguments)
            Action.REMINDER -> reminder(arguments)
            Action.NOTIFY -> notify(arguments)
            Action.CLIPBOARD_READ -> clipboardRead(context)
            Action.CLIPBOARD_WRITE -> clipboardWrite(arguments)
            Action.OPEN_APP -> openApp(arguments.stringArgument("app")?.trim().orEmpty())
            null -> {
                val whatFailed = if (actionArgument == null) "argument action is missing" else "action $actionArgument is unknown"
                ToolOutput.error(whatFailed, "Use one of: ${Action.entries.joinToString(", ") { it.argument }}.")
            }
        }
    }

    private suspend fun calendarList(arguments: JsonObject, context: ToolContext): ToolOutput {
        val now = clock()
        val startArgument = arguments.stringArgument("start")
        val from = if (startArgument.isNullOrBlank()) {
            now.toLocalDate().atStartOfDay(now.zone)
        } else {
            TimeArguments.parse(startArgument, now) ?: return unreadableTime("start", startArgument)
        }
        val endArgument = arguments.stringArgument("end")
        val to = if (endArgument.isNullOrBlank()) {
            from.plusDays(DEFAULT_LIST_DAYS)
        } else {
            TimeArguments.parse(endArgument, now) ?: return unreadableTime("end", endArgument)
        }
        if (!to.isAfter(from)) {
            return ToolOutput.error("end is not after start", "Give an end later than the start.")
        }
        if (JavaDuration.between(from, to).toDays() > MAX_LIST_DAYS) {
            return ToolOutput.error("the range is longer than $MAX_LIST_DAYS days", "List a shorter range, for example one month.")
        }
        val events = when (val answer = phone.calendarEvents(from, to)) {
            is PhoneAnswer.Done -> answer.value
            else -> return errorFor(answer, "calendar")
        }
        val range = "from ${from.format(DAY_AND_TIME)} to ${to.format(DAY_AND_TIME)} (local time)"
        if (events.isEmpty()) {
            return ToolOutput.success("No events $range.")
        }
        val lines = events.map { event -> describeEvent(event, now) }
        val text = "Events $range:\n" + lines.joinToString("\n")
        return ToolOutput.success(context.outputLimiter.limit(text, MAX_OUTPUT_CHARACTERS, name))
    }

    private fun describeEvent(event: CalendarEvent, now: ZonedDateTime): String {
        val details = buildList {
            add(event.title.ifBlank { "(no title)" })
            event.location?.takeIf { it.isNotBlank() }?.let { location -> add("at $location") }
            add("calendar ${event.calendarName}")
        }
        return "${describeEventTime(event, now)} ${details.joinToString(" · ")}"
    }

    private fun describeEventTime(event: CalendarEvent, now: ZonedDateTime): String {
        if (event.allDay) {
            // Android keeps all-day events at UTC midnights, and the end is the day after the last day.
            val firstDay = Instant.ofEpochMilli(event.startMillis).atZone(ZoneOffset.UTC).toLocalDate()
            val lastDay = Instant.ofEpochMilli(event.endMillis).atZone(ZoneOffset.UTC).toLocalDate().minusDays(1)
            if (!lastDay.isAfter(firstDay)) {
                return "${firstDay.format(DAY)} all day"
            }
            return "${firstDay.format(DAY)} – ${lastDay.format(DAY)} all day"
        }
        val start = Instant.ofEpochMilli(event.startMillis).atZone(now.zone)
        val end = Instant.ofEpochMilli(event.endMillis).atZone(now.zone)
        if (start.toLocalDate() == end.toLocalDate()) {
            return "${start.format(DAY_AND_TIME)}–${end.format(TIME_OF_DAY)}"
        }
        return "${start.format(DAY_AND_TIME)} – ${end.format(DAY_AND_TIME)}"
    }

    private suspend fun calendarAdd(arguments: JsonObject): ToolOutput {
        val title = arguments.stringArgument("title")?.trim().orEmpty()
        if (title.isEmpty()) {
            return ToolOutput.error("argument title is missing", "Give the event a title.")
        }
        val now = clock()
        val startArgument = arguments.stringArgument("start")?.trim().orEmpty()
        val start = TimeArguments.parse(startArgument, now) ?: return unreadableTime("start", startArgument)
        val endArgument = arguments.stringArgument("end")
        val end = if (endArgument.isNullOrBlank()) null else TimeArguments.parse(endArgument, now)
        if (!endArgument.isNullOrBlank() && end == null) {
            return unreadableTime("end", endArgument)
        }
        val allDay = arguments.booleanArgument("all_day") == true
        val event = if (allDay) {
            allDayEvent(title, start.toLocalDate(), end?.toLocalDate(), arguments)
        } else {
            timedEvent(title, start, end ?: start.plusHours(1), arguments)
        }
        if (event.endMillis <= event.startMillis) {
            return ToolOutput.error("end is not after start", "Give an end later than the start, or leave it out.")
        }
        val calendarName = when (val answer = phone.addCalendarEvent(event)) {
            is PhoneAnswer.Done -> answer.value
            else -> return errorFor(answer, "calendar")
        }
        val described = describeEventTime(
            CalendarEvent(title, event.startMillis, event.endMillis, allDay, location = null, calendarName = calendarName),
            now,
        )
        return ToolOutput.success("Added \"$title\" on $described to calendar $calendarName.")
    }

    /** [lastDay] is the last day the event covers, as a person says it; Android wants the day after. */
    private fun allDayEvent(title: String, firstDay: LocalDate, lastDay: LocalDate?, arguments: JsonObject): NewCalendarEvent {
        val dayAfter = (lastDay ?: firstDay).plusDays(1)
        return NewCalendarEvent(
            title = title,
            startMillis = firstDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            endMillis = dayAfter.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            allDay = true,
            location = arguments.stringArgument("location")?.trim()?.takeIf { it.isNotEmpty() },
            description = arguments.stringArgument("text")?.trim()?.takeIf { it.isNotEmpty() },
            timeZoneId = "UTC",
        )
    }

    private fun timedEvent(title: String, start: ZonedDateTime, end: ZonedDateTime, arguments: JsonObject) = NewCalendarEvent(
        title = title,
        startMillis = start.toInstant().toEpochMilli(),
        endMillis = end.toInstant().toEpochMilli(),
        allDay = false,
        location = arguments.stringArgument("location")?.trim()?.takeIf { it.isNotEmpty() },
        description = arguments.stringArgument("text")?.trim()?.takeIf { it.isNotEmpty() },
        timeZoneId = start.zone.id,
    )

    private suspend fun reminder(arguments: JsonObject): ToolOutput {
        val text = arguments.stringArgument("text")?.trim().orEmpty()
        if (text.isEmpty()) {
            return ToolOutput.error("argument text is missing", "Give the text the reminder shows.")
        }
        val now = clock()
        val inMinutes = arguments.intArgument("in_minutes")
        val atArgument = arguments.stringArgument("at")?.trim().orEmpty()
        val at = when {
            inMinutes != null -> now.plusMinutes(inMinutes.toLong())
            else -> TimeArguments.parse(atArgument, now) ?: return unreadableTime("at", atArgument)
        }
        if (!at.isAfter(now)) {
            return ToolOutput.error("the reminder time ${at.format(DAY_AND_TIME)} has already passed", "Give a time in the future.")
        }
        val timing = when (val answer = phone.setReminder(text, at)) {
            is PhoneAnswer.Done -> answer.value
            else -> return errorFor(answer, "notifications")
        }
        // The zone's name is left out: it would tell the provider the user's country (see ZoneInMessages).
        val reminderTime = "${at.format(DAY_AND_TIME)} (local time)"
        val timingNote = when (timing) {
            ReminderTiming.EXACT -> ""
            ReminderTiming.INEXACT -> " Exact alarms are off for Jonaki, so Android may show it some minutes late; " +
                "the user can turn on Alarms & reminders for Jonaki in Android settings."
        }
        return ToolOutput.success("Reminder set for $reminderTime: \"$text\". It rings again until the user taps Done.$timingNote")
    }

    private suspend fun notify(arguments: JsonObject): ToolOutput {
        val text = arguments.stringArgument("text")?.trim().orEmpty()
        if (text.isEmpty()) {
            return ToolOutput.error("argument text is missing", "Give the text the notification shows.")
        }
        val title = arguments.stringArgument("title")?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_NOTIFICATION_TITLE
        return when (val answer = phone.notify(title, text)) {
            is PhoneAnswer.Done -> ToolOutput.success("Posted the notification \"$title\".")
            else -> errorFor(answer, "notifications")
        }
    }

    private suspend fun clipboardRead(context: ToolContext): ToolOutput {
        val text = when (val answer = phone.readClipboard()) {
            is PhoneAnswer.Done -> answer.value
            else -> return errorFor(answer, "clipboard")
        }
        if (text.isEmpty()) {
            return ToolOutput.success("The clipboard is empty.")
        }
        return ToolOutput.success(context.outputLimiter.limit("Clipboard:\n$text", MAX_OUTPUT_CHARACTERS, name))
    }

    private suspend fun clipboardWrite(arguments: JsonObject): ToolOutput {
        val text = arguments.stringArgument("text").orEmpty()
        if (text.isEmpty()) {
            return ToolOutput.error("argument text is missing", "Give the text to copy.")
        }
        return when (val answer = phone.writeClipboard(text)) {
            is PhoneAnswer.Done -> ToolOutput.success("Copied ${text.length} characters to the clipboard.")
            else -> errorFor(answer, "clipboard")
        }
    }

    private suspend fun openApp(query: String): ToolOutput {
        if (query.isEmpty()) {
            return ToolOutput.error("argument app is missing", "Give the app's name as the phone shows it, for example Maps.")
        }
        val apps = when (val answer = phone.launchableApps()) {
            is PhoneAnswer.Done -> answer.value
            else -> return errorFor(answer, "apps")
        }
        val app = when (val match = AppMatch.find(query, apps)) {
            is AppMatch.Found -> match.app
            is AppMatch.Several -> return ToolOutput.error(
                "several apps match $query: ${match.apps.joinToString(", ") { app -> app.label }}",
                "Call again with the full name of one of them.",
            )
            AppMatch.None -> return ToolOutput.error(
                "no app on the phone is called $query",
                "Ask the user for the app's name as it appears on their phone.",
            )
        }
        return when (val answer = phone.openApp(app.packageName)) {
            is PhoneAnswer.Done -> ToolOutput.success("Opened ${app.label}.")
            else -> errorFor(answer, "apps")
        }
    }

    private fun unreadableTime(argumentName: String, value: String): ToolOutput {
        val whatFailed = if (value.isEmpty()) "argument $argumentName is missing" else "$argumentName $value is not a time"
        return ToolOutput.error(whatFailed, "Give a local time such as 2026-10-04T08:00, a date such as 2026-10-04, or 08:00.")
    }

    private fun errorFor(answer: PhoneAnswer<*>, feature: String): ToolOutput = when (answer) {
        is PhoneAnswer.Done -> error("errorFor is only called for failures")
        is PhoneAnswer.PermissionDenied -> ToolOutput.error(
            "the user has not allowed Jonaki to use ${answer.permission}",
            "Tell the user; they can allow it in Android settings > Apps > Jonaki, then ask again.",
        )
        PhoneAnswer.AppNotOnScreen -> ToolOutput.error(
            "this needs Jonaki on screen to use $feature, and it is in the background",
            "Ask the user to open Jonaki, then call phone again.",
        )
        is PhoneAnswer.Failed -> ToolOutput.error(answer.reason, "Tell the user what failed; do not retry the same call.")
    }

    private companion object {
        const val DEFAULT_LIST_DAYS = 7L
        const val MAX_LIST_DAYS = 92L
        const val MAX_OUTPUT_CHARACTERS = 8_000
        const val DEFAULT_NOTIFICATION_TITLE = "Jonaki"
        val TIME_OF_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)
        val DAY_AND_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy HH:mm", Locale.ENGLISH)
    }
}
