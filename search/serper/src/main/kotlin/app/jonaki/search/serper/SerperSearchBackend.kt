package app.jonaki.search.serper

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
 * Serper (serper.dev), which returns Google's results (D-177). It gives
 * snippets only, so [SearchQuery.includeContent] has no effect here and the
 * model reads a page with web_fetch. No key was available when this was
 * written: the request and response shapes follow Serper's documentation
 * and have not been checked against the live service yet.
 */
class SerperSearchBackend(
    private val apiKey: String,
    private val httpClient: OkHttpClient,
    private val baseUrl: String = "https://google.serper.dev",
) : SearchBackend {
    override val name: String = "Serper"

    override suspend fun search(query: SearchQuery): SearchOutcome = withContext(Dispatchers.IO) {
        val call = httpClient.newCall(
            Request.Builder()
                .url(baseUrl.trimEnd('/') + "/search")
                .header("X-API-KEY", apiKey)
                .post(requestBody(query).toString().toRequestBody("application/json".toMediaType()))
                .build(),
        )
        val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                val message = "Serper HTTP ${response.code}: ${errorMessage(bodyText)}"
                when {
                    response.isSuccessful -> SearchOutcome.Success(resultsFrom(bodyText))
                    isQuotaError(response.code, bodyText) -> SearchOutcome.QuotaExceeded(message)
                    else -> SearchOutcome.Failed(message)
                }
            }
        } catch (networkError: IOException) {
            SearchOutcome.Failed("Could not reach Serper: ${networkError.message}")
        } finally {
            cancelHandle?.dispose()
        }
    }

    private fun requestBody(query: SearchQuery): JsonObject = buildJsonObject {
        // Serper has no domain field; Google's own site: operator does the job.
        put("q", if (query.site == null) query.text else "${query.text} site:${query.site}")
        put("num", query.count)
        query.freshness?.let { put("tbs", timeFilter(it)) }
    }

    /** Google's "past day / week / month / year" filter. */
    private fun timeFilter(freshness: Freshness): String = when (freshness) {
        Freshness.DAY -> "qdr:d"
        Freshness.WEEK -> "qdr:w"
        Freshness.MONTH -> "qdr:m"
        Freshness.YEAR -> "qdr:y"
    }

    private fun resultsFrom(bodyText: String): List<SearchResult> {
        val results = Json.parseToJsonElement(bodyText).jsonObject["organic"]?.jsonArray.orEmpty()
        return results.map { element ->
            val result = element.jsonObject
            SearchResult(
                title = result.text("title").orEmpty(),
                url = result.text("link").orEmpty(),
                snippet = result.text("snippet").orEmpty(),
                publishedDate = result.text("date"),
            )
        }
    }

    /** Serper answers 429 over the rate limit and 400 with "Not enough credits" when the account is empty. */
    private fun isQuotaError(statusCode: Int, bodyText: String): Boolean =
        statusCode == RATE_LIMIT_STATUS_CODE || errorMessage(bodyText).contains("credits", ignoreCase = true)

    /** Serper wraps errors as {"message": "...", "statusCode": 400}. */
    private fun errorMessage(bodyText: String): String {
        val parsed = runCatching { Json.parseToJsonElement(bodyText).jsonObject }.getOrNull()
        return parsed?.text("message") ?: bodyText.take(300).ifBlank { "no details" }
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private companion object {
        const val RATE_LIMIT_STATUS_CODE = 429
    }
}
