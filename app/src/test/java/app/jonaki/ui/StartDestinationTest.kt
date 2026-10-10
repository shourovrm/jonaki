package app.jonaki.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StartDestinationTest {
    private val now = 10_000_000_000L
    private val minute = 60_000L
    private val regularThread = KnownThread(incognito = false)

    private fun decide(
        hasGivenDestination: Boolean = false,
        record: LeftThread? = LeftThread("thread-1", leftAtMillis = now - 5 * minute),
        thread: KnownThread? = regularThread,
    ): StartChoice = StartDestination.decide(hasGivenDestination, record, thread, now)

    @Test
    fun `reopens a thread left five minutes ago`() {
        assertEquals(StartChoice.ReopenThread("thread-1"), decide())
    }

    @Test
    fun `reopens a thread left one millisecond short of thirty minutes`() {
        val record = LeftThread("thread-1", leftAtMillis = now - StartDestination.REOPEN_WITHIN_MILLIS + 1)
        assertEquals(StartChoice.ReopenThread("thread-1"), decide(record = record))
    }

    @Test
    fun `opens a new thread when the thread was left exactly thirty minutes ago`() {
        val record = LeftThread("thread-1", leftAtMillis = now - StartDestination.REOPEN_WITHIN_MILLIS)
        assertEquals(StartChoice.NewThread, decide(record = record))
    }

    @Test
    fun `opens a new thread when the thread was left two hours ago`() {
        val record = LeftThread("thread-1", leftAtMillis = now - 120 * minute)
        assertEquals(StartChoice.NewThread, decide(record = record))
    }

    @Test
    fun `opens a new thread when nothing was recorded`() {
        assertEquals(StartChoice.NewThread, decide(record = null, thread = null))
    }

    @Test
    fun `opens a new thread when the recorded thread was deleted`() {
        assertEquals(StartChoice.NewThread, decide(thread = null))
    }

    @Test
    fun `opens a new thread instead of reopening an incognito thread`() {
        assertEquals(StartChoice.NewThread, decide(thread = KnownThread(incognito = true)))
    }

    @Test
    fun `opens a new thread when the clock went backwards`() {
        val record = LeftThread("thread-1", leftAtMillis = now + 5 * minute)
        assertEquals(StartChoice.NewThread, decide(record = record))
    }

    @Test
    fun `reopens a thread left at this very moment`() {
        val record = LeftThread("thread-1", leftAtMillis = now)
        assertEquals(StartChoice.ReopenThread("thread-1"), decide(record = record))
    }

    @Test
    fun `keeps the given destination even when a recent thread could be reopened`() {
        assertEquals(StartChoice.KeepGivenDestination, decide(hasGivenDestination = true))
    }

    @Test
    fun `keeps the given destination when nothing was recorded`() {
        assertEquals(StartChoice.KeepGivenDestination, decide(hasGivenDestination = true, record = null, thread = null))
    }
}
