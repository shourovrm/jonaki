package app.jonaki.core.agent

import kotlin.time.Duration
import kotlinx.coroutines.delay

/**
 * Waits for a time; the real one delays, tests pass a virtual clock. Used
 * for the 3-minute approval rule (D-062) and the spacing of web searches
 * that run side by side (D-080).
 */
fun interface WaitTimer {
    suspend fun wait(duration: Duration)

    companion object {
        val REAL: WaitTimer = WaitTimer { duration -> delay(duration) }
    }
}
