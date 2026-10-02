package app.jonaki.memory

import app.jonaki.core.storage.JonakiDatabase
import app.jonaki.core.storage.MemoryEntity
import app.jonaki.core.storage.MemoryOrigin
import app.jonaki.core.storage.MemoryScope

/** The memory screen's changes (D-008: add, edit, delete, pin, promote; review mode). */
class MemoryEdits(private val database: JonakiDatabase, private val clock: () -> Long) {
    private val memoryDao = database.memoryDao()

    /** [threadId] null adds a global fact. */
    suspend fun add(text: String, threadId: String?) {
        val now = clock()
        memoryDao.insert(
            MemoryEntity(
                scope = if (threadId == null) MemoryScope.GLOBAL else MemoryScope.THREAD,
                threadId = threadId,
                text = text,
                origin = MemoryOrigin.USER,
                createdAtMillis = now,
                updatedAtMillis = now,
            ),
        )
    }

    /** An edited fact counts as the user's own, so background extraction no longer changes it (D-036). */
    suspend fun edit(factId: Long, text: String) {
        val fact = memoryDao.find(factId) ?: return
        memoryDao.update(fact.copy(text = text, origin = MemoryOrigin.USER, pendingReview = false, updatedAtMillis = clock()))
    }

    suspend fun delete(factId: Long) {
        memoryDao.delete(factId)
    }

    suspend fun setPinned(factId: Long, pinned: Boolean) {
        val fact = memoryDao.find(factId) ?: return
        memoryDao.update(fact.copy(pinned = pinned, updatedAtMillis = clock()))
    }

    /** Moves a thread fact to all threads; when a global fact already says the same, the thread copy goes. */
    suspend fun promote(factId: Long) {
        val fact = memoryDao.find(factId) ?: return
        if (fact.threadId == null) {
            return
        }
        if (FactText.findSame(memoryDao.listGlobal(), fact.text) != null) {
            memoryDao.delete(factId)
            return
        }
        memoryDao.update(fact.copy(scope = MemoryScope.GLOBAL, threadId = null, pendingReview = false, updatedAtMillis = clock()))
    }

    /** Approves a fact that waited for review. */
    suspend fun keep(factId: Long) {
        val fact = memoryDao.find(factId) ?: return
        memoryDao.update(fact.copy(pendingReview = false, updatedAtMillis = clock()))
    }
}
