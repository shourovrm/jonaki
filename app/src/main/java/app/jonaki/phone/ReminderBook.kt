package app.jonaki.phone

import app.jonaki.files.JsonListFile
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** A reminder the phone tool set; it leaves the book when its notification is posted. */
data class Reminder(
    val id: String,
    val text: String,
    val atMillis: Long,
)

/**
 * The reminders waiting to fire, in a file, because Android forgets every
 * alarm when the phone restarts (D-097). One instance serves the whole app.
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

    /** The removed reminder, or null when it was not in the book (already fired or cancelled). */
    @Synchronized
    fun remove(id: String): Reminder? {
        val removed = current.value.firstOrNull { it.id == id } ?: return null
        save(current.value - removed)
        return removed
    }

    private fun save(reminders: List<Reminder>) {
        val sorted = reminders.sortedBy { it.atMillis }
        storage.write(sorted.map(::toJson))
        current.value = sorted
    }

    private fun load(): List<Reminder> = storage.read().mapNotNull(::fromJson).sortedBy { it.atMillis }

    companion object {
        /**
         * When to set each alarm again after a restart. A reminder whose time
         * passed while the phone was off fires at once rather than never.
         */
        fun alarmTimesAfterRestart(reminders: List<Reminder>, nowMillis: Long): List<Pair<Reminder, Long>> =
            reminders.map { reminder -> reminder to maxOf(reminder.atMillis, nowMillis) }

        private fun toJson(reminder: Reminder) = JsonObject(
            mapOf(
                "id" to JsonPrimitive(reminder.id),
                "text" to JsonPrimitive(reminder.text),
                "atMillis" to JsonPrimitive(reminder.atMillis),
            ),
        )

        private fun fromJson(json: JsonObject): Reminder? {
            val id = (json["id"] as? JsonPrimitive)?.contentOrNull ?: return null
            val text = (json["text"] as? JsonPrimitive)?.contentOrNull ?: return null
            val atMillis = (json["atMillis"] as? JsonPrimitive)?.longOrNull ?: return null
            return Reminder(id, text, atMillis)
        }
    }
}
