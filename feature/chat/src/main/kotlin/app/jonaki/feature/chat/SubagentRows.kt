package app.jonaki.feature.chat

import androidx.compose.runtime.Immutable

/** One subagent of a delegate call (M7, D-126): a row in the run, in the work sheet and its own page. */
@Immutable
data class SubagentUi(
    /** The subagent's own id; Stop and the page use it. */
    val id: String,
    /** "researcher", or "researcher 2" when one call started several. */
    val label: String,
    /** The delegate step that started it; the page's arrows step through its siblings. */
    val delegateStepId: String,
    val task: String,
    /** Null when the model is not in the catalog and has no name. */
    val modelName: String?,
    val status: SubagentUiStatus,
    val steps: List<StepUi>,
    /** Null while no call has a known cost. */
    val costUsd: Double?,
    /** The start of the latest text it wrote. */
    val latestText: String?,
    /** What it returned; null while it runs. */
    val answer: String?,
    val startedAtMillis: Long,
    val finishedAtMillis: Long?,
    /** Paths it wrote, edited or showed, relative to the thread folder, in the order first written. */
    val filesWritten: List<String> = emptyList(),
    /** Notes it posted to its call's notes board. */
    val notesPosted: Int = 0,
    /** D-061's budgets, so the meters read "5 of 10" and "$0.012 of $0.10". */
    val stepLimit: Int = DEFAULT_STEP_LIMIT,
    val costLimitUsd: Double = DEFAULT_COST_LIMIT_USD,
    /** Why a failed one failed, as the service or the app worded it; null when the row was saved without it. */
    val failure: String? = null,
    /** The time limit of its type today, for "Time limit reached (10 min)". */
    val timeLimitMinutes: Int = DEFAULT_TIME_LIMIT_MINUTES,
) {
    /**
     * Steps that used up the budget: the notes board costs none (D-015), so it
     * is shown among [steps] but not counted here.
     */
    val stepsUsed: Int
        get() = steps.count { step -> step.toolName != NOTES_TOOL }

    /** Calls whose approval card went unanswered for 3 minutes (D-062). */
    val skippedSteps: List<StepUi>
        get() = steps.filter { step -> step.status == StepUiStatus.SKIPPED }

    val isRunning: Boolean
        get() = status == SubagentUiStatus.RUNNING

    companion object {
        private const val NOTES_TOOL = "notes"
        const val DEFAULT_STEP_LIMIT = 10
        const val DEFAULT_COST_LIMIT_USD = 0.10
        const val DEFAULT_TIME_LIMIT_MINUTES = 10
    }
}

/** A notes board file of one delegate call and how many notes it holds. */
@Immutable
data class NotesBoardUi(val path: String, val notes: Int)

/** The mark at the start of a subagent's row; only a working one is lit (D-123). */
enum class SubagentRowIcon {
    LIVE,
    WAITING_FOR_YOU,
    ASKING_MAIN_AGENT,
    DONE,
    WARNING,
}

/** The second line of a row: what the subagent does now, or how it ended. */
sealed interface SubagentNow {
    data class Working(val step: StepUi) : SubagentNow

    /** Between steps: the first line of the latest text it wrote. */
    data class Wrote(val line: String) : SubagentNow

    data object Thinking : SubagentNow

    data object WaitingForYou : SubagentNow

    data object AskingMainAgent : SubagentNow

    data class Done(val skippedParts: Int, val filesWritten: Int, val durationMillis: Long?) : SubagentNow

    /** Stopped early: a limit, a failure or Stop. */
    data class Ended(val status: SubagentUiStatus) : SubagentNow
}

/** [needsLook] turns the second line to full ink, so rows that want a decision stand out without a colour. */
@Immutable
data class SubagentRow(val icon: SubagentRowIcon, val now: SubagentNow, val needsLook: Boolean)

/** "2 of 3 done": every subagent that has ended counts, however it ended. */
@Immutable
data class GroupProgress(val done: Int, val total: Int)

/** The line under "Work from N subagents". */
@Immutable
data class WorkSummary(
    /** Early stops by kind, in [SubagentUiStatus] order, with how many ended that way. */
    val earlyStops: List<Pair<SubagentUiStatus, Int>>,
    val skippedParts: Int,
    val notes: Int,
) {
    val allDone: Boolean
        get() = earlyStops.isEmpty() && skippedParts == 0
}

/** Why a subagent did not finish normally; the row and the page word it. */
sealed interface SubagentEndReason {
    data class TimeLimit(val minutes: Int) : SubagentEndReason

    data class StepLimit(val steps: Int) : SubagentEndReason

    data object CostLimit : SubagentEndReason

    data class Failed(val message: String) : SubagentEndReason
}

object SubagentRows {
    private const val ASK_PARENT_TOOL = "ask_parent"

    fun rowOf(subagent: SubagentUi): SubagentRow {
        if (!subagent.isRunning) {
            return endedRow(subagent)
        }
        val steps = subagent.steps
        // The user is the one to act, so a waiting card wins over any work in progress.
        if (steps.any { step -> step.status == StepUiStatus.WAITING_FOR_APPROVAL }) {
            return SubagentRow(SubagentRowIcon.WAITING_FOR_YOU, SubagentNow.WaitingForYou, needsLook = true)
        }
        val runningStep = steps.lastOrNull { step -> step.status == StepUiStatus.RUNNING }
        if (runningStep?.toolName == ASK_PARENT_TOOL) {
            return SubagentRow(SubagentRowIcon.ASKING_MAIN_AGENT, SubagentNow.AskingMainAgent, needsLook = false)
        }
        if (runningStep != null) {
            return SubagentRow(SubagentRowIcon.LIVE, SubagentNow.Working(runningStep), needsLook = false)
        }
        val latestLine = subagent.latestText?.lineSequence()?.map { line -> line.trim() }?.firstOrNull { line -> line.isNotEmpty() }
        val now = if (latestLine == null) SubagentNow.Thinking else SubagentNow.Wrote(latestLine)
        return SubagentRow(SubagentRowIcon.LIVE, now, needsLook = false)
    }

    private fun endedRow(subagent: SubagentUi): SubagentRow {
        // A subagent at its step limit still returned an answer, so its row reads as work done, not as a failure.
        if (subagent.status == SubagentUiStatus.STEP_LIMIT) {
            return SubagentRow(SubagentRowIcon.DONE, SubagentNow.Ended(subagent.status), needsLook = false)
        }
        if (subagent.status != SubagentUiStatus.DONE) {
            return SubagentRow(SubagentRowIcon.WARNING, SubagentNow.Ended(subagent.status), needsLook = true)
        }
        val now = SubagentNow.Done(
            skippedParts = subagent.skippedSteps.size,
            filesWritten = subagent.filesWritten.size,
            durationMillis = durationOf(subagent),
        )
        return SubagentRow(SubagentRowIcon.DONE, now, needsLook = false)
    }

    /**
     * The reason line of a row that did not finish normally. Null for a
     * subagent that runs, is done or was stopped by the user, and for a
     * failure whose row was saved without a reason (older versions).
     */
    fun endReasonOf(subagent: SubagentUi): SubagentEndReason? = when (subagent.status) {
        SubagentUiStatus.FAILED -> subagent.failure?.takeIf { message -> message.isNotBlank() }?.let { message -> SubagentEndReason.Failed(message) }
        SubagentUiStatus.TIME_LIMIT -> SubagentEndReason.TimeLimit(subagent.timeLimitMinutes)
        SubagentUiStatus.STEP_LIMIT -> SubagentEndReason.StepLimit(subagent.stepLimit)
        SubagentUiStatus.COST_LIMIT -> SubagentEndReason.CostLimit
        SubagentUiStatus.RUNNING, SubagentUiStatus.DONE, SubagentUiStatus.STOPPED -> null
    }

    /** Null while it runs. */
    fun durationOf(subagent: SubagentUi): Long? {
        val finished = subagent.finishedAtMillis ?: return null
        return finished - subagent.startedAtMillis
    }

    fun progressOf(subagents: List<SubagentUi>): GroupProgress =
        GroupProgress(done = subagents.count { subagent -> !subagent.isRunning }, total = subagents.size)

    fun workSummaryOf(subagents: List<SubagentUi>): WorkSummary {
        val earlyStops = SubagentUiStatus.entries
            .filter { status -> status != SubagentUiStatus.RUNNING && status != SubagentUiStatus.DONE }
            .map { status -> status to subagents.count { subagent -> subagent.status == status } }
            .filter { (_, count) -> count > 0 }
        return WorkSummary(
            earlyStops = earlyStops,
            skippedParts = subagents.sumOf { subagent -> subagent.skippedSteps.size },
            notes = subagents.sumOf { subagent -> subagent.notesPosted },
        )
    }

    /** How full a meter is, from 0 to 1; a limit of 0 or less draws it empty. */
    fun share(used: Double, limit: Double): Float {
        if (limit <= 0.0) {
            return 0f
        }
        return (used / limit).coerceIn(0.0, 1.0).toFloat()
    }
}
