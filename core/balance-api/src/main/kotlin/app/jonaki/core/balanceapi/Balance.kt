package app.jonaki.core.balanceapi

/**
 * What is left on one service account (D-031). Each service with a balance
 * API has its fetcher in its own module; services without one (Gemini,
 * Ollama) simply have no fetcher.
 */
fun interface BalanceSource {
    suspend fun fetch(): Balance
}

sealed interface Balance {
    /**
     * Money left, for example 12.87 USD on OpenRouter or a CNY balance on
     * DeepSeek. [spentThisMonth] is in the same currency and only set when the
     * service itself reports it (OpenRouter's key usage).
     */
    data class Money(val amount: Double, val currency: String, val spentThisMonth: Double? = null) : Balance

    /** Credits used and allowed in the current period, for example Tavily's 3 of 1,000. */
    data class Credits(val used: Long, val limit: Long?) : Balance

    /** The account gives no balance through this key, for example a key without a spending limit. */
    data object Unavailable : Balance

    /** The request failed; [message] says why, for the settings card. */
    data class Failed(val message: String) : Balance
}
