package app.jonaki.search.tavily

import app.jonaki.core.searchapi.Freshness
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
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Tavily search API, the default backend (D-011). 1,000 free credits a month. */
class TavilySearchBackend(
    private val apiKey: String,
    private val httpClient: OkHttpClient,
    private val baseUrl: String = "https://api.tavily.com",
) : SearchBackend {
    override val name: String = "Tavily"

    override suspend fun search(query: SearchQuery): SearchOutcome = withContext(Dispatchers.IO) {
        val call = httpClient.newCall(
            Request.Builder()
                .url(baseUrl.trimEnd('/') + "/search")
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
                        SearchOutcome.QuotaExceeded("Tavily HTTP ${response.code}: ${errorMessage(bodyText)}")
                    else -> SearchOutcome.Failed("Tavily HTTP ${response.code}: ${errorMessage(bodyText)}")
                }
            }
        } catch (networkError: IOException) {
            SearchOutcome.Failed("Could not reach Tavily: ${networkError.message}")
        } finally {
            cancelHandle?.dispose()
        }
    }

    private fun requestBody(query: SearchQuery): JsonObject = buildJsonObject {
        put("query", query.text)
        put("max_results", query.count)
        put("search_depth", "basic")
        if (query.site != null) putJsonArray("include_domains") { add(query.site) }
        query.freshness?.let { put("time_range", timeRange(it)) }
        if (query.includeContent) put("include_raw_content", "markdown")
    }

    private fun timeRange(freshness: Freshness): String = when (freshness) {
        Freshness.DAY -> "day"
        Freshness.WEEK -> "week"
        Freshness.MONTH -> "month"
        Freshness.YEAR -> "year"
    }

    private fun resultsFrom(bodyText: String, includeContent: Boolean): List<SearchResult> {
        val results = Json.parseToJsonElement(bodyText).jsonObject["results"]?.jsonArray.orEmpty()
        return results.map { element ->
            val result = element.jsonObject
            SearchResult(
                title = result.text("title").orEmpty(),
                url = result.text("url").orEmpty(),
                snippet = result.text("content").orEmpty(),
                content = if (includeContent) result.text("raw_content") else null,
                publishedDate = result.text("published_date"),
            )
        }
    }

    /** Tavily wraps errors as {"detail": {"error": "..."}}. */
    private fun errorMessage(bodyText: String): String {
        val parsed = runCatching { Json.parseToJsonElement(bodyText).jsonObject }.getOrNull()
        val detail = parsed?.get("detail")
        val message = (detail as? JsonObject)?.text("error")
        return message ?: bodyText.take(300).ifBlank { "no details" }
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private companion object {
        /** 429 rate limit, 432 plan limit, 433 pay-as-you-go limit (Tavily documentation). */
        val QUOTA_STATUS_CODES = setOf(429, 432, 433)
    }
}
