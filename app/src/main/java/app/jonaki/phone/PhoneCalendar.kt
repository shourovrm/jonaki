package app.jonaki.phone

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import app.jonaki.tools.phone.CalendarEvent
import app.jonaki.tools.phone.NewCalendarEvent

/** Reads and adds events through Android's calendar provider (CalendarContract). */
class PhoneCalendar(private val resolver: ContentResolver) {
    /** Occurrences of events, repeating ones included, from calendars the user shows. */
    fun events(fromMillis: Long, toMillis: Long): List<CalendarEvent> {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uri, fromMillis)
        ContentUris.appendId(uri, toMillis)
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
        )
        val events = mutableListOf<CalendarEvent>()
        resolver.query(
            uri.build(),
            projection,
            "${CalendarContract.Instances.VISIBLE} = 1",
            null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cursor ->
            while (cursor.moveToNext() && events.size < MAX_EVENTS) {
                events += CalendarEvent(
                    title = cursor.getString(0).orEmpty(),
                    startMillis = cursor.getLong(1),
                    endMillis = cursor.getLong(2),
                    allDay = cursor.getInt(3) == 1,
                    location = cursor.getString(4),
                    calendarName = cursor.getString(5).orEmpty(),
                )
            }
        }
        return events
    }

    /** The calendar's name, or null when no calendar on the phone takes new events. */
    fun add(event: NewCalendarEvent): String? {
        val calendar = writableCalendar() ?: return null
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendar.first)
            put(CalendarContract.Events.TITLE, event.title)
            put(CalendarContract.Events.DTSTART, event.startMillis)
            put(CalendarContract.Events.DTEND, event.endMillis)
            put(CalendarContract.Events.ALL_DAY, if (event.allDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, event.timeZoneId)
            event.location?.let { location -> put(CalendarContract.Events.EVENT_LOCATION, location) }
            event.description?.let { description -> put(CalendarContract.Events.DESCRIPTION, description) }
        }
        resolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return null
        return calendar.second
    }

    /** The primary calendar the user shows and may write to, else the first such calendar: id and name. */
    private fun writableCalendar(): Pair<Long, String>? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
        )
        val selection = "${CalendarContract.Calendars.VISIBLE} = 1 AND " +
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}"
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            null,
            "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars._ID} ASC",
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0) to cursor.getString(1).orEmpty()
            }
        }
        return null
    }

    private companion object {
        /** Enough for three months of a busy calendar; the tool cuts long text further. */
        const val MAX_EVENTS = 300
    }
}
