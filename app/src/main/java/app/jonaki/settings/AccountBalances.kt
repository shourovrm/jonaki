package app.jonaki.settings

import app.jonaki.core.balanceapi.Balance
import app.jonaki.core.balanceapi.BalanceSource
import app.jonaki.providers.openaicompatible.DeepSeekBalance
import app.jonaki.providers.openaicompatible.OpenRouterBalance
import app.jonaki.search.tavily.TavilyBalance
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient

/**
 * Balances of the accounts whose keys are saved (D-031), refreshed when
 * Settings opens and shown in USD (D-032). Gemini and Ollama have no balance
 * API, so they never appear.
 */
class AccountBalances(
    private val secrets: SecretStore,
    private val httpClient: OkHttpClient,
    private val usdRates: UsdRates,
) {
    private val latest = MutableStateFlow<Map<SecretName, Balance>>(emptyMap())

    /** The last answer per account; an account without a saved key has no entry. */
    val balances: StateFlow<Map<SecretName, Balance>> = latest.asStateFlow()

    /**
     * Asks every account at once and replaces [balances] with the answers. A
     * failed answer keeps the account's last good value, so a dropped
     * connection does not blank the card.
     */
    suspend fun refreshAll() {
        val sources = sourcesWithKeys()
        val answers = coroutineScope {
            sources.map { (name, source) -> async { name to source.fetch() } }.awaitAll()
        }
        val needsRates = answers.any { (_, balance) -> balance is Balance.Money && balance.currency != "USD" }
        val rates = if (needsRates) usdRates.current() else null
        val previous = latest.value
        latest.value = answers.associate { (name, balance) ->
            val inDollars = balance.inDollars(rates)
            val kept = if (inDollars is Balance.Failed) previous[name] ?: inDollars else inDollars
            name to kept
        }
    }

    private fun sourcesWithKeys(): Map<SecretName, BalanceSource> {
        val sources = mutableMapOf<SecretName, BalanceSource>()
        secrets.read(SecretName.OPENROUTER)?.let { key -> sources[SecretName.OPENROUTER] = OpenRouterBalance(key, httpClient) }
        secrets.read(SecretName.DEEPSEEK)?.let { key -> sources[SecretName.DEEPSEEK] = DeepSeekBalance(key, httpClient) }
        secrets.read(SecretName.TAVILY)?.let { key -> sources[SecretName.TAVILY] = TavilyBalance(key, httpClient) }
        return sources
    }
}
