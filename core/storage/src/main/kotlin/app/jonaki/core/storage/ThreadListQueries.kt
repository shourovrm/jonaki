package app.jonaki.core.storage

/**
 * The SQL behind the thread list, kept as a constant so a test can run the
 * same statement on the JVM.
 */
object ThreadListQueries {
    /**
     * Each thread with the text and role of its latest user, assistant or
     * error row that has text, for the thread list. Both columns skip the
     * same rows: during a run the newest row is a tool call without text, and
     * a role from that row made the user's text look like an answer, so its
     * time line for the model stayed in the preview.
     */
    const val SUMMARIES =
        "SELECT threads.*, (SELECT messages.text FROM messages WHERE messages.threadId = threads.id " +
            "AND messages.role NOT IN ('TOOL', 'BACKGROUND') AND messages.text != '' " +
            "ORDER BY messages.position DESC LIMIT 1) AS lastText, " +
            "(SELECT messages.role FROM messages WHERE messages.threadId = threads.id " +
            "AND messages.role NOT IN ('TOOL', 'BACKGROUND') AND messages.text != '' " +
            "ORDER BY messages.position DESC LIMIT 1) AS lastRole, " +
            "(SELECT SUM(messages.costUsd) FROM messages WHERE messages.threadId = threads.id) AS totalCostUsd " +
            "FROM threads ORDER BY updatedAtMillis DESC"
}
