package app.jonaki.core.agent

/**
 * The sentence that closes a failed subagent's result text, and the way back
 * to the failure from the saved text. The saved row has no column for the
 * failure, so the app reads it out of the result text (which the row stores
 * as it went to the thread's agent) with [failureOf].
 */
object SubagentEnding {
    private const val FAILURE_PREFIX = "Stopped because the model call failed: "

    /** A provider's error text can carry a whole response body; the row needs the first part. */
    private const val MAX_FAILURE_CHARACTERS = 1_000

    internal fun failureSentence(failure: String?): String {
        val text = failure.orEmpty().trim()
        val shortened = if (text.length > MAX_FAILURE_CHARACTERS) text.take(MAX_FAILURE_CHARACTERS) + "…" else text
        return FAILURE_PREFIX + shortened
    }

    /**
     * The failure a saved result text ends with; null for any other stop and
     * for rows saved before the failure was part of the text.
     */
    fun failureOf(resultText: String?): String? {
        if (resultText == null) {
            return null
        }
        val start = resultText.lastIndexOf(FAILURE_PREFIX)
        if (start < 0) {
            return null
        }
        return resultText.substring(start + FAILURE_PREFIX.length).trim().ifEmpty { null }
    }
}
