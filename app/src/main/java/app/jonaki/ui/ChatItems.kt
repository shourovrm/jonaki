package app.jonaki.ui

import app.jonaki.core.model.Role
import app.jonaki.core.model.ToolCall
import app.jonaki.core.storage.HistoryMapper
import app.jonaki.core.storage.MessageEntity
import app.jonaki.core.storage.StepEntity
import app.jonaki.feature.chat.ChatItem
import app.jonaki.feature.chat.StepUi
import app.jonaki.feature.chat.StepUiStatus

/**
 * Builds the chat list from saved rows. One user turn shows the user's
 * message, then one run block with every tool step of that turn, then the
 * assistant's text (D-024: Rail line steps inside the Firefly look).
 */
object ChatItems {
    /** Id of the caret row shown while the model is thinking and nothing else moves. */
    const val WAITING_ID = "waiting"

    /** The "[date, time zone]" line saved in front of each user message for the model. */
    private val timeLine = Regex("""^\[[^\]\n]*]\n""")

    fun build(
        rows: List<MessageEntity>,
        steps: List<StepEntity>,
        isRunning: Boolean,
        pendingApproval: ToolCall?,
        /** Shown after an answer whose OpenRouter call fell back to the cheapest provider (D-030). */
        fallbackNote: String = "",
    ): List<ChatItem> {
        val turns = splitIntoTurns(rows)
        val stepsById = steps.associateBy { step -> step.toolCallId }
        val items = mutableListOf<ChatItem>()
        for ((index, turn) in turns.withIndex()) {
            val isLastTurn = index == turns.lastIndex
            items += itemsForTurn(turn, stepsById, isRunning && isLastTurn, fallbackNote)
            if (isLastTurn && pendingApproval != null) {
                val detail = StepDetail.of(pendingApproval.toolName, pendingApproval.argumentsJson)
                items += ChatItem.Approval(pendingApproval.id, pendingApproval.toolName, detail.target.orEmpty())
            }
        }
        val withRetry = markRetryableError(items, rows, isRunning)
        if (isRunning && pendingApproval == null && !somethingIsMoving(withRetry)) {
            return withRetry + ChatItem.AssistantMessage(WAITING_ID, "", isStreaming = true)
        }
        return withRetry
    }

    private fun somethingIsMoving(items: List<ChatItem>): Boolean = items.any { item ->
        val isStreamingText = item is ChatItem.AssistantMessage && item.isStreaming
        val hasRunningStep = item is ChatItem.Run && item.steps.any { step -> step.status == StepUiStatus.RUNNING }
        isStreamingText || hasRunningStep
    }

    private fun splitIntoTurns(rows: List<MessageEntity>): List<List<MessageEntity>> {
        val turns = mutableListOf<MutableList<MessageEntity>>()
        for (row in rows) {
            if (row.role == Role.USER.name || turns.isEmpty()) {
                turns += mutableListOf<MessageEntity>()
            }
            turns.last() += row
        }
        return turns
    }

    private fun itemsForTurn(
        turn: List<MessageEntity>,
        stepsById: Map<String, StepEntity>,
        isActiveTurn: Boolean,
        fallbackNote: String,
    ): List<ChatItem> {
        val items = mutableListOf<ChatItem>()
        val turnId = turn.first().id
        val userRow = turn.first().takeIf { it.role == Role.USER.name }
        if (userRow != null) {
            items += ChatItem.UserMessage(userRow.id, userRow.text.replaceFirst(timeLine, ""))
        }
        val turnSteps = turn
            .filter { row -> row.role == Role.ASSISTANT.name }
            .flatMap { row -> HistoryMapper.toolCallsFromJson(row.toolCallsJson) }
            .mapNotNull { call -> stepsById[call.id] }
            .sortedBy { step -> step.startedAtMillis }
        if (turnSteps.isNotEmpty()) {
            // The turn's cost is only final once the run has ended.
            val turnCost = if (isActiveTurn) null else costOf(turn)
            items += ChatItem.Run("run-$turnId", turnSteps.map(::stepUi), isActive = isActiveTurn, costUsd = turnCost)
        }
        for (row in turn) {
            when (row.role) {
                Role.ASSISTANT.name -> if (row.text.isNotBlank()) {
                    items += ChatItem.AssistantMessage(row.id, row.text, isStreaming = isActiveTurn && !row.isComplete)
                }
                HistoryMapper.ERROR_ROLE -> items += ChatItem.Error(row.id, row.text, canRetry = false)
            }
            if (row.routingFallback == true && fallbackNote.isNotEmpty()) {
                items += ChatItem.Note("note-${row.id}", fallbackNote)
            }
        }
        return items
    }

    private fun costOf(turn: List<MessageEntity>): Double? {
        val costs = turn.mapNotNull { row -> row.costUsd }
        return if (costs.isEmpty()) null else costs.sum()
    }

    private fun stepUi(step: StepEntity): StepUi {
        val detail = StepDetail.of(step.toolName, step.argumentsJson)
        val status = StepUiStatus.entries.firstOrNull { it.name == step.status } ?: StepUiStatus.STOPPED
        val finished = step.finishedAtMillis
        return StepUi(
            id = step.toolCallId,
            toolName = step.toolName,
            status = status,
            detail = detail.target.orEmpty(),
            query = detail.query,
            durationMillis = if (finished == null) null else finished - step.startedAtMillis,
        )
    }

    /** Retry re-runs the saved history, so it only makes sense on a final error while idle. */
    private fun markRetryableError(items: List<ChatItem>, rows: List<MessageEntity>, isRunning: Boolean): List<ChatItem> {
        val lastRow = rows.lastOrNull() ?: return items
        if (isRunning || lastRow.role != HistoryMapper.ERROR_ROLE) {
            return items
        }
        return items.map { item ->
            if (item is ChatItem.Error && item.id == lastRow.id) item.copy(canRetry = true) else item
        }
    }
}
