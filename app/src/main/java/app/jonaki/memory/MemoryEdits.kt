package app.jonaki.memory

import app.jonaki.core.storage.JonakiDatabase
import app.jonaki.core.storage.MemoryEntity
import app.jonaki.core.storage.MemoryOrigin
import app.jonaki.core.storage.MemoryScope

/** The memory screen's changes (D-008: add, edit, delete, pin, promote; review mode). */
class MemoryEdits(private val database: JonakiDatabase, private val clock: () -> Long) {
    private val memoryDao = database.memoryDao()

    /**
     * Adds a fact the user typed: to [threadId] when given, else to
     * [projectId] when given (D-135), else to all threads.
     */
    suspend fun add(text: String, threadId: String?, projectId: String? = null) {
        val now = clock()
        val scope = when {
            threadId != null -> MemoryScope.THREAD
            projectId != null -> MemoryScope.PROJECT
            else -> MemoryScope.GLOBAL
        }
        memoryDao.insert(
            MemoryEntity(
                scope = scope,
                threadId = threadId,
                projectId = if (threadId == null) projectId else null,
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
        memoryDao.update(edited(fact, text, clock()))
    }

    /** Puts a superseded fact back in use (the memory screen's Restore). */
    suspend fun restore(factId: Long) {
        memoryDao.restore(factId, clock())
    }

    suspend fun delete(factId: Long) {
        memoryDao.delete(factId)
    }

    suspend fun setPinned(factId: Long, pinned: Boolean) {
        val fact = memoryDao.find(factId) ?: return
        memoryDao.update(fact.copy(pinned = pinned, updatedAtMillis = clock()))
    }

    /**
     * Moves a thread or project fact to all threads; when a global fact
     * already says the same, the narrower copy goes.
     */
    suspend fun promote(factId: Long) {
        val fact = memoryDao.find(factId) ?: return
        if (fact.threadId == null && fact.projectId == null) {
            return
        }
        if (FactText.findSame(memoryDao.listGlobal(), fact.text) != null) {
            memoryDao.delete(factId)
            return
        }
        memoryDao.update(
            fact.copy(scope = MemoryScope.GLOBAL, threadId = null, projectId = null, pendingReview = false, updatedAtMillis = clock()),
        )
    }

    /**
     * Moves a thread fact to the thread's project (D-135); when the project
     * already has the same fact, the thread copy goes.
     */
    suspend fun moveToProject(factId: Long, projectId: String) {
        val fact = memoryDao.find(factId) ?: return
        if (fact.threadId == null) {
            return
        }
        if (FactText.findSame(memoryDao.listProject(projectId), fact.text) != null) {
            memoryDao.delete(factId)
            return
        }
        memoryDao.update(
            fact.copy(scope = MemoryScope.PROJECT, threadId = null, projectId = projectId, pendingReview = false, updatedAtMillis = clock()),
        )
    }

    /** Approves a fact that waited for review. */
    suspend fun keep(factId: Long) {
        val fact = memoryDao.find(factId) ?: return
        memoryDao.update(fact.copy(pendingReview = false, updatedAtMillis = clock()))
    }

    companion object {
        /**
         * The fact after the user edited its text. New text may no longer fit the keywords,
         * which described the old text, so a changed text clears them.
         */
        fun edited(fact: MemoryEntity, text: String, now: Long): MemoryEntity {
            val keywords = if (text == fact.text) fact.keywords else ""
            return fact.copy(text = text, keywords = keywords, origin = MemoryOrigin.USER, pendingReview = false, updatedAtMillis = now)
        }
    }
}
