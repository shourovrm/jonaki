package app.jonaki.core.agent

import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred

/**
 * A clock that moves only when a test calls [advanceBy]. Used in place of
 * kotlinx-coroutines-test, which the project does not depend on: approval
 * waits and the spacing of web searches go through [WaitTimer], and this
 * timer releases a wait once the virtual time passes its deadline.
 */
class VirtualClock : WaitTimer {
    private class Waiter(val deadline: Duration, val released: CompletableDeferred<Unit>)

    var now: Duration = Duration.ZERO
        private set

    private val waiters = mutableListOf<Waiter>()

    /** Every duration a wait asked for, in order. */
    val requestedWaits = mutableListOf<Duration>()

    override suspend fun wait(duration: Duration) {
        val waiter = Waiter(now + duration, CompletableDeferred())
        synchronized(waiters) {
            requestedWaits += duration
            waiters += waiter
        }
        try {
            waiter.released.await()
        } finally {
            synchronized(waiters) { waiters -= waiter }
        }
    }

    fun advanceBy(duration: Duration) {
        now += duration
        val due = synchronized(waiters) { waiters.filter { waiter -> waiter.deadline <= now } }
        due.forEach { waiter -> waiter.released.complete(Unit) }
    }

    val pendingWaits: Int
        get() = synchronized(waiters) { waiters.size }
}
