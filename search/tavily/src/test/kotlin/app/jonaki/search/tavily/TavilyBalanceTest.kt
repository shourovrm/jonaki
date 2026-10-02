package app.jonaki.search.tavily

import app.jonaki.core.balanceapi.Balance
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TavilyBalanceTest {
    private lateinit var server: MockWebServer

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        server.shutdown()
    }

    @Test
    fun planUsageAndLimitAreTheCreditsOfThisBillingCycle() = runBlocking {
        val recorded = File(System.getProperty("jonaki.testdata"), "balance/tavily-usage.json").readText()
        server.enqueue(MockResponse().setBody(recorded))

        val balance = TavilyBalance("k", OkHttpClient(), server.url("/").toString()).fetch()

        assertEquals(Balance.Credits(used = 3, limit = 1000), balance)
        val request = server.takeRequest()
        assertEquals("/usage", request.path)
        assertEquals("Bearer k", request.getHeader("Authorization"))
    }

    @Test
    fun aBadKeyIsAFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":{"error":"Unauthorized"}}"""))

        val balance = TavilyBalance("k", OkHttpClient(), server.url("/").toString()).fetch()

        assertTrue(balance is Balance.Failed)
    }
}
