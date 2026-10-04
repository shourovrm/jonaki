package app.jonaki.memory

import app.jonaki.core.storage.MemoryEntity
import app.jonaki.core.storage.MemoryOrigin
import app.jonaki.core.storage.MemoryScope

/** How a checked [ExtractionPlan] becomes rows; pure, so that [MemoryExtractor] only has to write them. */
object ExtractionWrites {
    private const val SUPERSEDED_KEPT_MILLIS = 30L * 24 * 60 * 60 * 1000

    /**
     * A proposed global fact always waits, because it reaches every thread. Any other
     * fact waits in review mode, or after the thread read outside content.
     */
    fun waitsForReview(newFact: NewFact, reviewMode: Boolean, holdAfterOutsideContent: Boolean): Boolean =
        newFact.forGlobal || reviewMode || holdAfterOutsideContent

    fun newMemory(newFact: NewFact, threadId: String, projectId: String?, now: Long, pendingReview: Boolean): MemoryEntity {
        val scope = when {
            newFact.forGlobal -> MemoryScope.GLOBAL
            newFact.forProject -> MemoryScope.PROJECT
            else -> MemoryScope.THREAD
        }
        return MemoryEntity(
            scope = scope,
            threadId = if (scope == MemoryScope.THREAD) threadId else null,
            projectId = if (scope == MemoryScope.PROJECT) projectId else null,
            text = newFact.text,
            keywords = newFact.keywords,
            sourceMessageId = newFact.sourceMessageId,
            origin = MemoryOrigin.EXTRACTED,
            pendingReview = pendingReview,
            createdAtMillis = now,
            updatedAtMillis = now,
        )
    }

    /**
     * The live fact after an update: same id, new text. The old keywords described the old
     * text, so only the keywords of the update are kept.
     */
    fun updated(current: MemoryEntity, update: FactUpdate, now: Long): MemoryEntity =
        current.copy(text = update.text, keywords = update.keywords, updatedAtMillis = now)

    /** A new row holding the text that an update replaced, so that the user can restore it. */
    fun supersededCopy(current: MemoryEntity, now: Long): MemoryEntity =
        current.copy(id = 0, pinned = false, supersededAtMillis = now)

    /** Superseded facts replaced before this time are deleted. */
    fun supersededDeleteCutoff(now: Long): Long = now - SUPERSEDED_KEPT_MILLIS
}
