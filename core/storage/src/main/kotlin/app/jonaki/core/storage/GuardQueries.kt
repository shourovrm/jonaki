package app.jonaki.core.storage

/**
 * The SQL behind the thread's cost total and the step's guard note, kept as
 * constants so a test can run the same statements on the JVM.
 */
object GuardQueries {
    /**
     * The thread's total cost. It sums every row, hidden BACKGROUND rows
     * included, which is how a guard call's cost reaches the total (D-036).
     */
    const val THREAD_COST = "SELECT SUM(costUsd) FROM messages WHERE threadId = :threadId"

    /**
     * Adds a line to a step's guard note. A step has up to two guard
     * answers (one about the call, one about its result), so the second
     * goes under the first, separated by a line break, in one statement
     * that cannot lose the first.
     */
    const val APPEND_GUARD_NOTE =
        "UPDATE steps SET guardNote = CASE WHEN guardNote IS NULL THEN :note ELSE guardNote || char(10) || :note END " +
            "WHERE toolCallId = :toolCallId"
}
