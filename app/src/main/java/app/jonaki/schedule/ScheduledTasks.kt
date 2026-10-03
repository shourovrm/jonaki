package app.jonaki.schedule

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.jonaki.tools.schedule.NextRun
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Scheduled tasks on WorkManager (plan M9 step 2). Each run is one one-time
 * work request with a delay up to the planned time; the run plans the next
 * one, so late starts never add up (D-099).
 */
class ScheduledTasks(
    private val context: Context,
    val book: ScheduleBook,
) {
    fun create(task: StoredTask) {
        book.put(task)
        enqueue(task.id, task.nextRunAtMillis)
    }

    fun cancel(taskId: String) {
        book.remove(taskId)
        WorkManager.getInstance(context).cancelAllWorkByTag(tagFor(taskId))
    }

    /** An id not yet in use, short enough for the model to repeat ("3f2a1b9c"). */
    fun newId(): String {
        while (true) {
            val id = UUID.randomUUID().toString().take(8)
            if (book.find(id) == null) {
                return id
            }
        }
    }

    /**
     * Moves the task on before this run's prompt starts: a one-off task
     * leaves the book, a repeating one gets its next run. Planning first
     * keeps the chain alive if the run fails or the process dies.
     */
    fun planRunAfter(task: StoredTask, thisRunAtMillis: Long, nowMillis: Long) {
        val zone = ZoneId.systemDefault()
        val thisRun = ZonedDateTime.ofInstant(Instant.ofEpochMilli(thisRunAtMillis), zone)
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        val nextRun = NextRun.following(task.anchor, task.repeat, thisRun, now)
        if (nextRun == null) {
            book.remove(task.id)
            return
        }
        val nextRunAtMillis = nextRun.toInstant().toEpochMilli()
        book.put(task.copy(nextRunAtMillis = nextRunAtMillis))
        enqueue(task.id, nextRunAtMillis)
    }

    private fun enqueue(taskId: String, runAtMillis: Long) {
        val delayMillis = (runAtMillis - System.currentTimeMillis()).coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<ScheduledTaskWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            // The prompt goes to a model online; without a network the run waits for one.
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(
                Data.Builder()
                    .putString(ScheduledTaskWorker.KEY_TASK_ID, taskId)
                    .putLong(ScheduledTaskWorker.KEY_RUN_AT_MILLIS, runAtMillis)
                    .build(),
            )
            .addTag(tagFor(taskId))
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    private fun tagFor(taskId: String) = "scheduled-task:$taskId"
}
