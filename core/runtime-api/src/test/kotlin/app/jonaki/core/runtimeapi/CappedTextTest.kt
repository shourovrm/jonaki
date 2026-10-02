package app.jonaki.core.runtimeapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CappedTextTest {
    @Test
    fun keepsTextUnderTheCap() {
        val text = CappedText(maxCharacters = 10)
        text.append("abc")
        text.append("def")

        assertEquals("abcdef", text.toString())
    }

    @Test
    fun cutsAtTheCapAndSaysHowMuchWasDropped() {
        val text = CappedText(maxCharacters = 5)
        text.append("abcdefgh")
        text.append("ij")

        val result = text.toString()
        assertTrue(result, result.startsWith("abcde\n"))
        assertTrue(result, result.contains("5 more characters were dropped"))
    }

    @Test
    fun countsDroppedCharactersFromLaterAppends() {
        val text = CappedText(maxCharacters = 3)
        text.append("abc")
        text.append("defg")

        assertTrue(text.toString().contains("4 more characters were dropped"))
    }
}
