package app.jonaki.run

import androidx.room.withTransaction
import app.jonaki.core.agent.AgentEvent
import app.jonaki.core.agent.ApprovalDecision
import app.jonaki.core.agent.ApprovalRequest
import app.jonaki.core.agent.ApprovalRequester
import app.jonaki.core.agent.RequestTimer
import app.jonaki.core.agent.RunOutcome
import app.jonaki.core.agent.StepRecorder
import app.jonaki.core.agent.SubagentAsk
import app.jonaki.core.model.Role
import app.jonaki.core.providerapi.Usage
import app.jonaki.core.storage.HistoryMapper
import app.jonaki.core.storage.JonakiDatabase
import app.jonaki.core.storage.MessageEntity
import app.jonaki.core.storage.StepEntity
import app.jonaki.core.storage.StepStatus
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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
    /** Milliseconds since boot (SystemClock.elapsedRealtime), for the request-log times (D-132). */
    elapsedClock: () -> Long,
    /** Shows the approval card; the runner clears it once answered. */
    private val onApprovalNeeded: (PendingApproval) -> Unit,
    /** Removes a card nobody answered in time (a subagent's 3-minute rule, D-062), or one cut off by Stop. */
    private val onApprovalWithdrawn: (PendingApproval) -> Unit,
    /** Counts steps for the thread list's "step N" label. */
    private val onStepStarted: () -> Unit,
    /** "service:modelId" of this run's model, saved with each call's usage (D-027). */
    private val modelKey: String,
    /** Cost in USD of one call: the service's own figure, else priced from the catalog. */
    private val priceOf: (Usage) -> Double?,
) : StepRecorder, ApprovalRequester {

    /** Set by the provider during a call; saved on that call's assistant message (D-030). */
    @Volatile
    private var routingFellBack = false

    /** Called by the OpenRouter provider when a private-only request ran on the cheapest endpoint. */
    fun markRoutingFallback() {
        routingFellBack = true
    }

    private val requestTimer = RequestTimer(elapsedClock = elapsedClock, wallClock = clock)

    private var streamingMessageId: String? = null
    private var streamingPosition: Long = 0
    private val streamingText = StringBuilder()
    private val streamingReasoning = StringBuilder()
    // Only the thread agent's denials; a subagent's steps get their status from SubagentSession.
    private val deniedToolCallIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val stepsOfThisRun = mutableSetOf<String>()

    override suspend fun record(event: AgentEvent) {
        val isFirstVisibleText = requestTimer.observe(event)
        when (event) {
            // The timer has noted the time; the row is created by the first chunk.
            AgentEvent.RequestSent -> Unit
            is AgentEvent.TextDelta -> appendStreamedText(event.text)
            is AgentEvent.ReasoningDelta -> appendStreamedReasoning(event.text)
            is AgentEvent.AssistantMessage -> saveAssistantMessage(event)
            is AgentEvent.ToolStarted -> saveStepStarted(event)
            is AgentEvent.ToolFinished -> saveToolFinished(event)
            is AgentEvent.RunFinished -> saveRunFinished(event.outcome)
        }
        if (isFirstVisibleText) {
            saveRequestTimes()
        }
    }

    /** Saved at the first visible text, before the turn ends, so the chat can mark its first draw against it. */
    private suspend fun saveRequestTimes() {
        val times = requestTimer.current ?: return
        val messageId = streamingMessageId ?: return
        val firstTextElapsedMillis = times.firstTextElapsedMillis ?: return
        database.messageDao().updateRequestTimes(messageId, times.sentAtMillis, times.sentElapsedMillis, firstTextElapsedMillis)
    }

    override suspend fun requestApproval(request: ApprovalRequest): ApprovalDecision {
        setStepStatus(request.toolCall.id, StepStatus.WAITING_FOR_APPROVAL)
        val answer = CompletableDeferred<ApprovalDecision>()
        val pending = PendingApproval(threadId, request.toolName, request.toolCall, answer, request.subagent)
        onApprovalNeeded(pending)
        val decision = try {
            answer.await()
        } finally {
            onApprovalWithdrawn(pending)
        }
        if (decision == ApprovalDecision.DENY) {
            if (request.subagent == null) {
                deniedToolCallIds += request.toolCall.id
            }
            setStepStatus(request.toolCall.id, StepStatus.DENIED)
        } else {
            // The step's time counts from approval, not from the wait for the user.
            val step = database.stepDao().find(request.toolCall.id)
            if (step != null) {
                database.stepDao().upsert(step.copy(status = StepStatus.RUNNING.name, startedAtMillis = clock()))
            }
        }
        return decision
    }

    private suspend fun appendStreamedText(text: String) {
        val messageId = streamingRowId()
        streamingText.append(text)
        database.messageDao().updateText(messageId, streamingText.toString())
    }

    /** Reasoning arrives before the answer, so it may be what creates the turn's row (D-054). */
    private suspend fun appendStreamedReasoning(text: String) {
        val messageId = streamingRowId()
        streamingReasoning.append(text)
        database.messageDao().updateReasoning(messageId, streamingReasoning.toString())
    }

    /** The row this model turn streams into, created on the first chunk of text or reasoning. */
    private suspend fun streamingRowId(): String {
        val messageDao = database.messageDao()
        return streamingMessageId ?: newMessageId().also { id ->
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
    }

    private suspend fun saveAssistantMessage(event: AgentEvent.AssistantMessage) {
        val messageDao = database.messageDao()
        // Streamed text already has a row; the final message completes it in place.
        val existingId = streamingMessageId
        val position = if (existingId == null) messageDao.nextPosition(threadId) else streamingPosition
        val times = requestTimer.current
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
            model = modelKey,
            // A stopped turn has no usage report; its row keeps the model but no numbers.
            inputTokens = event.usage?.inputTokens,
            cachedInputTokens = event.usage?.cachedInputTokens,
            outputTokens = event.usage?.outputTokens,
            costUsd = event.usage?.let(priceOf),
            routingFallback = if (routingFellBack) true else null,
            reasoningText = streamingReasoning.toString().trim().ifEmpty { null },
            requestSentAtMillis = times?.sentAtMillis,
            requestSentElapsedMillis = times?.sentElapsedMillis,
            firstTextElapsedMillis = times?.firstTextElapsedMillis,
        )
        // The chat may already have marked the first draw; the transaction keeps that mark
        // from being lost between reading it and replacing the row.
        database.withTransaction {
            val shownElapsedMillis = existingId?.let { id -> messageDao.findAll(listOf(id)).firstOrNull()?.firstShownElapsedMillis }
            messageDao.upsert(row.copy(firstShownElapsedMillis = shownElapsedMillis))
        }
        streamingMessageId = null
        routingFellBack = false
        streamingText.clear()
        streamingReasoning.clear()
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

    companion object {
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
    /** Set when a subagent asks; the card names it and offers "Allow for this task" (D-062). */
    val subagent: SubagentAsk? = null,
)
