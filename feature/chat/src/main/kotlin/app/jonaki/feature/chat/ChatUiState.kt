package app.jonaki.feature.chat

import androidx.compose.runtime.Immutable
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
)

/** One entry in the message list, in display order. */
sealed interface ChatItem {
    val id: String

    data class UserMessage(override val id: String, val text: String) : ChatItem

    data class AssistantMessage(override val id: String, val markdown: String, val isStreaming: Boolean) : ChatItem

    /** The tool steps of one agent turn, drawn as a track with one station per step. */
    data class Run(override val id: String, val steps: List<StepUi>, val isActive: Boolean) : ChatItem

    /** A tool that changes something and waits for the user (D-015 permission broker). */
    data class Approval(
        override val id: String,
        val toolName: String,
        /** What the tool will do, for example "Create work/notes.md (2.3 KB)". */
        val description: String,
    ) : ChatItem

    data class Error(override val id: String, val message: String, val canRetry: Boolean) : ChatItem
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
