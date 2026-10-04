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
 * left from GET /key. The key's spend this calendar month also comes from
 * GET /key (D-032).
 */
class OpenRouterBalance(
    private val apiKey: String,
    private val httpClient: OkHttpClient,
    private val baseUrl: String = ProviderPresets.openRouter.baseUrl,
) : BalanceSource {
    override suspend fun fetch(): Balance {
        val credits = getJson(httpClient, apiKey, baseUrl, "/credits")
        val key = getJson(httpClient, apiKey, baseUrl, "/key")
        val creditsData = credits.json?.get("data") as? JsonObject
        val keyData = key.json?.get("data") as? JsonObject
        val spentThisMonth = keyData?.number("usage_monthly")
        val total = creditsData?.number("total_credits")
        val used = creditsData?.number("total_usage")
        if (total != null && used != null) {
            return Balance.Money(total - used, USD, spentThisMonth)
        }
        if (keyData == null) {
            return Balance.Failed("OpenRouter answered ${credits.problem ?: key.problem}")
        }
        val remaining = keyData.number("limit_remaining") ?: return Balance.Unavailable
        return Balance.Money(remaining, USD, spentThisMonth)
    }

    private companion object {
        const val USD = "USD"
    }
}

/**
 * DeepSeek's account balance (GET /user/balance, D-031): the USD entry when
 * the account has one, else its first currency, which the app converts.
 */
class DeepSeekBalance(
    private val apiKey: String,
    private val httpClient: OkHttpClient,
    private val baseUrl: String = ProviderPresets.deepSeek.baseUrl,
) : BalanceSource {
    override suspend fun fetch(): Balance {
        val answer = getJson(httpClient, apiKey, baseUrl, "/user/balance")
        val root = answer.json ?: return Balance.Failed("DeepSeek answered ${answer.problem}")
        val entries = (root["balance_infos"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
        // A dollar entry needs no conversion; otherwise the first entry is the account's main currency.
        val chosen = entries.firstOrNull { entry -> entry.text("currency") == "USD" }
            ?: entries.firstOrNull()
            ?: return Balance.Unavailable
        val currency = chosen.text("currency") ?: return Balance.Unavailable
        val amount = chosen.number("total_balance") ?: return Balance.Unavailable
        return Balance.Money(amount, currency)
    }
}

/**
 * MiniMax (international platform). A pay-as-you-go key (prefix "sk-api-")
 * reads GET /account/query_balance, a USD amount; any other key is a Token
 * Plan key and reads GET /v1/token_plan/remains, the 5-hour window of the
 * text model. The field names come from MiniMax's own command line tool
 * (MiniMax-AI/cli), not from an API reference, so every unexpected answer
 * gives no balance rather than a guessed one. MiniMax answers HTTP 200 even
 * for a refused key and reports the error in base_resp.status_code.
 */
class MiniMaxBalance(
    private val apiKey: String,
    private val httpClient: OkHttpClient,
    private val baseUrl: String = "https://api.minimax.io",
) : BalanceSource {
    override suspend fun fetch(): Balance {
        val isPayAsYouGo = apiKey.startsWith(PAY_AS_YOU_GO_KEY_PREFIX)
        val path = if (isPayAsYouGo) "/account/query_balance" else "/v1/token_plan/remains"
        val answer = getJson(httpClient, apiKey, baseUrl, path)
        val root = answer.json ?: return Balance.Failed("MiniMax answered ${answer.problem}")
        val statusCode = (root["base_resp"] as? JsonObject)?.number("status_code")
        if (statusCode != 0.0) {
            return Balance.Failed("MiniMax answered status $statusCode")
        }
        if (isPayAsYouGo) {
            val amount = root.number("available_amount") ?: return Balance.Unavailable
            return Balance.Money(amount, "USD")
        }
        return tokenPlanCredits(root)
    }

    private fun tokenPlanCredits(root: JsonObject): Balance {
        val rows = (root["model_remains"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
        val textRow = rows.firstOrNull { row -> row.text("model_name")?.startsWith("MiniMax-M") == true }
            ?: return Balance.Unavailable
        val total = textRow.number("current_interval_total_count") ?: return Balance.Unavailable
        val reported = textRow.number("current_interval_usage_count") ?: return Balance.Unavailable
        val remainingPercent = textRow.number("current_interval_remaining_percent") ?: return Balance.Unavailable
        if (total <= 0.0 || reported < 0.0 || reported > total) {
            return Balance.Unavailable
        }
        // Older answers put the remaining count in *_usage_count and newer ones
        // the used count. Only the percentage tells which; if it matches
        // neither reading, no number is shown.
        val percentIfRemaining = reported / total * 100
        val percentIfUsed = (total - reported) / total * 100
        val used = when {
            Math.abs(percentIfRemaining - remainingPercent) <= PERCENT_TOLERANCE -> total - reported
            Math.abs(percentIfUsed - remainingPercent) <= PERCENT_TOLERANCE -> reported
            else -> return Balance.Unavailable
        }
        return Balance.Credits(used.toLong(), total.toLong())
    }

    private companion object {
        const val PAY_AS_YOU_GO_KEY_PREFIX = "sk-api-"
        const val PERCENT_TOLERANCE = 1.0
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

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
