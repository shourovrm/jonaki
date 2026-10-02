package app.jonaki.run

import app.jonaki.core.agent.AgentEvent
import app.jonaki.core.agent.ApprovalDecision
import app.jonaki.core.agent.ApprovalRequest
import app.jonaki.core.agent.ApprovalRequester
import app.jonaki.core.agent.RunOutcome
import app.jonaki.core.agent.StepRecorder
import app.jonaki.core.model.Role
import app.jonaki.core.storage.HistoryMapper
import app.jonaki.core.storage.JonakiDatabase
import app.jonaki.core.storage.MessageEntity
import app.jonaki.core.storage.StepEntity
import app.jonaki.core.storage.StepStatus
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred

/**
 * One agent run in one thread. Saves every event to the database as it
 * happens (D-005), so the chat screen, which only observes the database, shows
 * the run live and a killed app keeps everything received so far.
 */
class RunSession(
    private val threadId: String,
    private val database: JonakiDatabase,
    private val clock: () -> Long,
    /** Shows the approval card; the runner clears it once answered. */
    private val onApprovalNeeded: (PendingApproval) -> Unit,
    /** Counts steps for the thread list's "step N" label. */
    private val onStepStarted: () -> Unit,
) : StepRecorder, ApprovalRequester {

    private var streamingMessageId: String? = null
    private var streamingPosition: Long = 0
    private val streamingText = StringBuilder()
    private val deniedToolCallIds = mutableSetOf<String>()
    private val stepsOfThisRun = mutableSetOf<String>()

    override suspend fun record(event: AgentEvent) {
        when (event) {
            is AgentEvent.TextDelta -> appendStreamedText(event.text)
            is AgentEvent.ReasoningDelta -> Unit
            is AgentEvent.AssistantMessage -> saveAssistantMessage(event)
            is AgentEvent.ToolStarted -> saveStepStarted(event)
            is AgentEvent.ToolFinished -> saveToolFinished(event)
            is AgentEvent.RunFinished -> saveRunFinished(event.outcome)
        }
    }

    override suspend fun requestApproval(request: ApprovalRequest): ApprovalDecision {
        setStepStatus(request.toolCall.id, StepStatus.WAITING_FOR_APPROVAL)
        val answer = CompletableDeferred<ApprovalDecision>()
        onApprovalNeeded(PendingApproval(threadId, request.toolName, request.toolCall, answer))
        val decision = answer.await()
        if (decision == ApprovalDecision.DENY) {
            deniedToolCallIds += request.toolCall.id
            setStepStatus(request.toolCall.id, StepStatus.DENIED)
        } else {
            setStepStatus(request.toolCall.id, StepStatus.RUNNING)
        }
        return decision
    }

    private suspend fun appendStreamedText(text: String) {
        val messageDao = database.messageDao()
        val messageId = streamingMessageId ?: newMessageId().also { id ->
            streamingMessageId = id
            streamingPosition = messageDao.nextPosition(threadId)
            messageDao.upsert(
                MessageEntity(
                    id = id,
                    threadId = threadId,
                    position = streamingPosition,
                    role = Role.ASSISTANT.name,
                    text = "",
                    toolCallsJson = "[]",
                    toolCallId = null,
                    isComplete = false,
                    createdAtMillis = clock(),
                ),
            )
        }
        streamingText.append(text)
        messageDao.updateText(messageId, streamingText.toString())
    }

    private suspend fun saveAssistantMessage(event: AgentEvent.AssistantMessage) {
        val messageDao = database.messageDao()
        // Streamed text already has a row; the final message completes it in place.
        val existingId = streamingMessageId
        val position = if (existingId == null) messageDao.nextPosition(threadId) else streamingPosition
        val row = MessageEntity(
            id = existingId ?: newMessageId(),
            threadId = threadId,
            position = position,
            role = Role.ASSISTANT.name,
            text = event.message.text,
            toolCallsJson = HistoryMapper.toolCallsToJson(event.message.toolCalls),
            toolCallId = null,
            isComplete = true,
            createdAtMillis = clock(),
        )
        messageDao.upsert(row)
        streamingMessageId = null
        streamingText.clear()
    }

    private suspend fun saveStepStarted(event: AgentEvent.ToolStarted) {
        stepsOfThisRun += event.toolCall.id
        onStepStarted()
        database.stepDao().upsert(
            StepEntity(
                toolCallId = event.toolCall.id,
                threadId = threadId,
                toolName = event.toolCall.toolName,
                argumentsJson = event.toolCall.argumentsJson,
                status = StepStatus.RUNNING.name,
                resultText = null,
                startedAtMillis = clock(),
                finishedAtMillis = null,
            ),
        )
    }

    private suspend fun saveToolFinished(event: AgentEvent.ToolFinished) {
        val messageDao = database.messageDao()
        messageDao.upsert(
            MessageEntity(
                id = newMessageId(),
                threadId = threadId,
                position = messageDao.nextPosition(threadId),
                role = Role.TOOL.name,
                text = event.message.text,
                toolCallsJson = "[]",
                toolCallId = event.toolCall.id,
                isComplete = true,
                createdAtMillis = clock(),
            ),
        )
        val status = when {
            event.toolCall.id in deniedToolCallIds -> StepStatus.DENIED
            event.output.isError -> StepStatus.FAILED
            else -> StepStatus.DONE
        }
        val step = database.stepDao().find(event.toolCall.id) ?: return
        database.stepDao().upsert(
            step.copy(
                status = status.name,
                resultText = event.output.text.take(STEP_RESULT_PREVIEW_LENGTH),
                finishedAtMillis = clock(),
            ),
        )
    }

    private suspend fun saveRunFinished(outcome: RunOutcome) {
        if (outcome is RunOutcome.Stopped) {
            for (toolCallId in stepsOfThisRun) {
                val step = database.stepDao().find(toolCallId) ?: continue
                val unfinished = step.status == StepStatus.RUNNING.name ||
                    step.status == StepStatus.WAITING_FOR_APPROVAL.name
                if (unfinished) {
                    database.stepDao().upsert(step.copy(status = StepStatus.STOPPED.name, finishedAtMillis = clock()))
                }
            }
        }
        database.threadDao().touch(threadId, clock())
    }

    private suspend fun setStepStatus(toolCallId: String, status: StepStatus) {
        val step = database.stepDao().find(toolCallId) ?: return
        database.stepDao().upsert(step.copy(status = status.name))
    }

    private fun newMessageId(): String = UUID.randomUUID().toString()

    private companion object {
        /** The step card shows a short preview; the model got the full text. */
        const val STEP_RESULT_PREVIEW_LENGTH = 2_000
    }
}

/** An approval card waiting for the user's answer. */
data class PendingApproval(
    val threadId: String,
    val toolName: String,
    val toolCall: app.jonaki.core.model.ToolCall,
    val answer: CompletableDeferred<ApprovalDecision>,
)
