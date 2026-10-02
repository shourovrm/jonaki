package app.jonaki.memory

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
 * The memory tool's view of the database for one thread: the global facts
 * and this thread's facts, never another thread's.
 */
class RoomMemoryStore(
    private val database: JonakiDatabase,
    private val threadId: String,
    private val clock: () -> Long,
) : MemoryStore {
    private val memoryDao = database.memoryDao()

    override suspend fun remember(scope: FactScope, text: String): RememberResult {
        val visible = memoryDao.listVisibleFrom(threadId)
        val same = FactText.findSame(visible, text)
        if (same != null) {
            val promotesThreadFact = scope == FactScope.GLOBAL && same.threadId != null
            if (!promotesThreadFact) {
                return RememberResult.AlreadyKnown(factOf(same))
            }
            // Asked to remember for all threads what this thread already knows: move the fact up.
            val promoted = same.copy(scope = MemoryScope.GLOBAL, threadId = null, updatedAtMillis = clock())
            memoryDao.update(promoted)
            return RememberResult.Saved(factOf(promoted))
        }
        val now = clock()
        val isGlobal = scope == FactScope.GLOBAL
        val memory = MemoryEntity(
            scope = if (isGlobal) MemoryScope.GLOBAL else MemoryScope.THREAD,
            threadId = if (isGlobal) null else threadId,
            text = text,
            sourceMessageId = database.messageDao().latestUserMessageId(threadId),
            origin = MemoryOrigin.TOOL,
            createdAtMillis = now,
            updatedAtMillis = now,
            lastUsedAtMillis = now,
        )
        val id = memoryDao.insert(memory)
        return RememberResult.Saved(factOf(memory.copy(id = id)))
    }

    override suspend fun forget(factId: Long): ForgetResult {
        val memory = memoryDao.find(factId)
        val isVisible = memory != null && !memory.pendingReview && (memory.threadId == null || memory.threadId == threadId)
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
        val found = if (MemorySearchIndex.usesMatch(query)) {
            memoryDao.searchByMatch(MemorySearchIndex.matchPhrase(query), threadId, limit)
        } else {
            memoryDao.searchByLike(MemorySearchIndex.likePattern(query), threadId, limit)
        }
        if (found.isNotEmpty()) {
            // A recalled fact counts as used, so it can enter the next run's prompt (D-034).
            memoryDao.markUsed(found.map { memory -> memory.id }, clock())
        }
        return found.map(::factOf)
    }

    private fun factOf(memory: MemoryEntity): Fact = Fact(
        id = memory.id,
        scope = if (memory.threadId == null) FactScope.GLOBAL else FactScope.THREAD,
        text = memory.text,
        pinned = memory.pinned,
    )
}
