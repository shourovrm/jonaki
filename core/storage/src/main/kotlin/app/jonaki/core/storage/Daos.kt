package app.jonaki.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreadDao {
    @Query("SELECT * FROM threads ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<ThreadEntity>>

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
}

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
