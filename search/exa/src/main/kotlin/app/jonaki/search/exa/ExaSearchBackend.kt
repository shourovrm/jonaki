package app.jonaki.search.exa

import app.jonaki.core.searchapi.Freshness
import app.jonaki.core.searchapi.SearchBackend
import app.jonaki.core.searchapi.SearchOutcome
import app.jonaki.core.searchapi.SearchQuery
import app.jonaki.core.searchapi.SearchResult
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Exa search API, the third backend (D-011). No key was available during
 * spike S-4, so the response shape follows Exa's documentation and has not
 * been checked against the live service yet.
 */
class ExaSearchBackend(
    private val apiKey: String,
    private val httpClient: OkHttpClient,
    private val baseUrl: String = "https://api.exa.ai",
    private val clock: Clock = Clock.systemUTC(),
) : SearchBackend {
    override val name: String = "Exa"

    override suspend fun search(query: SearchQuery): SearchOutcome = withContext(Dispatchers.IO) {
        val call = httpClient.newCall(
            Request.Builder()
                .url(baseUrl.trimEnd('/') + "/search")
                .header("x-api-key", apiKey)
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
                        SearchOutcome.QuotaExceeded("Exa HTTP ${response.code}: ${errorMessage(bodyText)}")
                    else -> SearchOutcome.Failed("Exa HTTP ${response.code}: ${errorMessage(bodyText)}")
                }
            }
        } catch (networkError: IOException) {
            SearchOutcome.Failed("Could not reach Exa: ${networkError.message}")
        } finally {
            cancelHandle?.dispose()
        }
    }

    private fun requestBody(query: SearchQuery): JsonObject = buildJsonObject {
        put("query", query.text)
        put("numResults", query.count)
        put("type", "auto")
        if (query.site != null) putJsonArray("includeDomains") { add(query.site) }
        query.freshness?.let { put("startPublishedDate", startDate(it).toString()) }
        putJsonObject("contents") {
            putJsonObject("highlights") { put("numSentences", 2) }
            if (query.includeContent) putJsonObject("text") { put("maxCharacters", MAX_CONTENT_CHARACTERS) }
        }
    }

    private fun startDate(freshness: Freshness): Instant {
        val age = when (freshness) {
            Freshness.DAY -> Duration.ofDays(1)
            Freshness.WEEK -> Duration.ofDays(7)
            Freshness.MONTH -> Duration.ofDays(31)
            Freshness.YEAR -> Duration.ofDays(365)
        }
        return clock.instant().minus(age)
    }

    private fun resultsFrom(bodyText: String, includeContent: Boolean): List<SearchResult> {
        val results = Json.parseToJsonElement(bodyText).jsonObject["results"]?.jsonArray.orEmpty()
        return results.map { element ->
            val result = element.jsonObject
            val highlights = (result["highlights"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            SearchResult(
                title = result.text("title").orEmpty(),
                url = result.text("url").orEmpty(),
                snippet = highlights.joinToString(" … "),
                content = if (includeContent) result.text("text") else null,
                publishedDate = result.text("publishedDate"),
            )
        }
    }

    private fun errorMessage(bodyText: String): String {
        val parsed = runCatching { Json.parseToJsonElement(bodyText).jsonObject }.getOrNull()
        return parsed?.text("error") ?: bodyText.take(300).ifBlank { "no details" }
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private companion object {
        const val MAX_CONTENT_CHARACTERS = 5_000

        /** 402 when credits run out, 429 rate limit. */
        val QUOTA_STATUS_CODES = setOf(402, 429)
    }
}
