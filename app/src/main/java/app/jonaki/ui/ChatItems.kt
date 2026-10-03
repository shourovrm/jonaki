package app.jonaki.ui

import app.jonaki.core.model.Role
import app.jonaki.core.model.ToolCall
import app.jonaki.core.storage.CompactionEntity
import app.jonaki.core.storage.HistoryMapper
import app.jonaki.core.storage.MessageEntity
import app.jonaki.core.storage.StepEntity
import app.jonaki.core.storage.StepStatus
import app.jonaki.tools.runcode.InstallNeed
import app.jonaki.tools.runcode.InstallNeeds
import app.jonaki.core.storage.SubagentEntity
import app.jonaki.feature.chat.SubagentUiStatus
import app.jonaki.run.PendingApproval
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
        /** Cards waiting for the user, oldest first. */
        pendingApprovals: List<PendingApproval> = emptyList(),
        /** Shown after an answer whose OpenRouter call fell back to the cheapest provider (D-030). */
        fallbackNote: String = "",
        /** The thread's subagents (M7); each shows as a card under the run that started it. */
        subagents: List<SubagentEntity> = emptyList(),
        /**
         * Makes the install card when the last turn's run_code found Python or
         * its packages missing (plan M8 step 4); null shows no card, for
         * example after Not now.
         */
        pythonCard: (toolCallId: String, need: InstallNeed) -> ChatItem? = { _, _ -> null },
        /** The thread's newest summary; a divider marks where the part it covers ends. */
        compaction: CompactionEntity? = null,
        /** Labels for the step track and approval cards, in the app's language. */
        stepWords: StepDetail.Words,
    ): List<ChatItem> {
        // Background usage rows only carry a cost; they would add it to a run's cost line.
        val visibleRows = rows.filter { row -> row.role != HistoryMapper.BACKGROUND_ROLE }
        val turns = splitIntoTurns(visibleRows)
        val stepsById = steps.filter { step -> step.subagentId == null }.associateBy { step -> step.toolCallId }
        val subagentSteps = steps.filter { step -> step.subagentId != null }.groupBy { step -> step.subagentId }
        val subagentsByParent = subagents.groupBy { subagent -> subagent.parentToolCallId }
        val items = mutableListOf<ChatItem>()
        val firstKeptTurn = compaction?.let { summary -> firstTurnAfter(turns, summary.upToPosition) }
        for ((index, turn) in turns.withIndex()) {
            val isLastTurn = index == turns.lastIndex
            if (compaction != null && index == firstKeptTurn) {
                items += ChatItem.SummaryDivider("summary-${compaction.id}", compaction.summaryText)
            }
            items += itemsForTurn(turn, stepsById, isRunning && isLastTurn, fallbackNote, subagentsByParent, subagentSteps, stepWords)
            if (isLastTurn) {
                items += pythonCardFor(turn, stepsById, pythonCard)
                items += pendingApprovals.map { pending -> approvalCard(pending, stepWords) }
            }
        }
        val withRetry = markRetryableError(items, visibleRows, isRunning)
        // While an approval card waits, the user is the one to act, so nothing pulses.
        if (!isRunning || pendingApprovals.isNotEmpty()) {
            return withRetry
        }
        val runStartedAt = visibleRows.lastOrNull { row -> row.role == Role.USER.name }?.createdAtMillis ?: 0
        return withRetry + ChatItem.Working(WORKING_ID, activityOf(withRetry), runStartedAt)
    }

    /**
     * The turn's latest run_code call that failed for a missing Python or
     * missing packages. Only the last turn gets a card, so old turns stay
     * quiet once the user has moved on.
     */
    private fun pythonCardFor(
        turn: List<MessageEntity>,
        stepsById: Map<String, StepEntity>,
        pythonCard: (String, InstallNeed) -> ChatItem?,
    ): List<ChatItem> {
        val step = turn
            .filter { row -> row.role == Role.ASSISTANT.name }
            .flatMap { row -> HistoryMapper.toolCallsFromJson(row.toolCallsJson) }
            .mapNotNull { call -> stepsById[call.id] }
            .filter { candidate -> candidate.toolName == RUN_CODE_TOOL && candidate.status == StepStatus.FAILED.name }
            .lastOrNull { candidate -> InstallNeeds.of(candidate.resultText.orEmpty()) != null }
            ?: return emptyList()
        val need = InstallNeeds.of(step.resultText.orEmpty()) ?: return emptyList()
        return listOfNotNull(pythonCard(step.toolCallId, need))
    }

    /** A subagent's request_tool card names the tool it wants and says why; other cards say what the call will do. */
    private fun approvalCard(pending: PendingApproval, stepWords: StepDetail.Words): ChatItem.Approval {
        val subagent = pending.subagent
        val reason = subagent?.reason
        val description = if (reason != null) {
            reason
        } else {
            StepDetail.of(pending.toolCall.toolName, pending.toolCall.argumentsJson, stepWords).target.orEmpty()
        }
        return ChatItem.Approval(pending.toolCall.id, pending.toolName, description, agentLabel = subagent?.agentLabel)
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
        subagentsByParent: Map<String, List<SubagentEntity>>,
        subagentSteps: Map<String?, List<StepEntity>>,
        stepWords: StepDetail.Words,
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
        val turnSubagents = turnSteps.flatMap { step -> subagentsByParent[step.toolCallId].orEmpty() }
        if (turnSteps.isNotEmpty()) {
            // The turn's cost is only final once the run has ended.
            val turnCost = if (isActiveTurn) null else costOf(turn, turnSubagents)
            items += ChatItem.Run("run-$turnId", turnSteps.map { step -> stepUi(step, stepWords) }, isActive = isActiveTurn, costUsd = turnCost)
        }
        for (subagent in turnSubagents) {
            items += subagentCard(subagent, subagentSteps[subagent.id].orEmpty(), turnSubagents, stepWords)
        }
        // A writer subagent's artifacts open from the chat like the thread agent's own.
        val stepsOfTurn = turnSteps + turnSubagents.flatMap { subagent -> subagentSteps[subagent.id].orEmpty() }
        items += artifactsShown(stepsOfTurn, turnId)
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

    /** Its steps open the code sheet (D-090) and its install errors show the Python card (D-094). */
    private const val RUN_CODE_TOOL = "run_code"

    /** The turn's own model calls plus its subagents', whose usage rows are hidden. */
    private fun costOf(turn: List<MessageEntity>, subagents: List<SubagentEntity>): Double? {
        val costs = turn.mapNotNull { row -> row.costUsd } + subagents.mapNotNull { subagent -> subagent.costUsd }
        return if (costs.isEmpty()) null else costs.sum()
    }

    private fun subagentCard(
        subagent: SubagentEntity,
        steps: List<StepEntity>,
        sameCall: List<SubagentEntity>,
        stepWords: StepDetail.Words,
    ): ChatItem.Subagent {
        val siblings = sameCall.count { other -> other.parentToolCallId == subagent.parentToolCallId }
        // The same names the delegate result uses: "researcher", or "researcher 2" among several.
        val label = if (siblings > 1) "${subagent.agentType} ${subagent.orderInCall + 1}" else subagent.agentType
        return ChatItem.Subagent(
            id = "subagent-${subagent.id}",
            label = label,
            task = subagent.task,
            status = SubagentUiStatus.entries.firstOrNull { it.name == subagent.status } ?: SubagentUiStatus.STOPPED,
            steps = steps.sortedBy { step -> step.startedAtMillis }.map { step -> stepUi(step, stepWords) },
            costUsd = subagent.costUsd,
            latestText = subagent.latestText,
            answer = subagent.resultText,
        )
    }

    private fun stepUi(step: StepEntity, stepWords: StepDetail.Words): StepUi {
        val detail = StepDetail.of(step.toolName, step.argumentsJson, stepWords)

        val status = StepUiStatus.entries.firstOrNull { it.name == step.status } ?: StepUiStatus.STOPPED
        val finished = step.finishedAtMillis
        return StepUi(
            id = step.toolCallId,
            toolName = step.toolName,
            status = status,
            detail = detail.target.orEmpty(),
            query = detail.query,
            durationMillis = if (finished == null) null else finished - step.startedAtMillis,
            startedAtMillis = step.startedAtMillis,
            opensDetail = step.toolName == RUN_CODE_TOOL,
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
