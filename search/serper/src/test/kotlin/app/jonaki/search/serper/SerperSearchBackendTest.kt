package app.jonaki.search.serper

import app.jonaki.core.searchapi.Freshness
import app.jonaki.core.searchapi.SearchOutcome
import app.jonaki.core.searchapi.SearchQuery
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SerperSearchBackendTest {
    private lateinit var server: MockWebServer
    private lateinit var backend: SerperSearchBackend

    // Shape from Serper's documentation; not a recording (no Serper key when this was written).
    private val documentedResponse = """
        {"searchParameters":{"q":"apple inc","type":"search","engine":"google"},
         "knowledgeGraph":{"title":"Apple","type":"Technology company"},
         "organic":[
           {"title":"Apple","link":"https://www.apple.com/","snippet":"Discover the innovative world of Apple.","position":1},
           {"title":"Apple Inc. - Wikipedia","link":"https://en.wikipedia.org/wiki/Apple_Inc.",
            "snippet":"Apple Inc. is an American multinational technology company.","date":"Sep 12, 2026","position":2}
         ],
         "credits":1}
    """.trimIndent()

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
        backend = SerperSearchBackend("test-key", OkHttpClient(), server.url("/").toString())
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun readsOrganicResultsAndSendsTheKey() = runBlocking {
        server.enqueue(MockResponse().setBody(documentedResponse))

        val outcome = backend.search(SearchQuery("apple inc", count = 2)) as SearchOutcome.Success

        assertEquals(2, outcome.results.size)
        val second = outcome.results[1]
        assertEquals("Apple Inc. - Wikipedia", second.title)
        assertEquals("https://en.wikipedia.org/wiki/Apple_Inc.", second.url)
        assertEquals("Apple Inc. is an American multinational technology company.", second.snippet)
        assertEquals("Sep 12, 2026", second.publishedDate)
        assertNull(outcome.results[0].publishedDate)
        val recorded = server.takeRequest()
        assertEquals("/search", recorded.path)
        assertEquals("test-key", recorded.getHeader("X-API-KEY"))
        val body = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals("apple inc", body["q"]!!.jsonPrimitive.content)
        assertEquals(2, body["num"]!!.jsonPrimitive.content.toInt())
        assertFalse(body.containsKey("tbs"))
    }

    @Test
    fun aSiteBecomesGooglesSiteOperatorAndFreshnessATimeFilter() = runBlocking {
        server.enqueue(MockResponse().setBody(documentedResponse))

        backend.search(SearchQuery("fusion video", site = "youtube.com", freshness = Freshness.WEEK))

        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("fusion video site:youtube.com", body["q"]!!.jsonPrimitive.content)
        assertEquals("qdr:w", body["tbs"]!!.jsonPrimitive.content)
    }

    @Test
    fun aResponseWithoutOrganicResultsIsAnEmptySuccess() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"searchParameters":{"q":"x"},"credits":1}"""))

        val outcome = backend.search(SearchQuery("x")) as SearchOutcome.Success

        assertTrue(outcome.results.isEmpty())
    }

    @Test
    fun exhaustedCreditsAreAQuotaError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"message":"Not enough credits","statusCode":400}"""))

        val outcome = backend.search(SearchQuery("x")) as SearchOutcome.QuotaExceeded

        assertEquals("Serper HTTP 400: Not enough credits", outcome.message)
    }

    @Test
    fun theRateLimitIsAQuotaError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"message":"Too many requests","statusCode":429}"""))

        assertTrue(backend.search(SearchQuery("x")) is SearchOutcome.QuotaExceeded)
    }

    @Test
    fun badKeyIsAFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"message":"Unauthorized.","statusCode":403}"""))

        val outcome = backend.search(SearchQuery("x")) as SearchOutcome.Failed

        assertEquals("Serper HTTP 403: Unauthorized.", outcome.message)
    }
}
