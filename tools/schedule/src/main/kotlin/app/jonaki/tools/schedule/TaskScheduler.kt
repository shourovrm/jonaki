package app.jonaki.tools.schedule

import java.time.LocalDateTime
import java.time.ZonedDateTime

/**
 * Saves scheduled tasks and arranges their runs. The app implements this
 * for one thread with WorkManager; tests use a fake. A task runs its prompt
 * as a new message in the thread it was made in.
 */
interface TaskScheduler {
    /** Saves the task, plans its first run and asks for the notification permission if needed. */
    suspend fun create(request: TaskRequest): TaskCreated

    /** Every scheduled task, from all threads, soonest first. */
    suspend fun list(): List<ScheduledTask>

    /** False when no task has [taskId]. */
    suspend fun cancel(taskId: String): Boolean
}

data class TaskRequest(
    val title: String,
    val prompt: String,
    /** The local wall-clock time the task keeps, for example 08:00 on its first day. */
    val anchor: LocalDateTime,
    val repeat: Repeat,
    val firstRun: ZonedDateTime,
)

data class TaskCreated(
    val task: ScheduledTask,
    /** False when Android may not show Jonaki's notifications; the answer then only reaches the thread. */
    val notificationsOn: Boolean,
)

data class ScheduledTask(
    /** Short, so that the model can repeat it, for example "3f2a1b9c". */
    val id: String,
    val title: String,
    val prompt: String,
    val repeat: Repeat,
    val anchor: LocalDateTime,
    val nextRun: ZonedDateTime,
    val isInThisThread: Boolean,
)
