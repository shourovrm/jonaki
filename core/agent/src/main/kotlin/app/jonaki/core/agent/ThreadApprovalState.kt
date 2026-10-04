package app.jonaki.core.agent

/**
 * What one thread has done that changes which calls ask: the user chose
 * "Allow all in this thread", and the thread has read outside content. Both
 * survive an app restart, so [save] writes them each time one changes. One
 * state serves one thread; the chat's chip and the thread's run share it.
 */
class ThreadApprovalState(
    allowAllInThread: Boolean = false,
    readOutsideContent: Boolean = false,
    /** Writes both values to the thread's row. */
    private val save: suspend (allowAllInThread: Boolean, readOutsideContent: Boolean) -> Unit = { _, _ -> },
) {
    @Volatile
    var allowAllInThread: Boolean = allowAllInThread
        private set

    /**
     * True once any outside content reached the thread. It never goes back to
     * false: the outside text stays in the thread's history.
     */
    @Volatile
    var readOutsideContent: Boolean = readOutsideContent
        private set

    suspend fun grantAllowAll() {
        allowAllInThread = true
        save(allowAllInThread, readOutsideContent)
    }

    suspend fun withdrawAllowAll() {
        allowAllInThread = false
        save(allowAllInThread, readOutsideContent)
    }

    suspend fun markOutsideContentRead() {
        if (readOutsideContent) {
            return
        }
        readOutsideContent = true
        save(allowAllInThread, readOutsideContent)
    }
}
