package app.jonaki.search.ollama

import app.jonaki.core.searchapi.SearchBackend
import app.jonaki.core.searchapi.SearchOutcome
import app.jonaki.core.searchapi.SearchQuery
import app.jonaki.core.searchapi.SearchResult
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Ollama's web search API, the second backend (D-011). It always returns
 * page text (3,500 to 8,800 characters per result in spike S-4), so the
 * snippet is cut from that text and the full text is kept only on request.
 * It has no domain or date filter: a site restriction becomes a `site:`
 * operator in the query, and freshness is ignored.
 */
class OllamaSearchBackend(
    private val apiKey: String,
    private val httpClient: OkHttpClient,
    private val baseUrl: String = "https://ollama.com",
) : SearchBackend {
    override val name: String = "Ollama"

    override suspend fun search(query: SearchQuery): SearchOutcome = withContext(Dispatchers.IO) {
        val call = httpClient.newCall(
            Request.Builder()
                .url(baseUrl.trimEnd('/') + "/api/web_search")
                .header("Authorization", "Bearer $apiKey")
                .post(requestBody(query).toString().toRequestBody("application/json".toMediaType()))
                .build(),
        )
        val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> SearchOutcome.Success(resultsFrom(bodyText, query.includeContent))
                    response.code in QUOTA_STATUS_CODES ->
                        SearchOutcome.QuotaExceeded("Ollama HTTP ${response.code}: ${errorMessage(bodyText)}")
                    else -> SearchOutcome.Failed("Ollama HTTP ${response.code}: ${errorMessage(bodyText)}")
                }
            }
        } catch (networkError: IOException) {
            SearchOutcome.Failed("Could not reach Ollama: ${networkError.message}")
        } finally {
            cancelHandle?.dispose()
        }
    }

    private fun requestBody(query: SearchQuery): JsonObject = buildJsonObject {
        val text = if (query.site == null) query.text else "${query.text} site:${query.site}"
        put("query", text)
        // The API accepts at most 10 results.
        put("max_results", query.count.coerceIn(1, 10))
    }

    private fun resultsFrom(bodyText: String, includeContent: Boolean): List<SearchResult> {
        val results = Json.parseToJsonElement(bodyText).jsonObject["results"]?.jsonArray.orEmpty()
        return results.map { element ->
            val result = element.jsonObject
            val pageText = result.text("content").orEmpty()
            SearchResult(
                title = result.text("title").orEmpty(),
                url = result.text("url").orEmpty(),
                snippet = snippetFrom(pageText),
                content = if (includeContent) pageText else null,
            )
        }
    }

    private fun snippetFrom(pageText: String): String {
        val collapsed = pageText.replace(Regex("\\s+"), " ").trim()
        if (collapsed.length <= SNIPPET_LENGTH) return collapsed
        return collapsed.take(SNIPPET_LENGTH).substringBeforeLast(' ') + " …"
    }

    private fun errorMessage(bodyText: String): String {
        val parsed = runCatching { Json.parseToJsonElement(bodyText).jsonObject }.getOrNull()
        return parsed?.text("error") ?: bodyText.take(300).ifBlank { "no details" }
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private companion object {
        const val SNIPPET_LENGTH = 300

        /** 429 rate limit; 402 when an account's paid usage runs out. */
        val QUOTA_STATUS_CODES = setOf(402, 429)
    }
}
