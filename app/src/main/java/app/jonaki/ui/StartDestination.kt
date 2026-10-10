package app.jonaki.ui

/** The thread the user last left and the moment they left it. */
data class LeftThread(val threadId: String, val leftAtMillis: Long)

/** What the database knows about a recorded thread; null instead of this when the thread is gone. */
data class KnownThread(val incognito: Boolean)

sealed interface StartChoice {
    /** A share, the first-run picker or similar already decided what the start shows. */
    data object KeepGivenDestination : StartChoice

    data class ReopenThread(val threadId: String) : StartChoice

    /** An empty thread, as the New thread button opens; it has no database row until the first message. */
    data object NewThread : StartChoice
}

/** Decides what a fresh start of the app shows. Pure, so the time rules are tested on the JVM. */
object StartDestination {
    /** A thread left less than this long ago is reopened. */
    const val REOPEN_WITHIN_MILLIS = 30L * 60 * 1000

    fun decide(
        hasGivenDestination: Boolean,
        record: LeftThread?,
        recordedThread: KnownThread?,
        nowMillis: Long,
    ): StartChoice {
        if (hasGivenDestination) {
            return StartChoice.KeepGivenDestination
        }
        if (record == null || recordedThread == null || recordedThread.incognito) {
            return StartChoice.NewThread
        }
        val millisSinceLeft = nowMillis - record.leftAtMillis
        // A negative value means the clock moved backwards; trusting it would reopen the thread for as long as the clock stays behind.
        val isRecent = millisSinceLeft in 0 until REOPEN_WITHIN_MILLIS
        return if (isRecent) StartChoice.ReopenThread(record.threadId) else StartChoice.NewThread
    }
}
