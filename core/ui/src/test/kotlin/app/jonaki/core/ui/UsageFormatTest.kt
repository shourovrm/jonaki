package app.jonaki.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageFormatTest {
    @Test
    fun costBelowOneCentShowsFourDecimals() {
        assertEquals("$0.0041", UsageFormat.cost(0.00412))
        assertEquals("$0.0001", UsageFormat.cost(0.00005))
    }

    @Test
    fun costBelowOneDollarShowsThreeDecimals() {
        assertEquals("$0.013", UsageFormat.cost(0.0134))
        assertEquals("$0.210", UsageFormat.cost(0.21))
    }

    @Test
    fun costFromOneDollarShowsTwoDecimals() {
        assertEquals("$1.25", UsageFormat.cost(1.2549))
        assertEquals("$12.00", UsageFormat.cost(12.0))
    }

    @Test
    fun zeroCostIsPlain() {
        assertEquals("$0", UsageFormat.cost(0.0))
    }

    @Test
    fun contextWindowUsesKAndM() {
        assertEquals("950", UsageFormat.tokenCount(950))
        assertEquals("8K", UsageFormat.tokenCount(8_192))
        assertEquals("131K", UsageFormat.tokenCount(131_072))
        assertEquals("200K", UsageFormat.tokenCount(200_000))
        assertEquals("1M", UsageFormat.tokenCount(1_048_576))
        assertEquals("1.5M", UsageFormat.tokenCount(1_500_000))
    }

    @Test
    fun exactTokensAreGroupedByThousands() {
        assertEquals("48,210", UsageFormat.exactTokens(48_210))
        assertEquals("0", UsageFormat.exactTokens(0))
    }

    @Test
    fun percentUsedIsRoundedAndClamped() {
        assertEquals(24, UsageFormat.percentUsed(used = 48_210, window = 200_000))
        assertEquals(0, UsageFormat.percentUsed(used = 100, window = 0))
        assertEquals(100, UsageFormat.percentUsed(used = 250_000, window = 200_000))
        assertEquals(1, UsageFormat.percentUsed(used = 1, window = 200_000))
    }

    @Test
    fun priceIsPerMillionInAndOut() {
        assertEquals("$0.10 / $0.40", UsageFormat.pricePerMillion(0.10, 0.40))
        assertEquals("$3.00 / $15.00", UsageFormat.pricePerMillion(3.0, 15.0))
    }
}
