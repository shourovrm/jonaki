package app.jonaki.search.ollama

import app.jonaki.core.searchapi.SearchOutcome
import app.jonaki.core.searchapi.SearchQuery
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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

class OllamaSearchBackendTest {
    private lateinit var server: MockWebServer
    private lateinit var backend: OllamaSearchBackend

    private fun recording(name: String): String =
        File(System.getProperty("jonaki.testdata"), "search/$name").readText()

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
        backend = OllamaSearchBackend("test-key", OkHttpClient(), server.url("/").toString())
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun cutsShortSnippetsFromTheLongPageText() = runBlocking {
        server.enqueue(MockResponse().setBody(recording("ollama-web-search.json")))

        val outcome = backend.search(SearchQuery("Dhaka MRT line 1")) as SearchOutcome.Success

        assertEquals(5, outcome.results.size)
        assertTrue(outcome.results.all { it.snippet.length <= 302 })
        assertNull(outcome.results[0].content)
        assertEquals("https://www.bssnews.net/news/273294", outcome.results[0].url)
    }

    @Test
    fun keepsFullTextWhenAskedAndTurnsSiteIntoAQueryOperator() = runBlocking {
        server.enqueue(MockResponse().setBody(recording("ollama-web-search.json")))

        val outcome = backend.search(SearchQuery("talk", site = "youtube.com", count = 25, includeContent = true)) as SearchOutcome.Success

        assertTrue(outcome.results[0].content!!.length > 3_000)
        val recorded = server.takeRequest()
        assertEquals("/api/web_search", recorded.path)
        val body = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals("talk site:youtube.com", body["query"]!!.jsonPrimitive.content)
        assertEquals(10, body["max_results"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun badKeyIsAFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody(recording("ollama-error-401-bad-key.json")))

        val outcome = backend.search(SearchQuery("x")) as SearchOutcome.Failed

        assertEquals("Ollama HTTP 401: Unauthorized", outcome.message)
    }

    @Test
    fun rateLimitIsAQuotaError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"too many requests"}"""))

        assertTrue(backend.search(SearchQuery("x")) is SearchOutcome.QuotaExceeded)
    }
}
