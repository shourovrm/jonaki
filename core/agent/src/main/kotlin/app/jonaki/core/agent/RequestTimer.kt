package app.jonaki.core.agent

/**
 * The request-log times of one model request (D-132). Elapsed times are
 * milliseconds since boot, which do not jump when the user changes the
 * clock; [sentAtMillis] is the wall clock, for showing when it was sent.
 */
data class RequestTimes(
    val sentAtMillis: Long,
    val sentElapsedMillis: Long,
    /** When the first visible answer text arrived; null for a turn that only called tools. */
    val firstTextElapsedMillis: Long?,
)

/**
 * Times the model requests of one run from its events, in the order the
 * agent loop records them: [AgentEvent.RequestSent] starts a request, and the
 * first text delta with a visible character ends its wait. Blank deltas do
 * not count, because the chat draws nothing for them.
 */
class RequestTimer(
    private val elapsedClock: () -> Long,
    private val wallClock: () -> Long,
) {
    /** The latest request's times; null before the first request. */
    var current: RequestTimes? = null
        private set

    /** Returns true when [event] is the first visible text of the current request, so the caller can save the times. */
    fun observe(event: AgentEvent): Boolean {
        if (event is AgentEvent.RequestSent) {
            current = RequestTimes(sentAtMillis = wallClock(), sentElapsedMillis = elapsedClock(), firstTextElapsedMillis = null)
            return false
        }
        val request = current ?: return false
        val isFirstVisibleText = event is AgentEvent.TextDelta &&
            event.text.isNotBlank() &&
            request.firstTextElapsedMillis == null
        if (isFirstVisibleText) {
            current = request.copy(firstTextElapsedMillis = elapsedClock())
        }
        return isFirstVisibleText
    }
}
