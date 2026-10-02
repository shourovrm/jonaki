package app.jonaki.ui

import app.jonaki.core.balanceapi.Balance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BalanceTextTest {
    private val words = BalanceText.Words(
        moneyLeft = { amount -> "$amount left" },
        creditsOfLimit = { used, limit -> "$used / $limit credits this month" },
        creditsUsed = { used -> "$used credits used" },
        leftAndMonth = { left, month -> "$left left · $month this month" },
        monthOnly = { month -> "$month this month" },
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

    @Test
    fun cardShowsWhatIsLeftAndTheServicesOwnMonth() {
        val line = BalanceText.cardLine(Balance.Money(12.873, "USD", spentThisMonth = 0.0773), appCountedMonthUsd = 5.0, words)!!

        assertEquals("$12.87 left · $0.08 this month", line.text)
        assertEquals(false, line.isLow)
        assertEquals(false, line.monthCountedByApp)
    }

    @Test
    fun cardFallsBackToTheMonthJonakiCounted() {
        val line = BalanceText.cardLine(Balance.Money(110.0, "USD"), appCountedMonthUsd = 3.2, words)!!

        assertEquals("$110 left · $3.20 this month", line.text)
        assertEquals(true, line.monthCountedByApp)
    }

    @Test
    fun cardWithoutBalanceShowsOnlyTheMonth() {
        assertEquals("$0.21 this month", BalanceText.cardLine(null, appCountedMonthUsd = 0.21, words)!!.text)
        assertEquals("$0.21 this month", BalanceText.cardLine(Balance.Failed("timeout"), appCountedMonthUsd = 0.21, words)!!.text)
    }

    @Test
    fun cardWithNothingKnownShowsNoLine() {
        assertNull(BalanceText.cardLine(null, appCountedMonthUsd = null, words))
        assertNull(BalanceText.cardLine(Balance.Unavailable, appCountedMonthUsd = 0.0, words))
    }

    @Test
    fun cardBalanceWithoutAnyMonthShowsOnlyWhatIsLeft() {
        assertEquals("$4.73 left", BalanceText.cardLine(Balance.Money(4.73, "USD"), appCountedMonthUsd = null, words)!!.text)
    }

    @Test
    fun largeBalancesDropTheCentsAndTinySpendIsNotZero() {
        val line = BalanceText.cardLine(Balance.Money(1234.56, "USD", spentThisMonth = 0.004), appCountedMonthUsd = null, words)!!

        assertEquals("$1,234 left · <$0.01 this month", line.text)
    }

    @Test
    fun underOneDollarIsLow() {
        val line = BalanceText.cardLine(Balance.Money(0.42, "USD", spentThisMonth = 11.4), appCountedMonthUsd = null, words)!!

        assertEquals("$0.42 left · $11.40 this month", line.text)
        assertEquals(true, line.isLow)
    }
}
