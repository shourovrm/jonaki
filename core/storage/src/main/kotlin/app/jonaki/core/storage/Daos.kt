package app.jonaki.core.storage

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.SkipQueryVerification
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreadDao {
    @Query("SELECT * FROM threads ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<ThreadEntity>>

    /** Each thread with the text of its latest user, assistant or error row, for the thread list. */
    @Query(ThreadListQueries.SUMMARIES)
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

    @Query("UPDATE threads SET thinkingLevel = :thinkingLevel WHERE id = :threadId")
    suspend fun setThinkingLevel(threadId: String, thinkingLevel: String?)

    @Query("UPDATE threads SET approvalMode = :approvalMode WHERE id = :threadId")
    suspend fun setApprovalMode(threadId: String, approvalMode: String?)

    @Query("UPDATE threads SET webSearchEnabled = :enabled WHERE id = :threadId")
    suspend fun setWebSearchEnabled(threadId: String, enabled: Boolean)

    @Query("UPDATE threads SET memoryExtractedUpToPosition = :position WHERE id = :threadId")
    suspend fun setMemoryExtractedUpTo(threadId: String, position: Long)

    @Query("UPDATE threads SET toolsAllowedForThread = :toolNames WHERE id = :threadId")
    suspend fun setToolsAllowedForThread(threadId: String, toolNames: String)

    @Query("UPDATE threads SET disabledSkills = :skillNames WHERE id = :threadId")
    suspend fun setDisabledSkills(threadId: String, skillNames: String)

    @Query("DELETE FROM threads WHERE id = :threadId")
    suspend fun delete(threadId: String)

    @Query("UPDATE threads SET answerStyle = :answerStyle WHERE id = :threadId")
    suspend fun setAnswerStyle(threadId: String, answerStyle: String?)

    @Query("UPDATE threads SET personaId = :personaId WHERE id = :threadId")
    suspend fun setPersona(threadId: String, personaId: String?)

    @Query("UPDATE threads SET instructions = :instructions WHERE id = :threadId")
    suspend fun setInstructions(threadId: String, instructions: String)
    /** Moves a thread into a project, or out of every project with null (D-110). */
    @Query("UPDATE threads SET projectId = :projectId WHERE id = :threadId")
    suspend fun setProject(threadId: String, projectId: String?)

    /** Leaves a deleted project's threads without a project; the threads themselves stay. */
    @Query("UPDATE threads SET projectId = NULL WHERE projectId = :projectId")
    suspend fun clearProject(projectId: String)

    /** Incognito threads with their last message time, for the deletion a day later (D-111). */
    @Query(IncognitoQueries.ACTIVITY)
    suspend fun listIncognitoActivity(): List<IncognitoThreadActivity>

    /** "Keep as a regular thread": stops the deletion; past messages stay out of memory (D-111). */
    @Query(IncognitoQueries.KEEP)
    suspend fun keepIncognito(threadId: String)
}

/** Projects that group threads (D-110). */
@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY name COLLATE NOCASE, createdAtMillis")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun find(projectId: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE id = :projectId")
    fun observe(projectId: String): Flow<ProjectEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(project: ProjectEntity)

    @Update
    suspend fun update(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun delete(projectId: String)
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

    @Query("UPDATE messages SET reasoningText = :reasoningText WHERE id = :messageId")
    suspend fun updateReasoning(messageId: String, reasoningText: String)

    /** Saved when the first visible text arrives, so the chat can time its first draw against it (D-132). */
    @Query(
        "UPDATE messages SET requestSentAtMillis = :sentAtMillis, requestSentElapsedMillis = :sentElapsedMillis, " +
            "firstTextElapsedMillis = :firstTextElapsedMillis WHERE id = :messageId",
    )
    suspend fun updateRequestTimes(messageId: String, sentAtMillis: Long, sentElapsedMillis: Long, firstTextElapsedMillis: Long)

    /** Only the first draw counts; a later redraw of the same answer keeps the first time. */
    @Query("UPDATE messages SET firstShownElapsedMillis = :shownElapsedMillis WHERE id = :messageId AND firstShownElapsedMillis IS NULL")
    suspend fun markFirstShown(messageId: String, shownElapsedMillis: Long)

    /** Removes a thread's messages from [fromPosition] on, when an edited prompt replaces them (D-056). */
    @Query("DELETE FROM messages WHERE threadId = :threadId AND position >= :fromPosition")
    suspend fun deleteFrom(threadId: String, fromPosition: Long)

    /** Messages left incomplete when Android stopped the app mid-stream. */
    @Query("UPDATE messages SET isComplete = 1 WHERE isComplete = 0")
    suspend fun closeInterrupted()

    /** Total cost of a thread in USD; null when no call in it has a known cost. */
    @Query("SELECT SUM(costUsd) FROM messages WHERE threadId = :threadId")
    fun observeThreadCost(threadId: String): Flow<Double?>

    /** Total cost of every call since [sinceMillis], for the month's total in the thread list. */
    @Query("SELECT SUM(costUsd) FROM messages WHERE createdAtMillis >= :sinceMillis")
    fun observeCostSince(sinceMillis: Long): Flow<Double?>

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
            "AND role = 'ASSISTANT' ORDER BY position DESC LIMIT 1",
    )
    fun observeLastInputTokens(threadId: String): Flow<Int?>

    /** The thread's latest user message: the source of a fact the model remembers during a run. */
    @Query("SELECT id FROM messages WHERE threadId = :threadId AND role = 'USER' ORDER BY position DESC LIMIT 1")
    suspend fun latestUserMessageId(threadId: String): String?

    /** Source messages of memory facts, for the memory screen. */
    @Query("SELECT * FROM messages WHERE id IN (:messageIds)")
    suspend fun findAll(messageIds: List<String>): List<MessageEntity>

    /** The full result the model got for one tool call; the step row keeps only a preview. */
    @Query("SELECT * FROM messages WHERE toolCallId = :toolCallId AND role = 'TOOL' LIMIT 1")
    suspend fun findToolResult(toolCallId: String): MessageEntity?
}

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

    @Query("SELECT * FROM steps WHERE subagentId = :subagentId ORDER BY startedAtMillis")
    suspend fun listOfSubagent(subagentId: String): List<StepEntity>

    @Upsert
    suspend fun upsert(step: StepEntity)

    /** Steps left running when Android stopped the app. */
    @Query("UPDATE steps SET status = 'STOPPED' WHERE status IN ('RUNNING', 'WAITING_FOR_APPROVAL')")
    suspend fun stopInterrupted()

    @Query("DELETE FROM steps WHERE toolCallId IN (:toolCallIds)")
    suspend fun deleteAll(toolCallIds: List<String>)

    /** The steps of the subagents that the delegate calls [parentToolCallIds] started. */
    @Query(
        "DELETE FROM steps WHERE subagentId IN " +
            "(SELECT id FROM subagents WHERE parentToolCallId IN (:parentToolCallIds))",
    )
    suspend fun deleteOfSubagentsUnder(parentToolCallIds: List<String>)
}

@Dao
interface SubagentDao {
    @Query("SELECT * FROM subagents WHERE threadId = :threadId ORDER BY startedAtMillis, orderInCall")
    fun observeThread(threadId: String): Flow<List<SubagentEntity>>

    @Query("SELECT * FROM subagents WHERE id = :subagentId")
    suspend fun find(subagentId: String): SubagentEntity?

    @Upsert
    suspend fun upsert(subagent: SubagentEntity)

    @Query("UPDATE subagents SET latestText = :text WHERE id = :subagentId")
    suspend fun setLatestText(subagentId: String, text: String)

    @Query("UPDATE subagents SET costUsd = :costUsd WHERE id = :subagentId")
    suspend fun setCost(subagentId: String, costUsd: Double?)

    @Query("DELETE FROM subagents WHERE parentToolCallId IN (:parentToolCallIds)")
    suspend fun deleteUnder(parentToolCallIds: List<String>)

    /** Subagents left running when Android stopped the app. */
    @Query("UPDATE subagents SET status = 'STOPPED' WHERE status = 'RUNNING'")
    suspend fun stopInterrupted()
}

@Dao
interface MemoryDao {
    /** Global facts, pinned first, then newest; facts waiting for review included (the screen marks them). */
    @Query("SELECT * FROM memories WHERE threadId IS NULL AND projectId IS NULL ORDER BY pinned DESC, updatedAtMillis DESC")
    fun observeGlobal(): Flow<List<MemoryEntity>>

    /** One project's facts, pinned first, then newest (D-135). */
    @Query("SELECT * FROM memories WHERE projectId = :projectId ORDER BY pinned DESC, updatedAtMillis DESC")
    fun observeProject(projectId: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE threadId = :threadId ORDER BY pinned DESC, updatedAtMillis DESC")
    fun observeThread(threadId: String): Flow<List<MemoryEntity>>

    /** Extracted facts of every thread that wait for the user's approval (review mode). */
    @Query("SELECT * FROM memories WHERE pendingReview = 1 ORDER BY createdAtMillis DESC")
    fun observePendingReview(): Flow<List<MemoryEntity>>

    /**
     * Facts the model may see in one thread: global, the thread's project's
     * (none when [projectId] is null) and the thread's own, without those
     * waiting for review, in the order injection picks them.
     */
    @Query(
        "SELECT * FROM memories WHERE pendingReview = 0 AND (" + MemorySearchIndex.VISIBLE_FROM_THREAD + ") " +
            "ORDER BY pinned DESC, lastUsedAtMillis DESC, id DESC",
    )
    suspend fun listVisibleFrom(threadId: String, projectId: String?): List<MemoryEntity>

    /** A thread's facts, including those waiting for review, for background extraction. */
    @Query("SELECT * FROM memories WHERE threadId = :threadId ORDER BY id")
    suspend fun listThread(threadId: String): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE threadId IS NULL AND projectId IS NULL ORDER BY id")
    suspend fun listGlobal(): List<MemoryEntity>

    /** A project's facts, including those waiting for review, for background extraction. */
    @Query("SELECT * FROM memories WHERE projectId = :projectId ORDER BY id")
    suspend fun listProject(projectId: String): List<MemoryEntity>

    /** Keeps a deleted project's facts as global ones (D-135). */
    @Query("UPDATE memories SET projectId = NULL, scope = 'global' WHERE projectId = :projectId")
    suspend fun makeProjectFactsGlobal(projectId: String)

    @Query("DELETE FROM memories WHERE projectId = :projectId")
    suspend fun deleteProjectFacts(projectId: String)

    @Query("SELECT * FROM memories WHERE id = :memoryId")
    suspend fun find(memoryId: Long): MemoryEntity?

    @Insert
    suspend fun insert(memory: MemoryEntity): Long

    @Update
    suspend fun update(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :memoryId")
    suspend fun delete(memoryId: Long)

    @Query("UPDATE memories SET lastUsedAtMillis = :usedAtMillis WHERE id IN (:memoryIds)")
    suspend fun markUsed(memoryIds: List<Long>, usedAtMillis: Long)

    /** Full-text search with the FTS5 trigram index; [phrase] comes from [MemorySearchIndex.matchPhrase]. */
    // Room checks queries against its own tables at build time and cannot see the FTS5 table.
    @SkipQueryVerification
    @Query(MemorySearchIndex.MATCH_SEARCH)
    suspend fun searchByMatch(phrase: String, threadId: String, projectId: String?, limit: Int): List<MemoryEntity>

    /** For queries under three characters; [pattern] comes from [MemorySearchIndex.likePattern]. */
    @Query(MemorySearchIndex.LIKE_SEARCH)
    suspend fun searchByLike(pattern: String, threadId: String, projectId: String?, limit: Int): List<MemoryEntity>
}

/** Summaries of older messages, written by compaction (M4 step 6). */
@Dao
interface CompactionDao {
    @Insert
    suspend fun insert(compaction: CompactionEntity)

    /** Summaries that cover any message from [fromPosition] on; an edit makes them out of date (D-056). */
    @Query("DELETE FROM compactions WHERE threadId = :threadId AND upToPosition >= :fromPosition")
    suspend fun deleteCoveringFrom(threadId: String, fromPosition: Long)

    /** The summary that covers the most messages of the thread; null before the first compaction. */
    @Query("SELECT * FROM compactions WHERE threadId = :threadId ORDER BY upToPosition DESC LIMIT 1")
    suspend fun latestForThread(threadId: String): CompactionEntity?

    /** The same summary as [latestForThread], for the chat's divider. */
    @Query("SELECT * FROM compactions WHERE threadId = :threadId ORDER BY upToPosition DESC LIMIT 1")
    fun observeLatestForThread(threadId: String): Flow<CompactionEntity?>
}

data class ThreadSummary(
    @Embedded val thread: ThreadEntity,
    val lastText: String?,
    val lastRole: String?,
    val totalCostUsd: Double?,
)
