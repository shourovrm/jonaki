package app.jonaki.run

import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadTitlesTest {
    @Test
    fun aWholeQuestionUpToAHundredCharactersIsKept() {
        val question = "Search the web: what is the weather forecast for Dhaka tomorrow?"
        assertEquals(question, ThreadTitles.fromMessage(question))
    }

    @Test
    fun theTimeLineAddedForTheModelIsNotPartOfTheName() {
        assertEquals("hello", ThreadTitles.fromMessage("[Friday 2 October 2026, 16:55 Asia/Dhaka]\nhello"))
    }

    @Test
    fun aVeryLongFirstLineEndsAtAWordAndAnEllipsis() {
        val title = ThreadTitles.fromMessage("word ".repeat(40))
        assertEquals(true, title.length <= 101)
        assertEquals(true, title.endsWith("word…"))
    }

    @Test
    fun namesCutByVersionOneAreRecognised() {
        assertEquals(true, ThreadTitles.looksCutByOldVersion("Search the web: what is the weather…"))
        assertEquals(false, ThreadTitles.looksCutByOldVersion("Thesis — sample size"))
    }
}
