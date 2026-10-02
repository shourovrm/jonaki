package app.jonaki.settings

import app.jonaki.core.balanceapi.Balance
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * How many units of each currency one US dollar buys, so every balance shows
 * in USD (D-032). The free open endpoint of ExchangeRate-API needs no key and
 * updates once a day, so one fetch per app run is enough.
 */
class UsdRates(
    private val httpClient: OkHttpClient,
    private val url: String = OPEN_RATES_URL,
) {
    private val fetchLock = Mutex()
    private var fetched: Map<String, Double>? = null

    /** The rates, or null when they could not be fetched; a failure is retried on the next call. */
    suspend fun current(): Map<String, Double>? = fetchLock.withLock {
        fetched ?: fetch()?.also { rates -> fetched = rates }
    }

    private suspend fun fetch(): Map<String, Double>? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext null
                }
                parse(response.body?.string().orEmpty())
            }
        } catch (networkError: IOException) {
            null
        }
    }

    companion object {
        const val OPEN_RATES_URL = "https://open.er-api.com/v6/latest/USD"

        fun parse(body: String): Map<String, Double>? {
            val root = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
            val result = (root["result"] as? JsonPrimitive)?.contentOrNull
            if (result != "success") {
                return null
            }
            val rates = root["rates"] as? JsonObject ?: return null
            return rates.mapNotNull { (currency, value) ->
                val rate = (value as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
                currency to rate
            }.toMap()
        }
    }
}

/**
 * The same balance in USD. Without a rate for its currency the balance
 * fails rather than showing yuan with a dollar sign.
 */
fun Balance.inDollars(usdRates: Map<String, Double>?): Balance {
    if (this !is Balance.Money || currency.equals("USD", ignoreCase = true)) {
        return this
    }
    val unitsPerDollar = usdRates?.get(currency.uppercase())
    if (unitsPerDollar == null || unitsPerDollar <= 0.0) {
        return Balance.Failed("No dollar rate for $currency")
    }
    return Balance.Money(
        amount = amount / unitsPerDollar,
        currency = "USD",
        spentThisMonth = spentThisMonth?.let { spent -> spent / unitsPerDollar },
    )
}
