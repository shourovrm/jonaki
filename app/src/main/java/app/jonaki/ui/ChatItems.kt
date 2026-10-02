package app.jonaki.ui

import app.jonaki.core.model.Role
import app.jonaki.core.model.ToolCall
import app.jonaki.core.storage.CompactionEntity
import app.jonaki.core.storage.HistoryMapper
import app.jonaki.core.storage.MessageEntity
import app.jonaki.core.storage.StepEntity
import app.jonaki.feature.chat.ChatItem
import app.jonaki.feature.chat.StepUi
import app.jonaki.feature.chat.StepUiStatus
import app.jonaki.feature.chat.WorkingActivity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Builds the chat list from saved rows. One user turn shows the user's
 * message, then one run block with every tool step of that turn, then the
 * assistant's text (D-024: Rail line steps inside the Firefly look).
 */
object ChatItems {
    /** Id of the working line shown at the end while a run is going (D-055). */
    const val WORKING_ID = "working"

    /** The "[date, time zone]" line saved in front of each user message for the model. */
    private val timeLine = Regex("""^\[[^\]\n]*]\n""")

    fun build(
        rows: List<MessageEntity>,
        steps: List<StepEntity>,
        isRunning: Boolean,
        pendingApproval: ToolCall?,
        /** Shown after an answer whose OpenRouter call fell back to the cheapest provider (D-030). */
        fallbackNote: String = "",
        /** The thread's newest summary; a divider marks where the part it covers ends. */
        compaction: CompactionEntity? = null,
    ): List<ChatItem> {
        // Background usage rows only carry a cost; they would add it to a run's cost line.
        val visibleRows = rows.filter { row -> row.role != HistoryMapper.BACKGROUND_ROLE }
        val turns = splitIntoTurns(visibleRows)
        val stepsById = steps.associateBy { step -> step.toolCallId }
        val items = mutableListOf<ChatItem>()
        val firstKeptTurn = compaction?.let { summary -> firstTurnAfter(turns, summary.upToPosition) }
        for ((index, turn) in turns.withIndex()) {
            val isLastTurn = index == turns.lastIndex
            if (compaction != null && index == firstKeptTurn) {
                items += ChatItem.SummaryDivider("summary-${compaction.id}", compaction.summaryText)
            }
            items += itemsForTurn(turn, stepsById, isRunning && isLastTurn, fallbackNote)
            if (isLastTurn && pendingApproval != null) {
                val detail = StepDetail.of(pendingApproval.toolName, pendingApproval.argumentsJson)
                items += ChatItem.Approval(pendingApproval.id, pendingApproval.toolName, detail.target.orEmpty())
            }
        }
        val withRetry = markRetryableError(items, visibleRows, isRunning)
        // While an approval card waits, the user is the one to act, so nothing pulses.
        if (!isRunning || pendingApproval != null) {
            return withRetry
        }
        val runStartedAt = visibleRows.lastOrNull { row -> row.role == Role.USER.name }?.createdAtMillis ?: 0
        return withRetry + ChatItem.Working(WORKING_ID, activityOf(withRetry), runStartedAt)
    }

    /** A running tool wins, then text being written; anything else is thinking. */
    private fun activityOf(items: List<ChatItem>): WorkingActivity {
        val runningStep = items.filterIsInstance<ChatItem.Run>()
            .flatMap { run -> run.steps }
            .lastOrNull { step -> step.status == StepUiStatus.RUNNING }
        if (runningStep != null) {
            return WorkingActivity.Tool(runningStep.toolName)
        }
        val isWriting = items.any { item -> item is ChatItem.AssistantMessage && item.isStreaming }
        return if (isWriting) WorkingActivity.Writing else WorkingActivity.Thinking
    }

    /**
     * The index of the first turn the summary does not cover, or null when it
     * covers all of them or none: a divider at the top or bottom marks nothing.
     */
    private fun firstTurnAfter(turns: List<List<MessageEntity>>, upToPosition: Long): Int? {
        val index = turns.indexOfFirst { turn -> turn.first().position > upToPosition }
        if (index <= 0) {
            return null
        }
        return index
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
        items += artifactsShown(turnSteps, turnId)
        for (row in turn) {
            when (row.role) {
                Role.ASSISTANT.name -> items += assistantItems(row, isActiveTurn)
                HistoryMapper.ERROR_ROLE -> items += ChatItem.Error(row.id, row.text, canRetry = false)
            }
            if (row.routingFallback == true && fallbackNote.isNotEmpty()) {
                items += ChatItem.Note("note-${row.id}", fallbackNote)
            }
        }
        return items
    }

    /** The reasoning block, then the answer text, of one assistant row. */
    private fun assistantItems(row: MessageEntity, isActiveTurn: Boolean): List<ChatItem> {
        val items = mutableListOf<ChatItem>()
        val isStreaming = isActiveTurn && !row.isComplete
        val reasoning = row.reasoningText
        if (!reasoning.isNullOrBlank()) {
            // Reasoning stays open only until the answer text starts.
            val isStillThinking = isStreaming && row.text.isEmpty()
            items += ChatItem.Reasoning("reasoning-${row.id}", reasoning.trim(), isStreaming = isStillThinking)
        }
        if (row.text.isNotBlank()) {
            items += ChatItem.AssistantMessage(row.id, row.text, isStreaming = isStreaming)
        }
        return items
    }

    /** One card per file the artifact tool showed in this turn, in the order first shown (D-047). */
    private fun artifactsShown(turnSteps: List<StepEntity>, turnId: String): List<ChatItem> {
        val paths = turnSteps
            .filter { step -> step.toolName == ARTIFACT_TOOL && step.status == "DONE" }
            .mapNotNull { step -> artifactPathOf(step.argumentsJson) }
            .distinct()
        return paths.map { path -> ChatItem.Artifact("artifact-$turnId-$path", path) }
    }

    private fun artifactPathOf(argumentsJson: String): String? {
        val arguments = runCatching { Json.parseToJsonElement(argumentsJson) as? JsonObject }.getOrNull() ?: return null
        return (arguments["path"] as? JsonPrimitive)?.contentOrNull?.trim()?.removePrefix("./")
    }

    private const val ARTIFACT_TOOL = "artifact"

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
