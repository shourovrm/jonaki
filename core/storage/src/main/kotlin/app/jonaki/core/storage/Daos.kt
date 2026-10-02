package app.jonaki.core.storage

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreadDao {
    @Query("SELECT * FROM threads ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<ThreadEntity>>

    /** Each thread with the text of its latest user, assistant or error row, for the thread list. */
    @Query(
        "SELECT threads.*, (SELECT messages.text FROM messages WHERE messages.threadId = threads.id " +
            "AND messages.role != 'TOOL' AND messages.text != '' ORDER BY messages.position DESC LIMIT 1) AS lastText, " +
            "(SELECT messages.role FROM messages WHERE messages.threadId = threads.id " +
            "AND messages.role != 'TOOL' ORDER BY messages.position DESC LIMIT 1) AS lastRole, " +
            "(SELECT SUM(messages.costUsd) FROM messages WHERE messages.threadId = threads.id) AS totalCostUsd " +
            "FROM threads ORDER BY updatedAtMillis DESC",
    )
    fun observeSummaries(): Flow<List<ThreadSummary>>

    @Query("UPDATE threads SET modelKey = :modelKey WHERE id = :threadId")
    suspend fun setModelKey(threadId: String, modelKey: String)

    @Query("SELECT * FROM threads WHERE id = :threadId")
    fun observe(threadId: String): Flow<ThreadEntity?>

    @Query("SELECT * FROM threads WHERE id = :threadId")
    suspend fun find(threadId: String): ThreadEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(thread: ThreadEntity)

    @Query("UPDATE threads SET title = :title WHERE id = :threadId")
    suspend fun rename(threadId: String, title: String)

    @Query("UPDATE threads SET updatedAtMillis = :updatedAtMillis WHERE id = :threadId")
    suspend fun touch(threadId: String, updatedAtMillis: Long)

    @Query("UPDATE threads SET webSearchEnabled = :enabled WHERE id = :threadId")
    suspend fun setWebSearchEnabled(threadId: String, enabled: Boolean)

    @Query("UPDATE threads SET toolsAllowedForThread = :toolNames WHERE id = :threadId")
    suspend fun setToolsAllowedForThread(threadId: String, toolNames: String)

    @Query("DELETE FROM threads WHERE id = :threadId")
    suspend fun delete(threadId: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY position")
    fun observeThread(threadId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY position")
    suspend fun listThread(threadId: String): List<MessageEntity>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM messages WHERE threadId = :threadId")
    suspend fun nextPosition(threadId: String): Long

    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Query("UPDATE messages SET text = :text WHERE id = :messageId")
    suspend fun updateText(messageId: String, text: String)

    /** Messages left incomplete when Android stopped the app mid-stream. */
    @Query("UPDATE messages SET isComplete = 1 WHERE isComplete = 0")
    suspend fun closeInterrupted()

    /** Total cost of a thread in USD; null when no call in it has a known cost. */
    @Query("SELECT SUM(costUsd) FROM messages WHERE threadId = :threadId")
    fun observeThreadCost(threadId: String): Flow<Double?>

    /** Total cost of every call since [sinceMillis], for the month's total in the thread list. */
    @Query("SELECT SUM(costUsd) FROM messages WHERE createdAtMillis >= :sinceMillis")
    fun observeCostSince(sinceMillis: Long): Flow<Double?>

    /**
     * Cost per chat service since [sinceMillis], for services that report no
     * monthly spend of their own (D-032). The service is the part of the
     * "service:modelId" key before the colon.
     */
    @Query(
        "SELECT substr(model, 1, instr(model, ':') - 1) AS service, SUM(costUsd) AS costUsd FROM messages " +
            "WHERE createdAtMillis >= :sinceMillis AND model IS NOT NULL AND costUsd IS NOT NULL GROUP BY service",
    )
    fun observeServiceCostSince(sinceMillis: Long): Flow<List<ServiceCostRow>>

    /** One row per model used in a thread, in the order the models were first used (usage sheet). */
    @Query(
        "SELECT model, COUNT(*) AS turns, SUM(inputTokens) AS inputTokens, " +
            "SUM(cachedInputTokens) AS cachedInputTokens, SUM(outputTokens) AS outputTokens, " +
            "SUM(costUsd) AS costUsd FROM messages WHERE threadId = :threadId AND model IS NOT NULL " +
            "GROUP BY model ORDER BY MIN(position)",
    )
    fun observeModelUsage(threadId: String): Flow<List<ModelUsageRow>>

    /**
     * Input tokens of the thread's latest model call: the size of the context
     * the model last saw, for the status strip's percentage.
     */
    @Query(
        "SELECT inputTokens FROM messages WHERE threadId = :threadId AND inputTokens IS NOT NULL " +
            "ORDER BY position DESC LIMIT 1",
    )
    fun observeLastInputTokens(threadId: String): Flow<Int?>
}

data class ServiceCostRow(
    val service: String,
    val costUsd: Double,
)

data class ModelUsageRow(
    val model: String,
    val turns: Int,
    val inputTokens: Long?,
    val cachedInputTokens: Long?,
    val outputTokens: Long?,
    val costUsd: Double?,
)

@Dao
interface StepDao {
    @Query("SELECT * FROM steps WHERE threadId = :threadId ORDER BY startedAtMillis")
    fun observeThread(threadId: String): Flow<List<StepEntity>>

    @Query("SELECT * FROM steps WHERE toolCallId = :toolCallId")
    suspend fun find(toolCallId: String): StepEntity?

    @Upsert
    suspend fun upsert(step: StepEntity)

    /** Steps left running when Android stopped the app. */
    @Query("UPDATE steps SET status = 'STOPPED' WHERE status IN ('RUNNING', 'WAITING_FOR_APPROVAL')")
    suspend fun stopInterrupted()
}

data class ThreadSummary(
    @Embedded val thread: ThreadEntity,
    val lastText: String?,
    val lastRole: String?,
    val totalCostUsd: Double?,
)
