package app.jonaki.phone

import app.jonaki.files.JsonListFile
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * A reminder the phone tool set. It stays in the book until the user
 * confirms it with Done or cancels it, however often it has rung.
 */
data class Reminder(
    val id: String,
    val text: String,
    /** The time it was first set for; snoozing does not change it. */
    val atMillis: Long,
    /** When it rings next; null once the last repeat has rung and only Done is left. */
    val nextRingMillis: Long? = atMillis,
    /** Repeats that have rung since it last rang for the first time. */
    val repeatsDone: Int = 0,
    /** True from its first ring until the user snoozes it, so Settings can say it waits for Done. */
    val hasRung: Boolean = false,
) {
    /** Where it sits in the soonest-first list. */
    val listedAtMillis: Long
        get() = nextRingMillis ?: atMillis
}

/**
 * The reminders the user has not confirmed, in a file, because Android
 * forgets every alarm when the phone restarts (D-097). One instance serves
 * the whole app.
 */
class ReminderBook(file: File) {
    private val storage = JsonListFile(file)
    private val current = MutableStateFlow(load())

    /** Soonest first. */
    val reminders: StateFlow<List<Reminder>> = current.asStateFlow()

    @Synchronized
    fun add(reminder: Reminder) {
        save(current.value.filterNot { it.id == reminder.id } + reminder)
    }

    /**
     * Changes one reminder and answers the changed reminder, or null when it
     * was not in the book (confirmed or cancelled meanwhile), so a late
     * alarm never brings it back.
     */
    @Synchronized
    fun update(id: String, change: (Reminder) -> Reminder): Reminder? {
        val existing = current.value.firstOrNull { it.id == id } ?: return null
        val changed = change(existing)
        save(current.value - existing + changed)
        return changed
    }

    /** The removed reminder, or null when it was not in the book (already confirmed or cancelled). */
    @Synchronized
    fun remove(id: String): Reminder? {
        val removed = current.value.firstOrNull { it.id == id } ?: return null
        save(current.value - removed)
        return removed
    }

    private fun save(reminders: List<Reminder>) {
        val sorted = reminders.sortedBy { it.listedAtMillis }
        storage.write(sorted.map(::toJson))
        current.value = sorted
    }

    private fun load(): List<Reminder> = storage.read().mapNotNull(::fromJson).sortedBy { it.listedAtMillis }

    companion object {
        /**
         * When to set each alarm again after a restart. A ring whose time
         * passed while the phone was off happens at once rather than never.
         * A reminder with no ring left needs no alarm; its notification
         * waits for Done.
         */
        fun alarmTimesAfterRestart(reminders: List<Reminder>, nowMillis: Long): List<Pair<Reminder, Long>> =
            reminders.mapNotNull { reminder ->
                val nextRingMillis = reminder.nextRingMillis ?: return@mapNotNull null
                reminder to maxOf(nextRingMillis, nowMillis)
            }

        private fun toJson(reminder: Reminder) = JsonObject(
            mapOf(
                "id" to JsonPrimitive(reminder.id),
                "text" to JsonPrimitive(reminder.text),
                "atMillis" to JsonPrimitive(reminder.atMillis),
                // A null number is written as JSON null: no ring is left.
                "nextRingMillis" to JsonPrimitive(reminder.nextRingMillis),
                "repeatsDone" to JsonPrimitive(reminder.repeatsDone),
                "hasRung" to JsonPrimitive(reminder.hasRung),
            ),
        )

        private fun fromJson(json: JsonObject): Reminder? {
            val id = (json["id"] as? JsonPrimitive)?.contentOrNull ?: return null
            val text = (json["text"] as? JsonPrimitive)?.contentOrNull ?: return null
            val atMillis = (json["atMillis"] as? JsonPrimitive)?.longOrNull ?: return null
            // Reminders stored before repeats existed have none of the newer fields.
            val nextRingMillis = if ("nextRingMillis" in json) {
                (json["nextRingMillis"] as? JsonPrimitive)?.longOrNull
            } else {
                atMillis
            }
            val repeatsDone = (json["repeatsDone"] as? JsonPrimitive)?.intOrNull ?: 0
            val hasRung = (json["hasRung"] as? JsonPrimitive)?.booleanOrNull ?: false
            return Reminder(id, text, atMillis, nextRingMillis, repeatsDone, hasRung)
        }
    }
}
