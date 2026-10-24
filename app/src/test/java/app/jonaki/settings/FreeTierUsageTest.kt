package app.jonaki.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Jonaki Free counter the Settings card shows: calls made today on this
 * phone, reset at local midnight. The Worker enforces the same limit (D-186).
 */
class FreeTierUsageTest {
    private val store = InMemoryStore()

    @Test
    fun `counts calls on the same day`() {
        var now = 1_761_350_400_000L // 2026-10-25 00:00 UTC
        val usage = FreeTierUsage(store, clock = { now }, zoneId = "UTC")
        assertEquals(0, usage.todayCount())
        usage.record()
        usage.record()
        now += 3_600_000L
        assertEquals(2, usage.todayCount())
    }

    @Test
    fun `starts again after midnight`() {
        var now = 1_761_350_400_000L
        val usage = FreeTierUsage(store, clock = { now }, zoneId = "UTC")
        usage.record()
        now += 24 * 3_600_000L
        assertEquals(0, usage.todayCount())
        usage.record()
        assertEquals(1, usage.todayCount())
    }

    @Test
    fun `the daily limit is sixty calls`() {
        assertEquals(60, FreeTierUsage.DAILY_CALL_LIMIT)
    }

    private class InMemoryStore : FreeTierUsage.Store {
        private var day: String? = null
        private var count: Int = 0
        override fun read(): Pair<String?, Int> = day to count
        override fun write(day: String, count: Int) {
            this.day = day
            this.count = count
        }
    }
}
