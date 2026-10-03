package app.jonaki.core.storage

/**
 * The SQL behind incognito threads (D-111), kept as constants so the
 * migration tests can run the same statements on the JVM.
 */
object IncognitoQueries {
    /**
     * Each incognito thread with the time of its last message. Background
     * rows (usage of memory or summary calls) are not messages the user
     * sent or read, so they do not extend the thread's life. A thread with
     * no message yet counts from its creation.
     */
    const val ACTIVITY =
        "SELECT threads.id AS threadId, COALESCE((SELECT MAX(messages.createdAtMillis) FROM messages " +
            "WHERE messages.threadId = threads.id AND messages.role != 'BACKGROUND'), threads.createdAtMillis) " +
            "AS lastMessageAtMillis FROM threads WHERE threads.incognito = 1"

    /**
     * Makes an incognito thread regular. Its messages so far count as read
     * by memory extraction, because the user wrote them expecting privacy;
     * only later messages are offered to memory.
     */
    const val KEEP =
        "UPDATE threads SET incognito = 0, memoryExtractedUpToPosition = " +
            "(SELECT MAX(messages.position) FROM messages WHERE messages.threadId = :threadId) WHERE id = :threadId"
}

/** One incognito thread and the time its last message was written. */
data class IncognitoThreadActivity(
    val threadId: String,
    val lastMessageAtMillis: Long,
)
