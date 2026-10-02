package app.jonaki.run

import app.jonaki.core.agent.SubagentOutcome
import app.jonaki.core.agent.SubagentRecorder
import app.jonaki.core.agent.SubagentStart
import app.jonaki.core.agent.SubagentStepStatus
import app.jonaki.core.agent.SubagentStop
import app.jonaki.core.model.ToolCall
import app.jonaki.core.providerapi.Usage
import app.jonaki.core.storage.JonakiDatabase
import app.jonaki.core.storage.StepEntity
import app.jonaki.core.storage.StepStatus
import app.jonaki.core.storage.SubagentEntity
import app.jonaki.core.storage.SubagentStatus
import app.jonaki.core.toolapi.ToolOutput
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Saves the subagents of one run as they work (D-005, D-064): a row per
 * subagent for its card, its tool calls as steps marked with its id, and
 * each model call's usage as a hidden BACKGROUND row, so that the thread's
 * cost, the month's and the usage sheet include it.
 */
class SubagentSession(
    private val threadId: String,
    private val database: JonakiDatabase,
    private val clock: () -> Long,
    /** Saves a model call's usage as a hidden row; [BackgroundModel.saveUsage]. */
    private val saveUsage: suspend (modelKey: String, usage: Usage, costUsd: Double?) -> Unit,
) : SubagentRecorder {
    // Parallel subagents write at the same time; one lock keeps message positions and cost sums whole.
    private val writeLock = Mutex()

    override suspend fun subagentStarted(start: SubagentStart) = writeLock.withLock {
        database.subagentDao().upsert(
            SubagentEntity(
                id = start.id,
                threadId = threadId,
                parentToolCallId = start.parentToolCallId,
                orderInCall = start.orderInCall,
                agentType = start.agentType,
                task = start.task,
                model = start.modelKey,
                status = SubagentStatus.RUNNING.name,
                resultText = null,
                costUsd = null,
                startedAtMillis = clock(),
                finishedAtMillis = null,
            ),
        )
    }

    override suspend fun stepStarted(subagentId: String, stepCall: ToolCall) = writeLock.withLock {
        database.stepDao().upsert(
            StepEntity(
                toolCallId = stepCall.id,
                threadId = threadId,
                toolName = stepCall.toolName,
                argumentsJson = stepCall.argumentsJson,
                status = StepStatus.RUNNING.name,
                resultText = null,
                startedAtMillis = clock(),
                finishedAtMillis = null,
                subagentId = subagentId,
            ),
        )
    }

    override suspend fun stepFinished(subagentId: String, stepCall: ToolCall, output: ToolOutput, status: SubagentStepStatus) =
        writeLock.withLock {
            val step = database.stepDao().find(stepCall.id) ?: return@withLock
            database.stepDao().upsert(
                step.copy(
                    status = stepStatusOf(status).name,
                    resultText = output.text.take(STEP_RESULT_PREVIEW_LENGTH),
                    finishedAtMillis = clock(),
                ),
            )
        }

    override suspend fun modelCallFinished(subagentId: String, modelKey: String, usage: Usage, costUsd: Double?) =
        writeLock.withLock {
            saveUsage(modelKey, usage, costUsd)
            if (costUsd != null) {
                val previous = database.subagentDao().find(subagentId)?.costUsd ?: 0.0
                database.subagentDao().setCost(subagentId, previous + costUsd)
            }
        }

    override suspend fun textWritten(subagentId: String, text: String) = writeLock.withLock {
        database.subagentDao().setLatestText(subagentId, text.trim().take(LATEST_TEXT_LENGTH))
    }

    override suspend fun subagentFinished(subagentId: String, outcome: SubagentOutcome, answerText: String) =
        writeLock.withLock {
            val row = database.subagentDao().find(subagentId) ?: return@withLock
            val now = clock()
            database.subagentDao().upsert(
                row.copy(status = statusOf(outcome.stop).name, resultText = answerText, finishedAtMillis = now),
            )
            if (outcome.stop == SubagentStop.STOPPED || outcome.stop == SubagentStop.TIME_LIMIT) {
                stopUnfinishedSteps(subagentId, now)
            }
        }

    /** A step cut off by Stop or the time limit would otherwise stay "running". */
    private suspend fun stopUnfinishedSteps(subagentId: String, now: Long) {
        val steps = database.stepDao().listOfSubagent(subagentId)
        for (step in steps) {
            val unfinished = step.status == StepStatus.RUNNING.name || step.status == StepStatus.WAITING_FOR_APPROVAL.name
            if (unfinished) {
                database.stepDao().upsert(step.copy(status = StepStatus.STOPPED.name, finishedAtMillis = now))
            }
        }
    }

    private fun stepStatusOf(status: SubagentStepStatus): StepStatus = when (status) {
        SubagentStepStatus.DONE -> StepStatus.DONE
        SubagentStepStatus.FAILED -> StepStatus.FAILED
        SubagentStepStatus.DENIED -> StepStatus.DENIED
        SubagentStepStatus.SKIPPED -> StepStatus.SKIPPED
    }

    private fun statusOf(stop: SubagentStop): SubagentStatus = when (stop) {
        SubagentStop.COMPLETED -> SubagentStatus.DONE
        SubagentStop.STEP_LIMIT -> SubagentStatus.STEP_LIMIT
        SubagentStop.COST_LIMIT -> SubagentStatus.COST_LIMIT
        SubagentStop.TIME_LIMIT -> SubagentStatus.TIME_LIMIT
        SubagentStop.FAILED -> SubagentStatus.FAILED
        SubagentStop.STOPPED -> SubagentStatus.STOPPED
    }

    private companion object {
        /** The step card shows a short preview; the subagent got the full text. */
        const val STEP_RESULT_PREVIEW_LENGTH = 2_000

        /** Enough for the folded card's line and a little more. */
        const val LATEST_TEXT_LENGTH = 400
    }
}
