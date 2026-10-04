package app.jonaki.memory

import android.util.Log
import app.jonaki.core.model.Role
import app.jonaki.core.storage.JonakiDatabase
import app.jonaki.core.storage.MessageEntity
import app.jonaki.guard.FactScreen
import app.jonaki.run.BackgroundAnswer
import app.jonaki.run.BackgroundModel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Background memory extraction (D-009, D-036): reads a thread's messages
 * since the last extraction, asks the background model for add, update and
 * delete operations and applies the allowed ones to the thread's facts. An
 * update keeps the old text as a superseded fact and a delete only marks the
 * fact superseded (the memory screen can restore both).
 */
class MemoryExtractor(
    private val database: JonakiDatabase,
    private val backgroundModel: BackgroundModel,
    /** Review mode: extracted facts wait for the user's approval. */
    private val reviewMode: () -> Boolean,
    private val clock: () -> Long,
    /** Whether extraction may propose global facts; they always wait for the user's review. */
    private val proposeGlobalFacts: () -> Boolean = { true },
    /** Whether facts found in a thread that read outside content wait for the user's review. */
    private val holdFactsAfterOutsideContent: () -> Boolean = { true },
    /** The guard's view of a fact found after outside content; see [FactScreen.looksPlanted]. */
    private val looksPlanted: suspend (threadId: String, factText: String) -> Boolean? = { _, _ -> null },
) {
    /** One extraction at a time, so two triggers cannot read and apply the same messages twice. */
    private val lock = Mutex()

    /**
     * Extracts when the thread has at least [minimumNewMessages] user and
     * assistant messages that no extraction has read yet.
     */
    suspend fun extractIfDue(threadId: String, threadModelKey: String?, minimumNewMessages: Int) {
        lock.withLock {
            extractLocked(threadId, threadModelKey, minimumNewMessages)
        }
    }

    private suspend fun extractLocked(threadId: String, threadModelKey: String?, minimumNewMessages: Int) {
        // Superseded facts older than 30 days go whenever extraction runs, due or not.
        database.memoryDao().deleteSupersededBefore(ExtractionWrites.supersededDeleteCutoff(clock()))
        val thread = database.threadDao().find(threadId) ?: return
        // A second guard after the runner's: an incognito thread's messages never reach memory (D-111).
        if (!ThreadMemory.isOn(thread)) {
            return
        }
        val rows = database.messageDao().listThread(threadId)
        val readUpTo = thread.memoryExtractedUpToPosition ?: -1L
        val newMessages = rows.filter { row -> row.position > readUpTo && isConversation(row) }
        if (newMessages.size < minimumNewMessages || newMessages.isEmpty()) {
            return
        }
        val lastPosition = rows.maxOf { row -> row.position }
        val messages = newMessages.takeLast(MAX_MESSAGES_PER_CALL).map { row ->
            ExtractionMessage(id = row.id, isUser = row.role == Role.USER.name, text = row.text)
        }
        val memoryDao = database.memoryDao()
        val threadFacts = memoryDao.listThread(threadId)
        val globalFacts = memoryDao.listGlobal()
        // A project deleted meanwhile counts as none.
        val projectId = thread.projectId?.takeIf { id -> database.projectDao().find(id) != null }
        val projectFacts = projectId?.let { id -> memoryDao.listProject(id) }
        val allowGlobalFacts = proposeGlobalFacts()
        val answer = backgroundModel.complete(
            threadId = threadId,
            threadModelKey = threadModelKey,
            systemPrompt = MemoryExtraction.systemPrompt(inProject = projectId != null, allowGlobalFacts = allowGlobalFacts),
            userText = MemoryExtraction.userPrompt(messages, threadFacts, globalFacts, projectFacts),
            maxOutputTokens = MAX_OUTPUT_TOKENS,
        )
        if (answer is BackgroundAnswer.Failed) {
            // The mark stays, so the next trigger tries these messages again.
            Log.w(TAG, "Memory extraction failed: ${answer.message}")
            return
        }
        val text = (answer as BackgroundAnswer.Success).text
        val plan = MemoryExtraction.plan(MemoryExtraction.parse(text), threadFacts, globalFacts, messages, projectFacts, allowGlobalFacts)
        if (plan.failure != null) {
            // Asking again would likely cost as much and fail the same way, so these messages count as read.
            Log.w(TAG, "Memory extraction answer unreadable: ${plan.failure}")
        }
        apply(threadId, projectId, plan)
        database.threadDao().setMemoryExtractedUpTo(threadId, lastPosition)
    }

    private suspend fun apply(threadId: String, projectId: String?, plan: ExtractionPlan) {
        val memoryDao = database.memoryDao()
        val now = clock()
        val waitForReview = reviewMode()
        // Read now, not at the start of the call: the thread may have read outside content meanwhile.
        val readOutsideContent = database.threadDao().find(threadId)?.readOutsideContent == true
        val holdAfterOutsideContent = holdFactsAfterOutsideContent() && readOutsideContent
        for (newFact in plan.adds) {
            // Asked only for a fact that would otherwise be held, so plain threads cost nothing.
            val heldAsPlanted = holdAfterOutsideContent && FactScreen.waitsForReview(looksPlanted(threadId, newFact.text))
            val pendingReview = ExtractionWrites.waitsForReview(newFact, waitForReview, heldAsPlanted)
            memoryDao.insert(ExtractionWrites.newMemory(newFact, threadId, projectId, now, pendingReview))
        }
        for (update in plan.updates) {
            val current = memoryDao.find(update.factId) ?: continue
            // The old text stays, as a superseded copy, so that the user can bring it back.
            memoryDao.insert(ExtractionWrites.supersededCopy(current, now))
            memoryDao.update(ExtractionWrites.updated(current, update, now))
        }
        for (factId in plan.deletes) {
            memoryDao.supersede(factId, now)
        }
    }

    private fun isConversation(row: MessageEntity): Boolean {
        val isUserOrAssistant = row.role == Role.USER.name || row.role == Role.ASSISTANT.name
        return isUserOrAssistant && row.text.isNotBlank()
    }

    companion object {
        /** Extraction runs after a run once this many messages are unread (D-009: every 20 messages). */
        const val MESSAGES_PER_EXTRACTION = 20

        /** Leaving a thread after at least one exchange extracts what it holds. */
        const val MESSAGES_FOR_EXTRACTION_ON_LEAVE = 2

        private const val MAX_MESSAGES_PER_CALL = 40
        private const val MAX_OUTPUT_TOKENS = 1_000
        private const val TAG = "MemoryExtractor"
    }
}
