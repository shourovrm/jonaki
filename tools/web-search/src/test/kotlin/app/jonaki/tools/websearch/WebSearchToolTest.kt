package app.jonaki.tools.websearch

import app.jonaki.core.searchapi.Freshness
import app.jonaki.core.searchapi.SearchBackend
import app.jonaki.core.searchapi.SearchOutcome
import app.jonaki.core.searchapi.SearchQuery
import app.jonaki.core.searchapi.SearchResult
import app.jonaki.core.toolapi.ToolContext
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchToolTest {
    private class FakeBackend(override val name: String, private val outcome: SearchOutcome) : SearchBackend {
        val queries = mutableListOf<SearchQuery>()

        override suspend fun search(query: SearchQuery): SearchOutcome {
            queries += query
            return outcome
        }
    }

    private val threadFolder: File = Files.createTempDirectory("thread").toFile()
    private val context = ToolContext(threadFolder, OkHttpClient())

    private fun arguments(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private val twoResults = SearchOutcome.Success(
        listOf(
            SearchResult("MRT Line 1", "https://example.com/mrt", "Construction started in 2023."),
            SearchResult("Metro news", "https://example.com/news", "Line 5 delayed.", publishedDate = "2026-09-30"),
        ),
    )

    @Test
    fun formatsResultsFromTheFirstBackend() = runBlocking {
        val tavily = FakeBackend("Tavily", twoResults)
        val tool = WebSearchTool(listOf(tavily))

        val output = tool.run(arguments("""{"query":"Dhaka MRT line 1"}"""), context)

        assertFalse(output.isError)
        assertTrue(output.text, output.text.startsWith("2 results from Tavily for \"Dhaka MRT line 1\""))
        assertTrue(output.text.contains("1. MRT Line 1\n   https://example.com/mrt\n   Construction started in 2023."))
        assertTrue(output.text.contains("Published 2026-09-30"))
    }

    @Test
    fun passesOptionsToTheBackend() = runBlocking {
        val tavily = FakeBackend("Tavily", twoResults)
        val tool = WebSearchTool(listOf(tavily))

        tool.run(arguments("""{"query":"rain","site":"youtube.com","freshness":"week","count":3,"include_content":true}"""), context)

        assertEquals(SearchQuery("rain", "youtube.com", Freshness.WEEK, 3, includeContent = true), tavily.queries.single())
    }

    @Test
    fun movesToTheNextBackendOnQuotaErrorsAndSaysSo() = runBlocking {
        val tavily = FakeBackend("Tavily", SearchOutcome.QuotaExceeded("Tavily HTTP 432: plan limit"))
        val ollama = FakeBackend("Ollama", twoResults)
        val tool = WebSearchTool(listOf(tavily, ollama))

        val output = tool.run(arguments("""{"query":"rain"}"""), context)

        assertFalse(output.isError)
        assertTrue(output.text.startsWith("2 results from Ollama"))
        assertTrue(output.text.contains("Tavily skipped: Tavily HTTP 432: plan limit"))
        assertEquals(1, ollama.queries.size)
    }

    @Test
    fun failsLoudlyWhenEveryBackendFails() = runBlocking {
        val tool = WebSearchTool(
            listOf(
                FakeBackend("Tavily", SearchOutcome.QuotaExceeded("quota")),
                FakeBackend("Ollama", SearchOutcome.Failed("Ollama HTTP 401: Unauthorized")),
            ),
        )

        val output = tool.run(arguments("""{"query":"rain"}"""), context)

        assertTrue(output.isError)
        assertTrue(output.text.contains("Tavily: quota"))
        assertTrue(output.text.contains("Ollama: Ollama HTTP 401: Unauthorized"))
    }

    @Test
    fun noBackendConfiguredIsAnError() = runBlocking {
        val output = WebSearchTool(emptyList()).run(arguments("""{"query":"rain"}"""), context)

        assertTrue(output.isError)
        assertTrue(output.text.contains("no search service"))
    }

    @Test
    fun capsEachPageAtFiveThousandCharacters() = runBlocking {
        val longPage = "words".repeat(3_000)
        val backend = FakeBackend("Tavily", SearchOutcome.Success(listOf(SearchResult("Long", "https://l", "s", content = longPage))))

        val output = WebSearchTool(listOf(backend)).run(arguments("""{"query":"long","include_content":true}"""), context)

        assertTrue(output.text.contains("[page text cut at 5000 of 15000 characters; use web_fetch for the whole page]"))
    }

    @Test
    fun missingQueryIsAnError() = runBlocking {
        val output = WebSearchTool(listOf(FakeBackend("Tavily", twoResults))).run(arguments("{}"), context)

        assertTrue(output.isError)
    }

    @Test
    fun unknownFreshnessIsAnError() = runBlocking {
        val output = WebSearchTool(listOf(FakeBackend("Tavily", twoResults))).run(arguments("""{"query":"x","freshness":"hour"}"""), context)

        assertTrue(output.isError)
        assertTrue(output.text.contains("day, week, month or year"))
    }

    @Test
    fun guidelinesKeepPersonalDetailsOutOfQueries() {
        val guidelines = WebSearchTool(emptyList()).guidelines.joinToString(" ")

        assertTrue(guidelines.contains("never names, personal details or file contents"))
    }
}
