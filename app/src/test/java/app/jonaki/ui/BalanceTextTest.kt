package app.jonaki.ui

import app.jonaki.core.balanceapi.Balance
import org.junit.Assert.assertEquals
import org.junit.Test

class BalanceTextTest {
    private val words = BalanceText.Words(
        moneyLeft = { amount -> "$amount left" },
        creditsOfLimit = { used, limit -> "$used / $limit credits this month" },
        creditsUsed = { used -> "$used credits used" },
    )

    @Test
    fun dollarsShowWithTwoDecimals() {
        assertEquals("$12.87 left", BalanceText.of(Balance.Money(12.873781, "USD"), words))
    }

    @Test
    fun yuanUsesItsSign() {
        assertEquals("¥30.00 left", BalanceText.of(Balance.Money(30.0, "CNY"), words))
    }

    @Test
    fun creditsShowUsedOfLimitWithThousandsSeparator() {
        assertEquals("3 / 1,000 credits this month", BalanceText.of(Balance.Credits(3, 1000), words))
        assertEquals("1,250 credits used", BalanceText.of(Balance.Credits(1250, null), words))
    }

    @Test
    fun noBalanceShowsNothing() {
        assertEquals(null, BalanceText.of(Balance.Unavailable, words))
        assertEquals(null, BalanceText.of(Balance.Failed("timeout"), words))
        assertEquals(null, BalanceText.of(null, words))
    }
}
