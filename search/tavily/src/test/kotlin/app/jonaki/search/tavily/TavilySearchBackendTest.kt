package app.jonaki.search.tavily

import app.jonaki.core.searchapi.Freshness
import app.jonaki.core.searchapi.SearchOutcome
import app.jonaki.core.searchapi.SearchQuery
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TavilySearchBackendTest {
    private lateinit var server: MockWebServer
    private lateinit var backend: TavilySearchBackend

    private fun recording(name: String): String =
        File(System.getProperty("jonaki.testdata"), "search/$name").readText()

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
        backend = TavilySearchBackend("test-key", OkHttpClient(), server.url("/").toString())
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun parsesTheRecordedResults() = runBlocking {
        server.enqueue(MockResponse().setBody(recording("tavily-search-200.json")))

        val outcome = backend.search(SearchQuery("Dhaka metro rail MRT line 1 construction progress")) as SearchOutcome.Success

        assertEquals(5, outcome.results.size)
        val first = outcome.results[0]
        assertEquals("https://en.wikipedia.org/wiki/MRT_Line_1_(Dhaka_Metro_Rail)", first.url)
        assertTrue(first.snippet.startsWith("Its construction started"))
        assertNull(first.content)
    }

    @Test
    fun sendsSiteFreshnessCountAndContentOptions() = runBlocking {
        server.enqueue(MockResponse().setBody(recording("tavily-search-youtube-200.json")))

        val outcome = backend.search(
            SearchQuery("Ken Robinson schools creativity", site = "youtube.com", freshness = Freshness.YEAR, count = 3, includeContent = true),
        ) as SearchOutcome.Success

        assertTrue(outcome.results.any { it.url == "https://www.youtube.com/watch?v=iG9CE55wbtY" })
        val recorded = server.takeRequest()
        assertEquals("/search", recorded.path)
        assertEquals("Bearer test-key", recorded.getHeader("Authorization"))
        val body = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals("youtube.com", body["include_domains"]!!.jsonArray[0].jsonPrimitive.content)
        assertEquals("year", body["time_range"]!!.jsonPrimitive.content)
        assertEquals(3, body["max_results"]!!.jsonPrimitive.content.toInt())
        assertEquals("markdown", body["include_raw_content"]!!.jsonPrimitive.content)
    }

    @Test
    fun badKeyIsAFailureNotAQuotaError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody(recording("tavily-error-401-bad-key.json")))

        val outcome = backend.search(SearchQuery("x")) as SearchOutcome.Failed

        assertEquals("Tavily HTTP 401: Unauthorized: missing or invalid API key.", outcome.message)
    }

    @Test
    fun planLimitIsAQuotaError() = runBlocking {
        // Shape from Tavily's documentation; a fresh account cannot trigger it (testdata/README.md).
        server.enqueue(MockResponse().setResponseCode(432).setBody("""{"detail":{"error":"Plan usage limit exceeded."}}"""))

        val outcome = backend.search(SearchQuery("x"))

        assertTrue(outcome is SearchOutcome.QuotaExceeded)
    }

    @Test
    fun rateLimitIsAQuotaError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"detail":{"error":"Too many requests."}}"""))

        assertTrue(backend.search(SearchQuery("x")) is SearchOutcome.QuotaExceeded)
    }
}
