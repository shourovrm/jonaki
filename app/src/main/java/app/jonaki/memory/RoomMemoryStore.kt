package app.jonaki.memory

import app.jonaki.core.storage.FtsQuery
import app.jonaki.core.storage.JonakiDatabase
import app.jonaki.core.storage.MemoryEntity
import app.jonaki.core.storage.MemoryOrigin
import app.jonaki.core.storage.MemoryScope
import app.jonaki.core.storage.MemorySearchIndex
import app.jonaki.tools.memory.Fact
import app.jonaki.tools.memory.FactScope
import app.jonaki.tools.memory.ForgetResult
import app.jonaki.tools.memory.MemoryStore
import app.jonaki.tools.memory.RememberResult

/**
 * The memory tool's view of the database for one thread: the global facts,
 * the facts of the thread's project ([projectId], null for none, D-135) and
 * this thread's facts, never another thread's or another project's.
 */
class RoomMemoryStore(
    private val database: JonakiDatabase,
    private val threadId: String,
    private val projectId: String?,
    private val clock: () -> Long,
    /** Whether a fact saved after the thread read outside content waits for the user's approval. */
    private val holdFactsAfterOutsideContent: () -> Boolean = { true },
) : MemoryStore {
    private val memoryDao = database.memoryDao()

    override suspend fun remember(scope: FactScope, text: String, keywords: String): RememberResult {
        val visible = memoryDao.listVisibleFrom(threadId, projectId)
        val same = FactText.findSame(visible, text)
        // Read now: the thread can read a web page during a run, after the tool was built.
        val waitsForReview = holdFactsAfterOutsideContent() && threadReadOutsideContent()
        if (same != null) {
            val sameScope = scopeOf(same)
            if (reach(scope) <= reach(sameScope)) {
                return RememberResult.AlreadyKnown(factOf(same))
            }
            if (!waitsForReview) {
                // Asked to remember for more threads what fewer threads already know: move the fact up.
                val widened = same.copy(
                    scope = storedScopeOf(scope),
                    threadId = null,
                    projectId = projectIdFor(scope),
                    updatedAtMillis = clock(),
                )
                memoryDao.update(widened)
                return RememberResult.Saved(factOf(widened))
            }
            // A held fact must not change what the known fact reaches, so the wider copy waits as a new fact.
        }
        val now = clock()
        val memory = MemoryEntity(
            scope = storedScopeOf(scope),
            threadId = if (scope == FactScope.THREAD) threadId else null,
            projectId = projectIdFor(scope),
            text = text,
            keywords = FactKeywords.cleaned(keywords),
            sourceMessageId = database.messageDao().latestUserMessageId(threadId),
            origin = MemoryOrigin.TOOL,
            pendingReview = waitsForReview,
            createdAtMillis = now,
            updatedAtMillis = now,
            lastUsedAtMillis = now,
        )
        val id = memoryDao.insert(memory)
        return RememberResult.Saved(factOf(memory.copy(id = id)), waitsForReview)
    }

    private suspend fun threadReadOutsideContent(): Boolean =
        database.threadDao().find(threadId)?.readOutsideContent == true

    override suspend fun forget(factId: Long): ForgetResult {
        val memory = memoryDao.find(factId)
        val isVisible = memory != null && !memory.pendingReview && memory.supersededAtMillis == null && isVisibleHere(memory)
        if (memory == null || !isVisible) {
            return ForgetResult.NotFound
        }
        if (memory.pinned) {
            return ForgetResult.Pinned(factOf(memory))
        }
        memoryDao.delete(factId)
        return ForgetResult.Forgotten(factOf(memory))
    }

    override suspend fun recall(query: String, limit: Int): List<Fact> {
        // Any word of the query, best match first; no word long enough for the index falls back to LIKE.
        val match = FtsQuery.anyWordOf(query)
        val found = if (match != null) {
            memoryDao.searchByMatch(match, threadId, projectId, limit)
        } else {
            memoryDao.searchByLike(MemorySearchIndex.likePattern(query.trim()), threadId, projectId, limit)
        }
        if (found.isNotEmpty()) {
            // A recalled fact counts as used, so it can enter the next run's prompt (D-034).
            memoryDao.markUsed(found.map { memory -> memory.id }, clock())
        }
        return found.map(::factOf)
    }

    private fun isVisibleHere(memory: MemoryEntity): Boolean = when (scopeOf(memory)) {
        FactScope.GLOBAL -> true
        FactScope.PROJECT -> memory.projectId == projectId
        FactScope.THREAD -> memory.threadId == threadId
    }

    private fun factOf(memory: MemoryEntity): Fact = Fact(
        id = memory.id,
        scope = scopeOf(memory),
        text = memory.text,
        pinned = memory.pinned,
    )

    private fun projectIdFor(scope: FactScope): String? = if (scope == FactScope.PROJECT) projectId else null

    companion object {
        /** Read from the columns rather than the scope text, which older rows may not have kept in step. */
        fun scopeOf(memory: MemoryEntity): FactScope = when {
            memory.threadId != null -> FactScope.THREAD
            memory.projectId != null -> FactScope.PROJECT
            else -> FactScope.GLOBAL
        }

        fun storedScopeOf(scope: FactScope): String = when (scope) {
            FactScope.GLOBAL -> MemoryScope.GLOBAL
            FactScope.PROJECT -> MemoryScope.PROJECT
            FactScope.THREAD -> MemoryScope.THREAD
        }

        /** How many threads see a fact of this scope, in order. */
        private fun reach(scope: FactScope): Int = when (scope) {
            FactScope.THREAD -> 0
            FactScope.PROJECT -> 1
            FactScope.GLOBAL -> 2
        }
    }
}
