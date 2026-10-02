package app.jonaki.ui

import app.jonaki.core.balanceapi.Balance
import java.text.NumberFormat
import java.util.Locale

/** Balance lines on the settings cards (D-031, D-032); null shows nothing. */
object BalanceText {
    /** The sentence shapes, from string resources so the copy review sees them. */
    class Words(
        val moneyLeft: (amount: String) -> String,
        val creditsOfLimit: (used: String, limit: String) -> String,
        val creditsUsed: (used: String) -> String,
        val leftAndMonth: (left: String, month: String) -> String,
        val monthOnly: (month: String) -> String,
    )

    /** The line on a closed chat service card. */
    data class CardLine(
        val text: String,
        /** Under one dollar left: the card draws the amount in the error colour. */
        val isLow: Boolean,
        /** The month comes from Jonaki's own call records, which miss other apps' spending. */
        val monthCountedByApp: Boolean,
    )

    private const val LOW_BALANCE_USD = 1.0

    /** Balances from 100 dollars up drop the cents, so the card line stays on one line. */
    private const val WHOLE_DOLLARS_FROM = 100.0

    private const val SMALLEST_SHOWN_SPEND = 0.01

    private val wholeNumbers: NumberFormat = NumberFormat.getIntegerInstance(Locale.ENGLISH)

    fun of(balance: Balance?, words: Words): String? = when (balance) {
        is Balance.Money -> words.moneyLeft(money(balance))
        is Balance.Credits -> credits(balance, words)
        // A failed lookup is not worth a line on the card; the key still works.
        is Balance.Failed, Balance.Unavailable, null -> null
    }

    /**
     * What is left and this month's spend. The service's own monthly figure
     * wins over [appCountedMonthUsd], which only counts calls made in Jonaki.
     */
    fun cardLine(balance: Balance?, appCountedMonthUsd: Double?, words: Words): CardLine? {
        val appMonth = appCountedMonthUsd?.takeIf { spent -> spent > 0.0 }
        if (balance !is Balance.Money) {
            if (appMonth == null) {
                return null
            }
            return CardLine(words.monthOnly(spend(appMonth)), isLow = false, monthCountedByApp = true)
        }
        val left = money(balance)
        val isLow = balance.amount < LOW_BALANCE_USD
        val serviceMonth = balance.spentThisMonth
        if (serviceMonth != null) {
            return CardLine(words.leftAndMonth(left, spend(serviceMonth)), isLow, monthCountedByApp = false)
        }
        if (appMonth != null) {
            return CardLine(words.leftAndMonth(left, spend(appMonth)), isLow, monthCountedByApp = true)
        }
        return CardLine(words.moneyLeft(left), isLow, monthCountedByApp = false)
    }

    private fun money(balance: Balance.Money): String = when (balance.currency.uppercase()) {
        "USD" -> dollarsLeft(balance.amount)
        "CNY" -> "¥" + twoDecimals(balance.amount)
        else -> twoDecimals(balance.amount) + " " + balance.currency
    }

    private fun dollarsLeft(amount: Double): String {
        if (amount >= WHOLE_DOLLARS_FROM) {
            // Rounded down, so the card never promises a cent that is not there.
            return "$" + wholeNumbers.format(Math.floor(amount).toLong())
        }
        return "$" + twoDecimals(amount)
    }

    /** A spend too small for two decimals still shows that something was spent. */
    private fun spend(amountUsd: Double): String {
        if (amountUsd > 0.0 && amountUsd < SMALLEST_SHOWN_SPEND) {
            return "<$" + twoDecimals(SMALLEST_SHOWN_SPEND)
        }
        return "$" + twoDecimals(amountUsd)
    }

    private fun twoDecimals(amount: Double): String = String.format(Locale.ENGLISH, "%,.2f", amount)

    private fun credits(balance: Balance.Credits, words: Words): String {
        val used = wholeNumbers.format(balance.used)
        val limit = balance.limit ?: return words.creditsUsed(used)
        return words.creditsOfLimit(used, wholeNumbers.format(limit))
    }
}
