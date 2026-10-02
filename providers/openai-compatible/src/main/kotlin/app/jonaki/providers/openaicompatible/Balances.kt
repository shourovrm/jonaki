package app.jonaki.providers.openaicompatible

import app.jonaki.core.balanceapi.Balance
import app.jonaki.core.balanceapi.BalanceSource
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * OpenRouter credits left in USD (D-031): purchased credits minus usage from
 * GET /credits; when the key may not read that, the key's own spending limit
 * left from GET /key.
 */
class OpenRouterBalance(
    private val apiKey: String,
    private val httpClient: OkHttpClient,
    private val baseUrl: String = ProviderPresets.openRouter.baseUrl,
) : BalanceSource {
    override suspend fun fetch(): Balance {
        val credits = getJson(httpClient, apiKey, baseUrl, "/credits")
        val creditsData = (credits.json?.get("data") as? JsonObject)
        val total = creditsData?.number("total_credits")
        val used = creditsData?.number("total_usage")
        if (total != null && used != null) {
            return Balance.Money(total - used, USD)
        }
        val key = getJson(httpClient, apiKey, baseUrl, "/key")
        val keyData = key.json?.get("data") as? JsonObject
            ?: return Balance.Failed("OpenRouter answered ${credits.problem ?: key.problem}")
        val remaining = keyData.number("limit_remaining") ?: return Balance.Unavailable
        return Balance.Money(remaining, USD)
    }

    private companion object {
        const val USD = "USD"
    }
}

/** DeepSeek's account balance in its own currency (GET /user/balance, D-031). */
class DeepSeekBalance(
    private val apiKey: String,
    private val httpClient: OkHttpClient,
    private val baseUrl: String = ProviderPresets.deepSeek.baseUrl,
) : BalanceSource {
    override suspend fun fetch(): Balance {
        val answer = getJson(httpClient, apiKey, baseUrl, "/user/balance")
        val root = answer.json ?: return Balance.Failed("DeepSeek answered ${answer.problem}")
        // The first entry is the account's main currency.
        val first = (root["balance_infos"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return Balance.Unavailable
        val currency = (first["currency"] as? JsonPrimitive)?.contentOrNull ?: return Balance.Unavailable
        val amount = first.number("total_balance") ?: return Balance.Unavailable
        return Balance.Money(amount, currency)
    }
}

/** A JSON body, or what went wrong ("HTTP 401", "no connection: …"). */
private class JsonAnswer(val json: JsonObject?, val problem: String?)

private suspend fun getJson(httpClient: OkHttpClient, apiKey: String, baseUrl: String, path: String): JsonAnswer =
    withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("Authorization", "Bearer $apiKey")
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext JsonAnswer(json = null, problem = "HTTP ${response.code}")
                }
                val parsed = runCatching { Json.parseToJsonElement(response.body?.string().orEmpty()) as? JsonObject }.getOrNull()
                JsonAnswer(json = parsed, problem = if (parsed == null) "an unreadable body" else null)
            }
        } catch (networkError: IOException) {
            JsonAnswer(json = null, problem = "no connection: ${networkError.message}")
        }
    }

/** Numbers arrive as JSON numbers (OpenRouter) or as text (DeepSeek's "110.00"). */
private fun JsonObject.number(key: String): Double? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    return primitive.doubleOrNull ?: primitive.contentOrNull?.toDoubleOrNull()
}
