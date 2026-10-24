package app.jonaki.settings

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.Assert.assertEquals

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
    fun `the daily limit is twenty calls`() {
        assertEquals(20, FreeTierUsage.DAILY_CALL_LIMIT)
    }

    @Test
    fun `fetches the server limit once per day and sends the device header`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"limit\":30,\"used\":7,\"resets_at\":\"2026-10-26T00:00:00.000Z\"}"))
            val usage = FreeTierUsage(store, clock = { 1_761_350_400_000L }, zoneId = "UTC")
            assertNull(usage.serverLimit())
            assertEquals(FreeTierUsage.DAILY_CALL_LIMIT, usage.effectiveLimit())

            runBlocking {
                usage.refreshLimit(OkHttpClient(), server.url("/v1").toString(), "device-1")
                usage.refreshLimit(OkHttpClient(), server.url("/v1").toString(), "device-1")
            }

            assertEquals(1, server.requestCount)
            val request = server.takeRequest()
            assertEquals("/v1/limit", request.path)
            assertEquals("device-1", request.getHeader("X-Jonaki-Device"))
            assertEquals(30, usage.effectiveLimit())
            assertEquals(7, usage.todayCount())
        }
    }

    @Test
    fun `a failed fetch keeps the stored limit and does not throw`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))
            server.enqueue(MockResponse().setBody("not json"))
            store.writeLimit("2026-10-24", 45)
            val usage = FreeTierUsage(store, clock = { 1_761_350_400_000L }, zoneId = "UTC")

            runBlocking { usage.refreshLimit(OkHttpClient(), server.url("/v1").toString(), "device-1") }
            runBlocking { usage.refreshLimit(OkHttpClient(), server.url("/v1").toString(), "device-1") }

            assertEquals(2, server.requestCount)
            assertEquals(45, usage.effectiveLimit())
        }
    }

    private class InMemoryStore : FreeTierUsage.Store {
        private var day: String? = null
        private var count: Int = 0
        override fun read(): Pair<String?, Int> = day to count
        override fun write(day: String, count: Int) {
            this.day = day
            this.count = count
        }

        private var limitDay: String? = null
        private var limit: Int? = null
        override fun readLimit(): Pair<String?, Int?> = limitDay to limit
        override fun writeLimit(day: String, limit: Int) {
            limitDay = day
            this.limit = limit
        }
    }
}
