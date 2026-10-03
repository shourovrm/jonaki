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

    /**
     * A download or storage size in decimal megabytes with one decimal, as
     * download sizes are usually given: 13,532,188 bytes is "13.5 MB".
     * Below one megabyte, whole kilobytes; from one gigabyte, gigabytes
     * with one decimal (a model file of 1,214,873,856 bytes is "1.2 GB").
     */
    fun byteSize(bytes: Long): String = when {
        bytes < 1_000 -> "$bytes B"
        bytes < 1_000_000 -> "${(bytes / 1_000.0).roundToInt()} KB"
        bytes < 1_000_000_000 -> String.format(Locale.ENGLISH, "%.1f MB", bytes / 1_000_000.0)
        else -> String.format(Locale.ENGLISH, "%.1f GB", bytes / 1_000_000_000.0)
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

    /**
     * One price per million tokens: two decimals, or three when a cheap cached
     * price would otherwise round to the same cent; a dash when unknown.
     */
    fun price(usdPerMillion: Double?): String {
        if (usdPerMillion == null) {
            return UNKNOWN
        }
        val hasSubCentPart = (usdPerMillion * 1000).roundToInt() % 10 != 0
        val decimals = if (hasSubCentPart && usdPerMillion < 0.1) 3 else 2
        return "$" + String.format(Locale.ENGLISH, "%.${decimals}f", usdPerMillion)
    }

    /** A usage tile's count: exact while it fits a third of a phone's width, short form above. */
    fun tileTokens(tokens: Int): String =
        if (tokens < EXACT_TILE_LIMIT) exactTokens(tokens) else tokenCount(tokens)

    private const val UNKNOWN = "–"
    private const val EXACT_TILE_LIMIT = 100_000

    private fun trimmedOneDecimal(value: Double): String {
        val rounded = (value * 10).roundToInt() / 10.0
        val isWhole = rounded == rounded.toInt().toDouble()
        return if (isWhole) rounded.toInt().toString() else rounded.toString()
    }
}
