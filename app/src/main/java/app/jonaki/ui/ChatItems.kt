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
    /** The "[date, time zone]" line saved in front of each user message for the model. */
    private val timeLine = Regex("""^\[[^\]\n]*]\n""")

    fun build(
        rows: List<MessageEntity>,
        steps: List<StepEntity>,
        isRunning: Boolean,
        pendingApproval: ToolCall?,
    ): List<ChatItem> {
        val turns = splitIntoTurns(rows)
        val stepsById = steps.associateBy { step -> step.toolCallId }
        val items = mutableListOf<ChatItem>()
        for ((index, turn) in turns.withIndex()) {
            val isLastTurn = index == turns.lastIndex
            items += itemsForTurn(turn, stepsById, isRunning && isLastTurn)
            if (isLastTurn && pendingApproval != null) {
                val detail = StepDetail.of(pendingApproval.toolName, pendingApproval.argumentsJson)
                items += ChatItem.Approval(pendingApproval.id, pendingApproval.toolName, detail.target.orEmpty())
            }
        }
        return markRetryableError(items, rows, isRunning)
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

    private fun itemsForTurn(turn: List<MessageEntity>, stepsById: Map<String, StepEntity>, isActiveTurn: Boolean): List<ChatItem> {
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
            items += ChatItem.Run("run-$turnId", turnSteps.map(::stepUi), isActive = isActiveTurn)
        }
        for (row in turn) {
            when (row.role) {
                Role.ASSISTANT.name -> if (row.text.isNotBlank()) {
                    items += ChatItem.AssistantMessage(row.id, row.text, isStreaming = isActiveTurn && !row.isComplete)
                }
                HistoryMapper.ERROR_ROLE -> items += ChatItem.Error(row.id, row.text, canRetry = false)
            }
        }
        return items
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
