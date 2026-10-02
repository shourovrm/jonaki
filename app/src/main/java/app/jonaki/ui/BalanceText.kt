package app.jonaki.ui

import app.jonaki.core.balanceapi.Balance
import java.text.NumberFormat
import java.util.Locale

/** The one line under a key on a settings card (D-031); null shows nothing. */
object BalanceText {
    /** The sentence shapes, from string resources so the copy review sees them. */
    class Words(
        val moneyLeft: (amount: String) -> String,
        val creditsOfLimit: (used: String, limit: String) -> String,
        val creditsUsed: (used: String) -> String,
    )

    private val wholeNumbers: NumberFormat = NumberFormat.getIntegerInstance(Locale.ENGLISH)

    fun of(balance: Balance?, words: Words): String? = when (balance) {
        is Balance.Money -> words.moneyLeft(money(balance))
        is Balance.Credits -> credits(balance, words)
        // A failed lookup is not worth a line on the card; the key still works.
        is Balance.Failed, Balance.Unavailable, null -> null
    }

    private fun money(balance: Balance.Money): String {
        val amount = String.format(Locale.ENGLISH, "%.2f", balance.amount)
        return when (balance.currency.uppercase()) {
            "USD" -> "$$amount"
            "CNY" -> "¥$amount"
            else -> "$amount ${balance.currency}"
        }
    }

    private fun credits(balance: Balance.Credits, words: Words): String {
        val used = wholeNumbers.format(balance.used)
        val limit = balance.limit ?: return words.creditsUsed(used)
        return words.creditsOfLimit(used, wholeNumbers.format(limit))
    }
}
