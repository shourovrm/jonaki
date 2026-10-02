package app.jonaki.core.storage

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
)

enum class StepStatus {
    WAITING_FOR_APPROVAL,
    RUNNING,
    DONE,
    FAILED,
    DENIED,
    STOPPED,
}
