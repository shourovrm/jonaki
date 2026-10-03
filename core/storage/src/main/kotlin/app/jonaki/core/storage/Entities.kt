package app.jonaki.core.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "threads")
data class ThreadEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    /** Per-thread switch from the D-011 amendment. */
    val webSearchEnabled: Boolean,
    /** Tools the user allowed for the whole thread, comma-separated names. */
    val toolsAllowedForThread: String = "",
    /** The thread's model as "service:modelId" (D-027); null in threads made before version 2. */
    val modelKey: String? = null,
    /** Highest message position that background memory extraction has read; null before the first run. */
    val memoryExtractedUpToPosition: Long? = null,
    /**
     * Skills the user switched off for this thread, comma-separated names
     * (D-040). Every other skill in the library is listed in the prompt.
     */
    @ColumnInfo(defaultValue = "")
    val disabledSkills: String = "",
    /** This thread's thinking level as a ThinkingLevel name; null follows the model's setting (D-057). */
    val thinkingLevel: String? = null,
    /** This thread's approval mode as an ApprovalMode name; null follows Settings (D-058). */
    val approvalMode: String? = null,
    /** This thread's answer style as an AnswerStyle name; null follows Settings (D-108). */
    val answerStyle: String? = null,
    /** The persona this thread uses, an id in personas; null for none (D-109). */
    val personaId: String? = null,
    /** The user's own instructions for this thread, added after the general ones (D-107). */
    @ColumnInfo(defaultValue = "")
    val instructions: String = "",
    /** The project the thread belongs to; null when it has none (D-110). */
    val projectId: String? = null,
    /**
     * An incognito thread uses no memory and is deleted a day after its last
     * message, unless the user keeps it as a regular thread (D-111).
     */
    @ColumnInfo(defaultValue = "0")
    val incognito: Boolean = false,
)

/**
 * A group of threads (D-110). Threads point to it with projectId; there is
 * no foreign key, so deleting a project clears that column instead of
 * deleting threads.
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Added to the system prompt of every thread in the project; empty for none. */
    @ColumnInfo(defaultValue = "")
    val instructions: String = "",
    /** The model new threads in the project start with, as "service:modelId"; null uses the default model. */
    val modelKey: String? = null,
    val createdAtMillis: Long,
)

/**
 * One message. Streamed assistant text is written into [text] as chunks
 * arrive, with [isComplete] false until the turn ends, so a killed app still
 * shows the part already received (D-005).
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ThreadEntity::class,
            parentColumns = ["id"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("threadId", "position")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val threadId: String,
    /** Order inside the thread; timestamps can tie. */
    val position: Long,
    val role: String,
    val text: String,
    /** Tool calls of an assistant message as JSON, "[]" when none. */
    val toolCallsJson: String,
    val toolCallId: String?,
    val isComplete: Boolean,
    val createdAtMillis: Long,
    // Usage of the model call that produced an assistant message (D-027); null on other rows.
    /** "service:modelId" of the call. */
    val model: String? = null,
    val inputTokens: Int? = null,
    val cachedInputTokens: Int? = null,
    val outputTokens: Int? = null,
    /** Reported by the service or priced from the catalog; null when unknown. */
    val costUsd: Double? = null,
    /**
     * True when OpenRouter had no endpoint that keeps prompts private and the
     * call ran on the cheapest one instead (D-030); the chat shows a note.
     */
    val routingFallback: Boolean? = null,
    /**
     * The model's reasoning before this answer, when it shows one; kept for
     * the folded "Thinking" block and never sent back to the model (D-054).
     */
    val reasoningText: String? = null,
)

/** One tool call as the user sees it in the step track. */
@Entity(
    tableName = "steps",
    foreignKeys = [
        ForeignKey(
            entity = ThreadEntity::class,
            parentColumns = ["id"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("threadId")],
)
data class StepEntity(
    @PrimaryKey val toolCallId: String,
    val threadId: String,
    val toolName: String,
    val argumentsJson: String,
    /** A [StepStatus] name; stored as text so the column reads plainly. */
    val status: String,
    val resultText: String?,
    val startedAtMillis: Long,
    val finishedAtMillis: Long?,
    /**
     * The subagent that made this call (M7); null for the thread's own agent.
     * A subagent's steps are shown in its card, not in the run's track.
     */
    val subagentId: String? = null,
)

enum class StepStatus {
    WAITING_FOR_APPROVAL,
    RUNNING,
    DONE,
    FAILED,
    DENIED,
    STOPPED,

    /** A subagent's approval was not answered within 3 minutes (D-015). */
    SKIPPED,
}

/**
 * One subagent started by a delegate call (M7, D-015). Its tool calls are
 * steps with [StepEntity.subagentId] set; the cost of its model calls is
 * also saved as hidden BACKGROUND message rows, so that thread, month and
 * usage totals include it, and summed here for its card.
 */
@Entity(
    tableName = "subagents",
    foreignKeys = [
        ForeignKey(
            entity = ThreadEntity::class,
            parentColumns = ["id"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("threadId"), Index("parentToolCallId")],
)
data class SubagentEntity(
    @PrimaryKey val id: String,
    val threadId: String,
    /** The delegate step that started it. */
    val parentToolCallId: String,
    /** Its place among the tasks of one delegate call, from 0. */
    val orderInCall: Int,
    /** "researcher", "scout", "writer" or "worker". */
    val agentType: String,
    val task: String,
    /** "service:modelId" it runs on. */
    val model: String?,
    /** A [SubagentStatus] name. */
    val status: String,
    /** The answer it returned to the thread's agent; null while it runs. */
    val resultText: String?,
    /** The start of the latest text it wrote, for its folded card; null until it writes. */
    val latestText: String? = null,
    val costUsd: Double?,
    val startedAtMillis: Long,
    val finishedAtMillis: Long?,
)

enum class SubagentStatus {
    RUNNING,
    DONE,
    STEP_LIMIT,
    COST_LIMIT,
    TIME_LIMIT,
    FAILED,
    STOPPED,
}

/**
 * One remembered fact (D-009). A global fact has no thread; a thread fact
 * belongs to one thread and goes with it. The full-text index over [text]
 * lives in a separate FTS5 table that triggers keep in step ([MemorySearchIndex]).
 * The id is a small number so that the model can name a fact cheaply ("forget 12").
 */
@Entity(
    tableName = "memories",
    foreignKeys = [
        ForeignKey(
            entity = ThreadEntity::class,
            parentColumns = ["id"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("threadId")],
)
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [MemoryScope] name: "global" or "thread". */
    val scope: String,
    /** Null for a global fact. */
    val threadId: String?,
    val text: String,
    /** Pinned facts go into the prompt first and background extraction leaves them alone. */
    val pinned: Boolean = false,
    /** The message the fact came from; null when the user typed it in the memory screen. */
    val sourceMessageId: String? = null,
    /** [MemoryOrigin] name: "tool", "extracted" or "user". */
    val origin: String,
    /** True for an extracted fact that waits for the user's approval (review mode); never sent to the model. */
    val pendingReview: Boolean = false,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    /** When the fact last went into a prompt or a recall result; null until then. */
    val lastUsedAtMillis: Long? = null,
)

object MemoryScope {
    const val GLOBAL = "global"
    const val THREAD = "thread"
}

object MemoryOrigin {
    /** Saved by the model with the memory tool. */
    const val TOOL = "tool"

    /** Found by background extraction (D-009). */
    const val EXTRACTED = "extracted"

    /** Typed or edited by the user in the memory screen. */
    const val USER = "user"
}

/**
 * A summary of a thread's older messages (M4 step 6). The original messages
 * stay in the database (D-005); the summary stands in for them in requests.
 */
@Entity(
    tableName = "compactions",
    foreignKeys = [
        ForeignKey(
            entity = ThreadEntity::class,
            parentColumns = ["id"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("threadId")],
)
data class CompactionEntity(
    @PrimaryKey val id: String,
    val threadId: String,
    /** Messages with position <= this are covered by the summary. */
    val upToPosition: Long,
    val summaryText: String,
    val createdAtMillis: Long,
    val model: String?,
    val costUsd: Double?,
)
