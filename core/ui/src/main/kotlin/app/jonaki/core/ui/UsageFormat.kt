package app.jonaki.core.ui

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

/** Number formats for cost and token counts (D-027), shared by the chat, thread list and settings. */
object UsageFormat {
    /**
     * US dollars with as many decimals as the size needs: tiny run costs keep
     * four so they don't all read "$0.00", larger totals drop to two.
     */
    fun cost(usd: Double): String {
        if (usd == 0.0) {
            return "$0"
        }
        val decimals = when {
            usd < 0.01 -> 4
            usd < 1.0 -> 3
            else -> 2
        }
        // A cost smaller than the last shown decimal still reads as spent, not as zero.
        val smallestShown = 10.0.pow(-decimals)
        val shown = if (usd < smallestShown) smallestShown else usd
        return "$" + String.format(Locale.ENGLISH, "%.${decimals}f", shown)
    }

    /** A context window or token total in short form: 950, 8K, 131K, 1M, 1.5M. */
    fun tokenCount(tokens: Int): String = when {
        tokens < 1_000 -> tokens.toString()
        tokens < 1_000_000 -> "${(tokens / 1_000.0).roundToInt()}K"
        else -> trimmedOneDecimal(tokens / 1_000_000.0) + "M"
    }

    /** An exact count with thousands separators, for the usage sheet. */
    fun exactTokens(tokens: Int): String = NumberFormat.getIntegerInstance(Locale.ENGLISH).format(tokens)

    /** Share of the context window in use, 0 to 100; any use shows at least 1. */
    fun percentUsed(used: Int, window: Int): Int {
        if (window <= 0 || used <= 0) {
            return 0
        }
        val percent = (used * 100.0 / window).roundToInt()
        return percent.coerceIn(1, 100)
    }

    /** Model prices per million tokens, input then output. */
    fun pricePerMillion(inputUsd: Double, outputUsd: Double): String =
        String.format(Locale.ENGLISH, "$%.2f / $%.2f", inputUsd, outputUsd)

    private fun trimmedOneDecimal(value: Double): String {
        val rounded = (value * 10).roundToInt() / 10.0
        val isWhole = rounded == rounded.toInt().toDouble()
        return if (isWhole) rounded.toInt().toString() else rounded.toString()
    }
}
