package app.jonaki.feature.chat

import androidx.compose.runtime.Immutable
import app.jonaki.core.ui.ApprovalModeChoice
import app.jonaki.core.ui.ThinkingChoice
import java.util.Locale

@Immutable
data class ChatUiState(
    val title: String,
    val webSearchEnabled: Boolean,
    val items: List<ChatItem>,
    /** True while the agent works on this thread; Stop shows beside Send, and Send queues the message. */
    val isRunning: Boolean,
    val draft: String,
    /** Messages sent during the run, oldest first, shown above the field until the agent takes them. */
    val queuedMessages: List<QueuedMessageUi> = emptyList(),
    /** The strip above the message field (D-027); null hides it. */
    val status: ChatStatusUi? = null,
    /** The scoped models offered in the model sheet, in the user's order. */
    val modelChoices: List<ModelChoiceUi> = emptyList(),
    val selectedModelKey: String? = null,
    /** What the usage sheet shows; null disables tapping the cost. */
    val usage: UsageUi? = null,
    /** Files picked or shared for the next message, shown as chips above the field. */
    val attachments: List<AttachmentUi> = emptyList(),
    /** A sent prompt being edited; sending replaces it and everything after it. */
    val editingMessageId: String? = null,
    /** This thread's thinking level; DEFAULT follows the model's setting (D-057). */
    val threadThinking: ThinkingChoice = ThinkingChoice.DEFAULT,
    /** An incognito thread shows a banner with Keep and has no Memory item (D-111). */
    val incognito: Boolean = false,
    /** This thread's own approval mode; null follows [defaultApprovalMode] from Settings (D-058). */
    val threadApprovalMode: ApprovalModeChoice? = null,
    val defaultApprovalMode: ApprovalModeChoice = ApprovalModeChoice.ASK,
    /** What the context sheet shows; null while it is being worked out (D-081). */
    val context: ContextUi? = null,
    /** The run_code step opened from its card; null when no code sheet is open (D-090). */
    val codeRun: CodeRunUi? = null,
)

/** One message waiting in the thread's queue. */
@Immutable
data class QueuedMessageUi(val id: String, val text: String)

/** One run_code call for the code sheet: the program and what came out of it (D-090). */
@Immutable
data class CodeRunUi(
    val stepId: String,
    /** Null when the language is not one the viewer colours; the code is then shown plain. */
    val syntax: CodeSyntax?,
    /** "Python", or the language the model named; empty when it named none. */
    val languageName: String,
    val code: String,
    val isRunning: Boolean,
    val printed: String,
    val printedToStderr: String,
    val result: String?,
    val error: String?,
    /** The program's line the error points at, from 1; marked red in the code. */
    val errorLine: Int?,
    val savedFiles: List<String>,
    val notSavedFiles: List<NotSavedFileUi>,
    /** Only the start of the output was kept, so the sheet says that more existed. */
    val outputIsCut: Boolean,
)

@Immutable
data class NotSavedFileUi(val path: String, val reason: String)

/** "Over 5 subagents. Each can cost up to $0.10." with the user's numbers (D-138). */
@Immutable
data class SubagentCostWarning(
    /** The user's warning number; the card shows when a message goes above it. */
    val above: Int,
    /** One subagent's cost cap in US dollars. */
    val costCapUsd: Double,
)

/** A file waiting to go into the thread's inbox/ with the next message. */
@Immutable
data class AttachmentUi(
    val id: String,
    val name: String,
)

/** The model, context and cost of this thread, shown as pills above the message field. */
@Immutable
data class ChatStatusUi(
    val modelName: String,
    /** Null when the model's context window is unknown; the context pills are then hidden. */
    val contextWindowTokens: Int?,
    /** Input tokens of the latest request, which is what the next request starts from. */
    val contextUsedTokens: Int,
    val costUsd: Double,
    /** The thread runs in Bypass mode: no tool asks first, so the strip shows a marker (D-058). */
    val bypassApprovals: Boolean = false,
)

/** How the context window is used, like Claude Code's /context (D-081). */
@Immutable
data class ContextUi(
    val windowTokens: Int,
    /** The service's count for the latest request, or an estimate before the first one. */
    val usedTokens: Int,
    /** False before the first request: the total is then estimated too. */
    val totalIsReported: Boolean,
    /** Estimated parts that add up to [usedTokens]. */
    val parts: List<ContextPartUi>,
    val freeTokens: Int,
    /** Older messages are summarised after a run whose last request reached this many tokens (D-033). */
    val compactAtTokens: Int,
)

/** [count] is shown beside the label: tools, skills, facts, images, or messages a summary covers. */
@Immutable
data class ContextPartUi(val kind: ContextPartUiKind, val tokens: Int, val count: Int? = null)

enum class ContextPartUiKind {
    SYSTEM_PROMPT,
    TOOLS,
    SKILLS,
    MEMORY,
    SUMMARY,
    MESSAGES,
    TOOL_RESULTS,
    IMAGES,
}

/** One scoped model in the model sheet. Prices are US dollars per million tokens, when known. */
@Immutable
data class ModelChoiceUi(
    /** Stable id the app uses, for example "openrouter:z-ai/glm-5.3-flash". */
    val key: String,
    val name: String,
    val serviceName: String,
    val inputPricePerMillion: Double? = null,
    val outputPricePerMillion: Double? = null,
    /** Price of input tokens served from the provider's cache. */
    val cachedInputPricePerMillion: Double? = null,
    /** False when the model takes no thinking level, so the sheet shows no choice (D-057). */
    val supportsThinking: Boolean = false,
)

/** Token and cost totals of one thread for the usage sheet. */
@Immutable
data class UsageUi(
    val totalCostUsd: Double,
    val inputTokens: Int,
    val cachedTokens: Int,
    val outputTokens: Int,
    val perModel: List<ModelUsageUi>,
    /** The latest model requests with their times, newest first (D-132). */
    val requests: List<RequestTimeUi> = emptyList(),
)

/**
 * One model request in the usage sheet's request log (D-132). [providerWaitMillis]
 * runs from sending to the first visible text; [shownAfterMillis] from that text
 * to the chat's first draw of it. Null when the request had no such moment.
 */
@Immutable
data class RequestTimeUi(
    /** Wall-clock time it was sent, already formatted. */
    val sentAt: String,
    val providerWaitMillis: Long?,
    val shownAfterMillis: Long?,
)

@Immutable
data class ModelUsageUi(
    val modelName: String,
    val turns: Int,
    val costUsd: Double,
)

/** One entry in the message list, in display order. */
sealed interface ChatItem {
    val id: String

    data class UserMessage(override val id: String, val text: String) : ChatItem

    data class AssistantMessage(
        override val id: String,
        val markdown: String,
        val isStreaming: Boolean,
        /** True until the chat reports its first draw of this answer to the request log (D-132). */
        val awaitsFirstDraw: Boolean = false,
    ) : ChatItem

    /** The tool steps of one agent turn, drawn as a track with one station per step. */
    data class Run(
        override val id: String,
        val steps: List<StepUi>,
        val isActive: Boolean,
        /** Cost of this turn's model calls; shown on the folded summary line when known. */
        val costUsd: Double? = null,
        /** The subagents its delegate steps started; each delegate step shows its own as rows (D-126). */
        val subagents: List<SubagentUi> = emptyList(),
    ) : ChatItem

    /** A tool that changes something and waits for the user (D-015 permission broker). */
    data class Approval(
        override val id: String,
        val toolName: String,
        /** What the tool will do, for example "Create work/notes.md (2.3 KB)". */
        val description: String,
        /** The subagent that asks, for example "researcher 2"; null for the thread's own agent (D-062). */
        val agentLabel: String? = null,
        /** When a subagent's card is withdrawn unanswered (D-062's 3 minutes); null when it waits without limit. */
        val waitEndsAtMillis: Long? = null,
        /**
         * A delegate card beyond the automatic limit (D-137): the subagents
         * this message will have started; the card then offers no thread
         * allowance.
         */
        val subagentsAfter: Int? = null,
        /** Set above the user's warning number of subagents in one message (D-137, D-138). */
        val costWarning: SubagentCostWarning? = null,
    ) : ChatItem

    /** Under the answer of a turn that used delegate: opens the work sheet (D-126). */
    data class SubagentWork(
        override val id: String,
        val subagents: List<SubagentUi>,
        /** Only boards that hold notes. */
        val notesBoards: List<NotesBoardUi>,
    ) : ChatItem

    data class Error(override val id: String, val message: String, val canRetry: Boolean) : ChatItem

    /** A quiet one-line remark about a turn, for example that routing fell back (D-030). */
    data class Note(override val id: String, val text: String) : ChatItem

    /** An HTML file the model showed with the artifact tool; tapping it opens the viewer (D-047). */
    data class Artifact(override val id: String, val path: String) : ChatItem

    /** The model's reasoning before an answer: open while it streams, folded afterwards (D-054). */
    data class Reasoning(override val id: String, val text: String, val isStreaming: Boolean) : ChatItem

    /** The pulsing line at the end of the chat while the agent works (D-055). */
    data class Working(override val id: String, val activity: WorkingActivity, val sinceMillis: Long) : ChatItem

    /** run_code needed Python, or Python packages, that are not installed (plan M8 step 4). */
    data class PythonInstall(
        override val id: String,
        /** Empty when Python itself is missing. */
        val packageNames: List<String>,
        /** Null when the size is not known before the download. */
        val downloadBytes: Long?,
        val state: PythonInstallState,
    ) : ChatItem

    /** Where the summarised part of the thread ends; a tap shows the summary (D-033). */
    data class SummaryDivider(override val id: String, val summaryMarkdown: String) : ChatItem
}

/** Where the install card's download stands. */
sealed interface PythonInstallState {
    data object Offered : PythonInstallState

    data class Downloading(val doneBytes: Long, val totalBytes: Long?) : PythonInstallState

    /** Installed now; the user can try again or ask again. */
    data object Installed : PythonInstallState

    data class Failed(val message: String) : PythonInstallState
}

enum class PythonCardAction {
    INSTALL,
    NOT_NOW,
    CANCEL,
    TRY_AGAIN,
}

/** What the working line says the agent is doing. */
sealed interface WorkingActivity {
    data object Thinking : WorkingActivity

    data object Writing : WorkingActivity

    data class Tool(val toolName: String) : WorkingActivity
}

@Immutable
data class StepUi(
    val id: String,
    val toolName: String,
    val status: StepUiStatus,
    /** Short result or target, for example "pmc.ncbi.nlm.nih.gov · 10,000 chars". */
    val detail: String,
    /** The exact text a web_search step sent to the search service (D-011 amendment). */
    val query: String? = null,
    val durationMillis: Long? = null,
    /** When the step started; steps that ran side by side overlap (D-080). */
    val startedAtMillis: Long? = null,
    /** Tapping the step opens a sheet with its details; true for run_code (D-090). */
    val opensDetail: Boolean = false,
)

enum class StepUiStatus {
    DONE,
    RUNNING,
    WAITING_FOR_APPROVAL,
    FAILED,
    DENIED,
    STOPPED,

    /** A subagent's approval went unanswered for 3 minutes (D-062). */
    SKIPPED,
}

enum class SubagentUiStatus {
    RUNNING,
    DONE,
    STEP_LIMIT,
    COST_LIMIT,
    TIME_LIMIT,
    FAILED,
    STOPPED,
}

enum class ApprovalChoice {
    ALLOW_ONCE,
    ALLOW_FOR_THREAD,

    /** On a subagent's card: until that subagent ends. */
    ALLOW_FOR_TASK,
    DENY,
}

/** Tool names that the word rule below would spell wrong: acronyms and a brand. */
private val stepLabelExceptions = mapOf(
    "mcp" to "MCP",
    "export_pdf" to "Export PDF",
    "youtube_summarize" to "YouTube summary",
)

/** A step's name as words: "web_search" reads "Web search". */
fun stepLabel(toolName: String): String {
    val exception = stepLabelExceptions[toolName]
    if (exception != null) {
        return exception
    }
    return toolName.replace('_', ' ').replaceFirstChar { first -> first.uppercaseChar() }
}

fun formatStepDuration(durationMillis: Long): String {
    if (durationMillis < 10_000) {
        return String.format(Locale.ENGLISH, "%.1f s", durationMillis / 1000.0)
    }
    if (durationMillis < 60_000) {
        return "${Math.round(durationMillis / 1000.0).coerceAtMost(59)} s"
    }
    val totalSeconds = durationMillis / 1000
    return String.format(Locale.ENGLISH, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}

/** Time left on a subagent's approval card, "2:41"; a part second counts as a whole one, so 0:00 means gone. */
fun formatCountdown(millisLeft: Long): String {
    val secondsLeft = (millisLeft.coerceAtLeast(0) + 999) / 1000
    return String.format(Locale.ENGLISH, "%d:%02d", secondsLeft / 60, secondsLeft % 60)
}

/** Time the steps took together: steps that ran side by side count once (D-080). */
fun totalDurationMillis(steps: List<StepUi>): Long {
    val withoutStart = steps.filter { step -> step.startedAtMillis == null }.sumOf { step -> step.durationMillis ?: 0L }
    val intervals = steps.mapNotNull { step ->
        val start = step.startedAtMillis
        val duration = step.durationMillis
        if (start == null || duration == null) null else start to start + duration
    }
    return withoutStart + coveredMillis(intervals)
}

/** Length of the union of (start, end) intervals: the usual sort-and-merge. */
private fun coveredMillis(intervals: List<Pair<Long, Long>>): Long {
    var covered = 0L
    var mergedStart = 0L
    var mergedEnd: Long? = null
    for ((start, end) in intervals.sortedBy { (start, _) -> start }) {
        val currentEnd = mergedEnd
        if (currentEnd == null || start > currentEnd) {
            if (currentEnd != null) {
                covered += currentEnd - mergedStart
            }
            mergedStart = start
            mergedEnd = end
        } else {
            mergedEnd = maxOf(currentEnd, end)
        }
    }
    val lastEnd = mergedEnd ?: return covered
    return covered + lastEnd - mergedStart
}
