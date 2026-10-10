package app.jonaki.ui

import app.jonaki.core.toolapi.SubagentLimitSettings
import app.jonaki.feature.chat.SubagentCostWarning
import app.jonaki.core.model.Role
import app.jonaki.core.toolapi.GeneratedImages
import app.jonaki.core.toolapi.GeneratedVideos
import app.jonaki.core.model.ToolCall
import app.jonaki.core.storage.CompactionEntity
import app.jonaki.core.storage.HistoryMapper
import app.jonaki.core.storage.MessageEntity
import app.jonaki.core.storage.StepEntity
import app.jonaki.core.storage.StepStatus
import app.jonaki.tools.runcode.InstallNeed
import app.jonaki.tools.runcode.InstallNeeds
import app.jonaki.core.agent.PromptBuilder
import app.jonaki.core.agent.AgentTypes
import app.jonaki.core.agent.SubagentEnding
import app.jonaki.core.agent.SubagentRunner
import app.jonaki.core.storage.SubagentEntity
import app.jonaki.core.storage.SubagentStatus
import app.jonaki.feature.chat.NotesBoardUi
import app.jonaki.feature.chat.SubagentUi
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

    fun build(
        rows: List<MessageEntity>,
        steps: List<StepEntity>,
        isRunning: Boolean,
        /** Cards waiting for the user, oldest first. */
        pendingApprovals: List<PendingApproval> = emptyList(),
        /** Shown after an answer whose OpenRouter call fell back to the cheapest provider (D-030). */
        fallbackNote: String = "",
        /** The thread's subagents (M7); each shows as a row under the delegate step that started it (D-126). */
        subagents: List<SubagentEntity> = emptyList(),
        /** A model's display name from its "service:modelId" key, for a subagent's page; null when unknown. */
        modelNameOf: (modelKey: String) -> String? = { null },
        /**
         * Makes the install card when the last turn's run_code found Python or
         * its packages missing (plan M8 step 4); null shows no card, for
         * example after Not now.
         */
        pythonCard: (toolCallId: String, need: InstallNeed) -> ChatItem? = { _, _ -> null },
        /** The thread's newest summary; a divider marks where the part it covers ends. */
        compaction: CompactionEntity? = null,
        /**
         * The user's subagent limits: the rows' step and cost meters and the
         * delegate card's warning use them (D-138). Rows of earlier runs are
         * measured against today's limits, since a subagent's row keeps none.
         */
        subagentLimits: SubagentLimitSettings = SubagentLimitSettings(),
        /** Every subagent type, custom ones included; the delegate card names the highest cost cap among them. */
        subagentTypeNames: List<String> = AgentTypes.ALL.map { type -> type.name },
        /** Labels for the step track and approval cards, in the app's language. */
        stepWords: StepDetail.Words,
    ): List<ChatItem> {
        // Background usage rows only carry a cost; they would add it to a run's cost line.
        val visibleRows = rows.filter { row -> row.role != HistoryMapper.BACKGROUND_ROLE }
        val turns = splitIntoTurns(visibleRows)
        val stepsById = steps.filter { step -> step.subagentId == null }.associateBy { step -> step.toolCallId }
        val subagentSteps = steps.filter { step -> step.subagentId != null }.groupBy { step -> step.subagentId }
        val subagentsByParent = subagents.groupBy { subagent -> subagent.parentToolCallId }
        val subagentParts = SubagentParts(subagentsByParent, subagentSteps, modelNameOf, stepWords, subagentLimits)
        val items = mutableListOf<ChatItem>()
        val firstKeptTurn = compaction?.let { summary -> firstTurnAfter(turns, summary.upToPosition) }
        for ((index, turn) in turns.withIndex()) {
            val isLastTurn = index == turns.lastIndex
            if (compaction != null && index == firstKeptTurn) {
                items += ChatItem.SummaryDivider("summary-${compaction.id}", compaction.summaryText)
            }
            items += itemsForTurn(turn, stepsById, isRunning && isLastTurn, fallbackNote, subagentParts, stepWords)
            if (isLastTurn) {
                items += pythonCardFor(turn, stepsById, pythonCard)
                items += pendingApprovals.map { pending -> approvalCard(pending, stepWords, subagentLimits, subagentTypeNames) }
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

    /** Only the thread's own agent asks; a card says what the call will do. */
    private fun approvalCard(
        pending: PendingApproval,
        stepWords: StepDetail.Words,
        subagentLimits: SubagentLimitSettings,
        subagentTypeNames: List<String>,
    ): ChatItem.Approval {
        val description = StepDetail.of(pending.toolCall.toolName, pending.toolCall.argumentsJson, stepWords).target.orEmpty()
        return ChatItem.Approval(
            pending.toolCall.id,
            pending.toolName,
            description,
            offersThreadAllowance = pending.offersThreadAllowance,
            afterOutsideContent = pending.afterOutsideContent,
            subagentsAfter = pending.subagentsAfter,
            costWarning = costWarningFor(pending.subagentsAfter, subagentLimits, subagentTypeNames),
        )
    }

    /**
     * Only a delegate card that takes the message above the user's cap says so
     * (D-137, D-138). The cost shown is the highest cap of any type, since the
     * card cannot know which types the call names.
     */
    private fun costWarningFor(subagentsAfter: Int?, limits: SubagentLimitSettings, typeNames: List<String>): SubagentCostWarning? {
        if (subagentsAfter == null || subagentsAfter <= limits.maxPerMessage) {
            return null
        }
        val highestCents = limits.highestCostCapCents(typeNames)
        return SubagentCostWarning(above = limits.maxPerMessage, costCapUsd = highestCents / 100.0)
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
        subagentParts: SubagentParts,
        stepWords: StepDetail.Words,
    ): List<ChatItem> {
        val items = mutableListOf<ChatItem>()
        val turnId = turn.first().id
        val userRow = turn.first().takeIf { it.role == Role.USER.name }
        if (userRow != null) {
            items += ChatItem.UserMessage(userRow.id, PromptBuilder.userTextOf(userRow.text))
        }
        val turnSteps = turn
            .filter { row -> row.role == Role.ASSISTANT.name }
            .flatMap { row -> HistoryMapper.toolCallsFromJson(row.toolCallsJson) }
            .mapNotNull { call -> stepsById[call.id] }
            .sortedBy { step -> step.startedAtMillis }
        val turnSubagents = turnSteps.flatMap { step -> subagentParts.byParent[step.toolCallId].orEmpty() }
        val subagentRows = turnSubagents.map { subagent -> subagentParts.uiOf(subagent, turnSubagents) }
        if (turnSteps.isNotEmpty()) {
            // The turn's cost is only final once the run has ended.
            val turnCost = if (isActiveTurn) null else costOf(turn, turnSubagents)
            items += ChatItem.Run(
                "run-$turnId",
                turnSteps.map { step -> stepUi(step, stepWords) },
                isActive = isActiveTurn,
                costUsd = turnCost,
                subagents = subagentRows,
            )
        }
        // A writer subagent's artifacts open from the chat like the thread agent's own.
        val stepsOfTurn = turnSteps + turnSubagents.flatMap { subagent -> subagentParts.stepsOf(subagent) }
        items += artifactsShown(stepsOfTurn, turnId)
        items += generatedImagesShown(turnSteps, turnId)
        items += generatedVideosShown(turnSteps, turnId)
        for (row in turn) {
            when (row.role) {
                Role.ASSISTANT.name -> items += assistantItems(row, isActiveTurn)
                HistoryMapper.ERROR_ROLE -> items += ChatItem.Error(row.id, row.text, canRetry = false)
            }
            if (row.routingFallback == true && fallbackNote.isNotEmpty()) {
                items += ChatItem.Note("note-${row.id}", fallbackNote)
            }
        }
        // Under the final answer, once the run has ended (D-126).
        if (!isActiveTurn && subagentRows.isNotEmpty()) {
            items += ChatItem.SubagentWork("work-$turnId", subagentRows, notesBoardsOf(subagentRows))
        }
        return items
    }

    /** One board per delegate call whose subagents posted notes; its path follows from the call's id. */
    private fun notesBoardsOf(subagents: List<SubagentUi>): List<NotesBoardUi> =
        subagents
            .groupBy { subagent -> subagent.delegateStepId }
            .map { (delegateStepId, group) ->
                NotesBoardUi(SubagentRunner.notesBoardPath(delegateStepId), notes = group.sumOf { subagent -> subagent.notesPosted })
            }
            .filter { board -> board.notes > 0 }

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
            // Rows from before version 9, subagents and background calls have no first-text time to measure from.
            val awaitsFirstDraw = row.firstTextElapsedMillis != null && row.firstShownElapsedMillis == null
            items += ChatItem.AssistantMessage(row.id, row.text, isStreaming = isStreaming, awaitsFirstDraw = awaitsFirstDraw)
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

    /**
     * One picture per finished generate_image or generate_vector_image call;
     * the result's first line names the file. The tool decides the card: a
     * raster thumbnail or a drawn SVG.
     */
    private fun generatedImagesShown(turnSteps: List<StepEntity>, turnId: String): List<ChatItem> =
        turnSteps
            .filter { step -> step.status == "DONE" && step.toolName in GENERATED_IMAGE_TOOLS }
            .mapNotNull { step ->
                val path = GeneratedImages.pathIn(step.resultText.orEmpty()) ?: return@mapNotNull null
                if (step.toolName == GeneratedImages.VECTOR_TOOL_NAME) {
                    ChatItem.GeneratedVectorImage("vector-image-$turnId-${step.toolCallId}", path)
                } else {
                    ChatItem.GeneratedImage("image-$turnId-${step.toolCallId}", path)
                }
            }

    private val GENERATED_IMAGE_TOOLS = setOf(GeneratedImages.TOOL_NAME, GeneratedImages.VECTOR_TOOL_NAME)

    /**
     * One card per finished generate_video call whose result names a saved
     * file. A call that handed over a still-running job has no file line, so
     * it shows no card until a later job_id call collects the video.
     */
    private fun generatedVideosShown(turnSteps: List<StepEntity>, turnId: String): List<ChatItem> =
        turnSteps
            .filter { step -> step.toolName == GeneratedVideos.TOOL_NAME && step.status == "DONE" }
            .mapNotNull { step -> GeneratedVideos.pathIn(step.resultText.orEmpty())?.let { path -> step.toolCallId to path } }
            .map { (toolCallId, path) -> ChatItem.GeneratedVideo("video-$turnId-$toolCallId", path) }

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

    /** The thread's subagents with their steps, turned into rows. */
    private class SubagentParts(
        val byParent: Map<String, List<SubagentEntity>>,
        private val stepsBySubagent: Map<String?, List<StepEntity>>,
        private val modelNameOf: (String) -> String?,
        private val stepWords: StepDetail.Words,
        private val limits: SubagentLimitSettings,
    ) {
        fun stepsOf(subagent: SubagentEntity): List<StepEntity> =
            stepsBySubagent[subagent.id].orEmpty().sortedBy { step -> step.startedAtMillis }

        fun uiOf(subagent: SubagentEntity, sameTurn: List<SubagentEntity>): SubagentUi {
            val siblings = sameTurn.count { other -> other.parentToolCallId == subagent.parentToolCallId }
            // The same names the delegate result uses: "researcher", or "researcher 2" among several.
            val label = if (siblings > 1) "${subagent.agentType} ${subagent.orderInCall + 1}" else subagent.agentType
            val steps = stepsOf(subagent)
            val budget = limits.budgetFor(subagent.agentType)
            return SubagentUi(
                id = subagent.id,
                label = label,
                delegateStepId = subagent.parentToolCallId,
                task = subagent.task,
                modelName = subagent.model?.let { key -> modelNameOf(key) ?: key.substringAfter(':') },
                status = SubagentUiStatus.entries.firstOrNull { it.name == subagent.status } ?: SubagentUiStatus.STOPPED,
                steps = steps.map { step -> stepUi(step, stepWords) },
                costUsd = subagent.costUsd,
                latestText = subagent.latestText,
                answer = subagent.resultText,
                startedAtMillis = subagent.startedAtMillis,
                finishedAtMillis = subagent.finishedAtMillis,
                filesWritten = filesWrittenBy(steps),
                notesPosted = steps.count(::isNotePosted),
                stepLimit = budget.toolSteps,
                costLimitUsd = budget.costCapUsd,
                failure = failureOf(subagent),
                timeLimitMinutes = budget.minutes,
            )
        }

        /** Only a failed subagent has one; older rows carry none and read as null. */
        private fun failureOf(subagent: SubagentEntity): String? =
            if (subagent.status == SubagentStatus.FAILED.name) SubagentEnding.failureOf(subagent.resultText) else null

        /** Files it wrote, edited or showed, in the order first touched; a denied or failed call wrote nothing. */
        private fun filesWrittenBy(steps: List<StepEntity>): List<String> =
            steps
                .filter { step -> step.toolName in FILE_WRITING_TOOLS && step.status == StepStatus.DONE.name }
                .mapNotNull { step -> artifactPathOf(step.argumentsJson) }
                .distinct()

        private fun isNotePosted(step: StepEntity): Boolean {
            if (step.toolName != NOTES_TOOL || step.status != StepStatus.DONE.name) {
                return false
            }
            val arguments = runCatching { Json.parseToJsonElement(step.argumentsJson) as? JsonObject }.getOrNull() ?: return false
            return (arguments["action"] as? JsonPrimitive)?.contentOrNull == "post"
        }
    }

    /** Tools whose "path" names a file the call wrote. */
    private val FILE_WRITING_TOOLS = setOf("write_file", "edit_file", ARTIFACT_TOOL)

    private const val NOTES_TOOL = "notes"

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
            guardNote = step.guardNote,
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
