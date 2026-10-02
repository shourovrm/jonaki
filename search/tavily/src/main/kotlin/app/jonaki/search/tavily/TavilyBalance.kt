package app.jonaki.search.tavily

import app.jonaki.core.balanceapi.Balance
import app.jonaki.core.balanceapi.BalanceSource
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** Tavily credits used and allowed in this billing cycle (GET /usage, D-031). */
class TavilyBalance(
    private val apiKey: String,
    private val httpClient: OkHttpClient,
    private val baseUrl: String = "https://api.tavily.com",
) : BalanceSource {
    override suspend fun fetch(): Balance = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/usage")
            .header("Authorization", "Bearer $apiKey")
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Balance.Failed("Tavily answered HTTP ${response.code}")
                }
                creditsFrom(response.body?.string().orEmpty())
            }
        } catch (networkError: IOException) {
            Balance.Failed("Could not reach Tavily: ${networkError.message}")
        }
    }

    private fun creditsFrom(bodyText: String): Balance {
        val account = runCatching { Json.parseToJsonElement(bodyText) as? JsonObject }.getOrNull()?.get("account") as? JsonObject
            ?: return Balance.Failed("Tavily's usage answer had no account")
        val used = (account["plan_usage"] as? JsonPrimitive)?.longOrNull ?: return Balance.Unavailable
        val limit = (account["plan_limit"] as? JsonPrimitive)?.longOrNull
        return Balance.Credits(used = used, limit = limit)
    }
}
