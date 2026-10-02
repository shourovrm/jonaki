package app.jonaki.settings

import app.jonaki.core.balanceapi.Balance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsdRatesTest {
    // Trimmed from https://open.er-api.com/v6/latest/USD on 2026-10-02.
    private val answer = """
        {"result":"success","base_code":"USD","rates":{"USD":1,"CNY":7.1,"BDT":121.5}}
    """.trimIndent()

    @Test
    fun ratesAreReadPerCurrency() {
        val rates = UsdRates.parse(answer)!!

        assertEquals(7.1, rates.getValue("CNY"), 1e-9)
        assertEquals(1.0, rates.getValue("USD"), 1e-9)
    }

    @Test
    fun anErrorAnswerGivesNoRates() {
        assertNull(UsdRates.parse("""{"result":"error","error-type":"unknown-code"}"""))
        assertNull(UsdRates.parse("not json"))
    }

    @Test
    fun yuanBecomeDollarsWithTheMonthToo() {
        val converted = Balance.Money(71.0, "CNY", spentThisMonth = 7.1).inDollars(mapOf("CNY" to 7.1)) as Balance.Money

        assertEquals("USD", converted.currency)
        assertEquals(10.0, converted.amount, 1e-9)
        assertEquals(1.0, converted.spentThisMonth!!, 1e-9)
    }

    @Test
    fun dollarsNeedNoRates() {
        val dollars = Balance.Money(4.73, "USD")

        assertEquals(dollars, dollars.inDollars(null))
    }

    @Test
    fun anUnknownRateFailsInsteadOfShowingTheWrongCurrency() {
        val converted = Balance.Money(110.0, "CNY").inDollars(null)

        assertTrue(converted is Balance.Failed)
    }

    @Test
    fun creditsAreNotMoney() {
        val credits = Balance.Credits(used = 3, limit = 1000)

        assertEquals(credits, credits.inDollars(null))
    }
}
