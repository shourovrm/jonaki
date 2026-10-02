package app.jonaki.search.exa

import app.jonaki.core.searchapi.Freshness
import app.jonaki.core.searchapi.SearchOutcome
import app.jonaki.core.searchapi.SearchQuery
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExaSearchBackendTest {
    private lateinit var server: MockWebServer
    private lateinit var backend: ExaSearchBackend

    // Shape from Exa's documentation; not a recording (no Exa key in spike S-4).
    private val documentedResponse = """
        {"requestId":"r1","results":[
          {"title":"Do schools kill creativity?","url":"https://www.ted.com/talks/ken_robinson",
           "publishedDate":"2006-06-27T00:00:00.000Z","text":"Full transcript text",
           "highlights":["Creativity is as important as literacy.","We are educating people out of their creativity."]}
        ]}
    """.trimIndent()

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
        val fixedClock = Clock.fixed(Instant.parse("2026-10-02T00:00:00Z"), ZoneOffset.UTC)
        backend = ExaSearchBackend("test-key", OkHttpClient(), server.url("/").toString(), fixedClock)
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun joinsHighlightsIntoTheSnippetAndSendsFilters() = runBlocking {
        server.enqueue(MockResponse().setBody(documentedResponse))

        val outcome = backend.search(
            SearchQuery("schools creativity", site = "ted.com", freshness = Freshness.WEEK, includeContent = true),
        ) as SearchOutcome.Success

        val result = outcome.results.single()
        assertEquals("Creativity is as important as literacy. … We are educating people out of their creativity.", result.snippet)
        assertEquals("Full transcript text", result.content)
        val recorded = server.takeRequest()
        assertEquals("test-key", recorded.getHeader("x-api-key"))
        val body = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals("ted.com", body["includeDomains"]!!.jsonArray[0].jsonPrimitive.content)
        assertEquals("2026-09-25T00:00:00Z", body["startPublishedDate"]!!.jsonPrimitive.content)
        assertTrue(body["contents"]!!.jsonObject.containsKey("text"))
    }

    @Test
    fun exhaustedCreditsAreAQuotaError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(402).setBody("""{"error":"Out of credits"}"""))

        val outcome = backend.search(SearchQuery("x")) as SearchOutcome.QuotaExceeded

        assertEquals("Exa HTTP 402: Out of credits", outcome.message)
    }

    @Test
    fun badKeyIsAFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"Invalid API key"}"""))

        assertTrue(backend.search(SearchQuery("x")) is SearchOutcome.Failed)
    }
}
