package app.jonaki.schedule

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.jonaki.JonakiApplication

/**
 * One planned run of a scheduled task. It plans the next run first, then
 * sends the prompt to the task's thread and posts the answer (D-100).
 */
class ScheduledTaskWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val application = applicationContext as JonakiApplication
        val scheduledTasks = application.scheduledTasks
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.success()
        val runAtMillis = inputData.getLong(KEY_RUN_AT_MILLIS, 0)
        // Cancelled, or this run was already handled before the process died.
        val task = scheduledTasks.book.find(taskId) ?: return Result.success()
        if (!task.isPlannedFor(runAtMillis)) {
            return Result.success()
        }
        scheduledTasks.planRunAfter(task, runAtMillis, System.currentTimeMillis())
        if (application.database.threadDao().find(task.threadId) == null) {
            // The thread was deleted, so its task has nowhere to run.
            scheduledTasks.cancel(task.id)
            return Result.success()
        }
        ScheduledTaskRun(application, application.runner, application.database).runAndReport(task)
        return Result.success()
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_RUN_AT_MILLIS = "run_at_millis"
    }
}
