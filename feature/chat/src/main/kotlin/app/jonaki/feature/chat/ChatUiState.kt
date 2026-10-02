package app.jonaki.feature.chat

import androidx.compose.runtime.Immutable
import app.jonaki.core.ui.ThinkingChoice
import java.util.Locale

@Immutable
data class ChatUiState(
    val title: String,
    /** Shown under the title, for example "DeepSeek V3". */
    val modelLabel: String,
    val webSearchEnabled: Boolean,
    val items: List<ChatItem>,
    /** True while the agent works on this thread; the Send button becomes Stop. */
    val isRunning: Boolean,
    val draft: String,
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
)

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

    data class AssistantMessage(override val id: String, val markdown: String, val isStreaming: Boolean) : ChatItem

    /** The tool steps of one agent turn, drawn as a track with one station per step. */
    data class Run(
        override val id: String,
        val steps: List<StepUi>,
        val isActive: Boolean,
        /** Cost of this turn's model calls; shown on the folded summary line when known. */
        val costUsd: Double? = null,
    ) : ChatItem

    /** A tool that changes something and waits for the user (D-015 permission broker). */
    data class Approval(
        override val id: String,
        val toolName: String,
        /** What the tool will do, for example "Create work/notes.md (2.3 KB)". */
        val description: String,
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

    /** Where the summarised part of the thread ends; a tap shows the summary (D-033). */
    data class SummaryDivider(override val id: String, val summaryMarkdown: String) : ChatItem
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
)

enum class StepUiStatus {
    DONE,
    RUNNING,
    WAITING_FOR_APPROVAL,
    FAILED,
    DENIED,
    STOPPED,
}

enum class ApprovalChoice {
    ALLOW_ONCE,
    ALLOW_FOR_THREAD,
    DENY,
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

fun totalDurationMillis(steps: List<StepUi>): Long = steps.sumOf { step -> step.durationMillis ?: 0L }
