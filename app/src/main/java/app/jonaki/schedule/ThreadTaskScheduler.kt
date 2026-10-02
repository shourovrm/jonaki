package app.jonaki.schedule

import app.jonaki.phone.RuntimePermissions
import app.jonaki.tools.schedule.ScheduledTask
import app.jonaki.tools.schedule.TaskCreated
import app.jonaki.tools.schedule.TaskRequest
import app.jonaki.tools.schedule.TaskScheduler
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The schedule tool's view of [ScheduledTasks] from one thread, where its new tasks will run. */
class ThreadTaskScheduler(
    private val threadId: String,
    private val scheduledTasks: ScheduledTasks,
    private val permissions: RuntimePermissions,
) : TaskScheduler {
    override suspend fun create(request: TaskRequest): TaskCreated {
        // The answer arrives as a notification, so this is when Jonaki first needs the permission.
        val notificationsOn = permissions.requestNotifications() == RuntimePermissions.Answer.GRANTED
        val stored = StoredTask(
            id = scheduledTasks.newId(),
            threadId = threadId,
            title = request.title,
            prompt = request.prompt,
            repeat = request.repeat,
            anchor = request.anchor,
            nextRunAtMillis = request.firstRun.toInstant().toEpochMilli(),
        )
        withContext(Dispatchers.IO) { scheduledTasks.create(stored) }
        return TaskCreated(toolView(stored), notificationsOn)
    }

    override suspend fun list(): List<ScheduledTask> = scheduledTasks.book.tasks.value.map(::toolView)

    override suspend fun cancel(taskId: String): Boolean {
        if (scheduledTasks.book.find(taskId) == null) {
            return false
        }
        withContext(Dispatchers.IO) { scheduledTasks.cancel(taskId) }
        return true
    }

    private fun toolView(task: StoredTask) = ScheduledTask(
        id = task.id,
        title = task.title,
        prompt = task.prompt,
        repeat = task.repeat,
        anchor = task.anchor,
        nextRun = Instant.ofEpochMilli(task.nextRunAtMillis).atZone(ZoneId.systemDefault()),
        isInThisThread = task.threadId == threadId,
    )
}
