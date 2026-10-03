package app.jonaki.schedule

import android.content.Context
import app.jonaki.R
import app.jonaki.core.model.Role
import app.jonaki.core.storage.HistoryMapper
import app.jonaki.core.storage.JonakiDatabase
import app.jonaki.phone.JonakiNotifications
import app.jonaki.run.AgentRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs a scheduled task's prompt as a new message in its thread, through the
 * same runner and foreground service as a typed message, and posts the
 * answer as a notification (D-100).
 */
class ScheduledTaskRun(
    private val context: Context,
    private val runner: AgentRunner,
    private val database: JonakiDatabase,
) {
    suspend fun runAndReport(task: StoredTask) {
        val startedAtMillis = withTimeoutOrNull(RUN_LIMIT_MINUTES * 60_000L) { runInThread(task) }
        if (startedAtMillis == null) {
            runner.stop(task.threadId)
            post(task, context.getString(R.string.scheduled_task_stopped, RUN_LIMIT_MINUTES))
            return
        }
        post(task, answerSince(task.threadId, startedAtMillis))
    }

    /** Waits while a run the user started is going, sends the prompt, and waits for its run to end. */
    private suspend fun runInThread(task: StoredTask): Long {
        val threadId = task.threadId
        var startedAtMillis: Long
        while (true) {
            runner.runningThreadIds.first { running -> threadId !in running }
            startedAtMillis = System.currentTimeMillis()
            // The screen sends on the main thread too, so the two never start a run at once.
            val sent = withContext(Dispatchers.Main) { runner.sendIfIdle(threadId, messageFor(task)) }
            if (sent) {
                break
            }
        }
        coroutineScope {
            val approvalWatch = launch {
                runner.pendingApprovals.first { pending -> threadId in pending }
                post(task, context.getString(R.string.scheduled_task_needs_approval))
            }
            runner.runningThreadIds.first { running -> threadId !in running }
            approvalWatch.cancel()
        }
        return startedAtMillis
    }

    /** The run's last answer or error, cut to what a notification shows well. */
    private suspend fun answerSince(threadId: String, startedAtMillis: Long): String {
        val last = database.messageDao().listThread(threadId).lastOrNull { row ->
            val isAnswerOrError = row.role == Role.ASSISTANT.name || row.role == HistoryMapper.ERROR_ROLE
            row.createdAtMillis >= startedAtMillis && isAnswerOrError && row.text.isNotBlank()
        } ?: return context.getString(R.string.scheduled_task_no_answer)
        val text = last.text.trim().take(MAX_NOTIFICATION_CHARACTERS)
        if (last.role == HistoryMapper.ERROR_ROLE) {
            return context.getString(R.string.scheduled_task_failed, text)
        }
        return text
    }

    /** One notification per task; the answer replaces the approval request. */
    private fun post(task: StoredTask, text: String) {
        JonakiNotifications.post(context, JonakiNotifications.Channel.SCHEDULED_TASKS, task.id.hashCode(), task.title, text)
    }

    private fun messageFor(task: StoredTask) = "Scheduled task \"${task.title}\": ${task.prompt}"

    private companion object {
        /** WorkManager stops a worker after 10 minutes; this leaves time to stop the run and post. */
        const val RUN_LIMIT_MINUTES = 9
        const val MAX_NOTIFICATION_CHARACTERS = 2_000
    }
}
