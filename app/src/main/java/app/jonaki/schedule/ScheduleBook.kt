package app.jonaki.schedule

import app.jonaki.files.JsonListFile
import app.jonaki.tools.schedule.Repeat
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** A task the schedule tool made. WorkManager holds only its next run; the rest lives here. */
data class StoredTask(
    val id: String,
    val threadId: String,
    val title: String,
    val prompt: String,
    val repeat: Repeat,
    /** The local wall-clock time the task keeps (D-M9-4). */
    val anchor: LocalDateTime,
    val nextRunAtMillis: Long,
) {
    /**
     * Whether a worker planned for [runAtMillis] should run. Each worker plans
     * the next run before it starts the prompt, so a worker that WorkManager
     * starts again after the process died finds a later time here and does
     * not run the prompt twice or plan a second chain.
     */
    fun isPlannedFor(runAtMillis: Long): Boolean = nextRunAtMillis == runAtMillis
}

/** Scheduled tasks in a file, so that Settings and the schedule tool can list them (D-M9-3). */
class ScheduleBook(file: File) {
    private val storage = JsonListFile(file)
    private val current = MutableStateFlow(load())

    /** Soonest first. */
    val tasks: StateFlow<List<StoredTask>> = current.asStateFlow()

    fun find(id: String): StoredTask? = current.value.firstOrNull { it.id == id }

    @Synchronized
    fun put(task: StoredTask) {
        save(current.value.filterNot { it.id == task.id } + task)
    }

    @Synchronized
    fun remove(id: String): StoredTask? {
        val removed = find(id) ?: return null
        save(current.value - removed)
        return removed
    }

    private fun save(tasks: List<StoredTask>) {
        val sorted = tasks.sortedBy { it.nextRunAtMillis }
        storage.write(sorted.map(::toJson))
        current.value = sorted
    }

    private fun load(): List<StoredTask> = storage.read().mapNotNull(::fromJson).sortedBy { it.nextRunAtMillis }

    private companion object {
        fun toJson(task: StoredTask) = JsonObject(
            mapOf(
                "id" to JsonPrimitive(task.id),
                "threadId" to JsonPrimitive(task.threadId),
                "title" to JsonPrimitive(task.title),
                "prompt" to JsonPrimitive(task.prompt),
                "repeat" to JsonPrimitive(task.repeat.argument),
                "anchor" to JsonPrimitive(task.anchor.toString()),
                "nextRunAtMillis" to JsonPrimitive(task.nextRunAtMillis),
            ),
        )

        fun fromJson(json: JsonObject): StoredTask? {
            fun text(key: String) = (json[key] as? JsonPrimitive)?.contentOrNull
            val anchor = try {
                LocalDateTime.parse(text("anchor") ?: return null)
            } catch (unreadable: DateTimeParseException) {
                return null
            }
            return StoredTask(
                id = text("id") ?: return null,
                threadId = text("threadId") ?: return null,
                title = text("title") ?: return null,
                prompt = text("prompt") ?: return null,
                repeat = Repeat.fromArgument(text("repeat")) ?: return null,
                anchor = anchor,
                nextRunAtMillis = (json["nextRunAtMillis"] as? JsonPrimitive)?.longOrNull ?: return null,
            )
        }
    }
}
