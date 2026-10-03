package app.jonaki.run

import app.jonaki.core.storage.IncognitoThreadActivity

/**
 * Deletes incognito threads one day after their last message (D-111).
 * The app calls [deleteExpired] at start and whenever the thread list
 * shows, so no background job is needed.
 */
class IncognitoCleanup(
    private val listIncognito: suspend () -> List<IncognitoThreadActivity>,
    private val isRunning: (threadId: String) -> Boolean,
    /** Deletes the thread's rows and its folder. */
    private val deleteThread: suspend (threadId: String) -> Unit,
    private val clock: () -> Long,
) {
    /** Returns the ids of the threads it deleted. */
    suspend fun deleteExpired(): List<String> {
        val nowMillis = clock()
        val deleted = mutableListOf<String>()
        for (thread in listIncognito()) {
            // A run writes a message soon; deleting under it would lose the answer mid-stream.
            if (isRunning(thread.threadId)) {
                continue
            }
            if (isExpired(thread.lastMessageAtMillis, nowMillis)) {
                deleteThread(thread.threadId)
                deleted += thread.threadId
            }
        }
        return deleted
    }

    companion object {
        const val LIFETIME_MILLIS = 24L * 60 * 60 * 1_000

        fun isExpired(lastMessageAtMillis: Long, nowMillis: Long): Boolean =
            nowMillis - lastMessageAtMillis >= LIFETIME_MILLIS
    }
}
