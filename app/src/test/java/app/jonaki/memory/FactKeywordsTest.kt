package app.jonaki.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FactKeywordsTest {
    @Test
    fun nullAndBlankGiveNoKeywords() {
        assertEquals("", FactKeywords.cleaned(null))
        assertEquals("", FactKeywords.cleaned("   "))
    }

    @Test
    fun commasAndExtraSpacesBecomeSingleSpaces() {
        assertEquals("thesis deadline submission", FactKeywords.cleaned("  thesis,  deadline ,submission "))
    }

    @Test
    fun keywordsLongerThanTheCapAreCutAtAWordEnd() {
        val raw = (1..40).joinToString(" ") { "word$it" }

        val cleaned = FactKeywords.cleaned(raw)

        assertTrue(cleaned.length <= FactKeywords.MAX_LENGTH)
        val keptWordCount = cleaned.split(" ").size
        assertEquals(raw.split(" ").take(keptWordCount).joinToString(" "), cleaned)
    }

    @Test
    fun oneWordLongerThanTheCapIsCutAtTheCap() {
        assertEquals(FactKeywords.MAX_LENGTH, FactKeywords.cleaned("x".repeat(300)).length)
    }

    @Test
    fun banglaKeywordsAreKept() {
        assertEquals("থিসিস thesis", FactKeywords.cleaned("থিসিস thesis"))
    }
}
