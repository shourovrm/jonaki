package app.jonaki.providers.openaicompatible

import app.jonaki.core.balanceapi.Balance
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BalanceTest {
    private fun recorded(name: String) = File(System.getProperty("jonaki.testdata"), "balance/$name").readText()

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

    private fun openRouter() = OpenRouterBalance("k", OkHttpClient(), server.url("/api/v1").toString())

    private fun deepSeek() = DeepSeekBalance("k", OkHttpClient(), server.url("/").toString())

    @Test
    fun openRouterRemainingIsCreditsMinusUsageWithTheKeysMonth() = runBlocking {
        server.enqueue(MockResponse().setBody(recorded("openrouter-credits.json")))
        server.enqueue(MockResponse().setBody(recorded("openrouter-key.json")))

        val balance = openRouter().fetch() as Balance.Money

        // 24 credits minus 11.126219445 used.
        assertEquals(12.873780555, balance.amount, 1e-9)
        assertEquals("USD", balance.currency)
        assertEquals(0.077305828, balance.spentThisMonth!!, 1e-9)
        assertEquals("/api/v1/credits", server.takeRequest().path)
        assertEquals("/api/v1/key", server.takeRequest().path)
    }

    @Test
    fun openRouterBalanceStaysWhenTheMonthCannotBeRead() = runBlocking {
        server.enqueue(MockResponse().setBody(recorded("openrouter-credits.json")))
        server.enqueue(MockResponse().setResponseCode(500))

        val balance = openRouter().fetch() as Balance.Money

        assertEquals(12.873780555, balance.amount, 1e-9)
        assertNull(balance.spentThisMonth)
    }

    @Test
    fun openRouterFallsBackToTheKeyLimitWhenCreditsFail() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"message":"Forbidden"}}"""))
        server.enqueue(MockResponse().setBody(recorded("openrouter-key.json")))

        val balance = openRouter().fetch() as Balance.Money

        assertEquals(19.927821514, balance.amount, 1e-9)
        assertEquals(0.077305828, balance.spentThisMonth!!, 1e-9)
        server.takeRequest()
        assertEquals("/api/v1/key", server.takeRequest().path)
    }

    @Test
    fun openRouterKeyWithoutLimitGivesUnavailable() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        server.enqueue(MockResponse().setBody("""{"data":{"limit":null,"limit_remaining":null,"usage":1.5}}"""))

        assertEquals(Balance.Unavailable, openRouter().fetch())
    }

    @Test
    fun openRouterFailureSaysWhatFailed() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))

        val balance = openRouter().fetch()

        assertTrue(balance is Balance.Failed && balance.message.contains("401"))
    }

    @Test
    fun deepSeekPrefersItsDollarBalance() = runBlocking {
        server.enqueue(MockResponse().setBody(recorded("deepseek-balance-two-currencies.json")))

        val balance = deepSeek().fetch() as Balance.Money

        assertEquals(2.50, balance.amount, 1e-9)
        assertEquals("USD", balance.currency)
    }

    @Test
    fun deepSeekBalanceUsesItsFirstCurrency() = runBlocking {
        server.enqueue(MockResponse().setBody(recorded("deepseek-balance-documented.json")))

        val balance = deepSeek().fetch() as Balance.Money

        assertEquals(4.73, balance.amount, 1e-9)
        assertEquals("USD", balance.currency)
        assertEquals("/user/balance", server.takeRequest().path)
    }
}
