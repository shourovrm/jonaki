package app.jonaki.run

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun aCleanGeneratedNameIsKept() {
        assertEquals("Trip plan for Dhaka", ThreadTitles.fromGenerated("Trip plan for Dhaka"))
    }

    @Test
    fun straightAndCurlyQuotesAroundTheNameAreRemoved() {
        assertEquals("Trip plan", ThreadTitles.fromGenerated("\"Trip plan\""))
        assertEquals("Trip plan", ThreadTitles.fromGenerated("“Trip plan”"))
        assertEquals("Trip plan", ThreadTitles.fromGenerated("‘Trip plan’"))
    }

    @Test
    fun aTitleLabelIsRemovedWhateverItsCaseOrMarkdown() {
        assertEquals("Trip plan", ThreadTitles.fromGenerated("Title: Trip plan"))
        assertEquals("Trip plan", ThreadTitles.fromGenerated("TITLE:Trip plan"))
        assertEquals("Trip plan", ThreadTitles.fromGenerated("**Title:** Trip plan"))
        assertEquals("Trip plan", ThreadTitles.fromGenerated("Title: \"Trip plan\""))
    }

    @Test
    fun trailingFullStopsAndSimilarMarksAreRemovedButNotAQuestionMark() {
        assertEquals("Trip plan", ThreadTitles.fromGenerated("Trip plan."))
        assertEquals("Trip plan", ThreadTitles.fromGenerated("\"Trip plan.\""))
        assertEquals("Trip plan", ThreadTitles.fromGenerated("\"Trip plan\"."))
        assertEquals("Trip plan", ThreadTitles.fromGenerated("Trip plan..."))
        assertEquals("Why is the sky blue?", ThreadTitles.fromGenerated("Why is the sky blue?"))
    }

    @Test
    fun onlyTheFirstNonBlankLineCounts() {
        assertEquals("Trip plan", ThreadTitles.fromGenerated("\n  \nTrip plan\nThis covers the days."))
    }

    @Test
    fun runsOfSpacesAndTabsBecomeOneSpace() {
        assertEquals("Trip plan for Dhaka", ThreadTitles.fromGenerated("  Trip \t plan   for Dhaka "))
    }

    @Test
    fun aLongNameIsCutAtAWordWithAnEllipsisWithinSixtyCharacters() {
        val name = ThreadTitles.fromGenerated("alpha ".repeat(20))!!
        assertTrue(name.length <= 60)
        assertTrue(name.endsWith("alpha…"))
    }

    @Test
    fun aNameOfExactlySixtyCharactersIsKept() {
        val sixty = "a".repeat(30) + " " + "b".repeat(29)
        assertEquals(60, sixty.length)
        assertEquals(sixty, ThreadTitles.fromGenerated(sixty))
    }

    @Test
    fun nothingUsableGivesNull() {
        assertNull(ThreadTitles.fromGenerated(""))
        assertNull(ThreadTitles.fromGenerated("  \n \t"))
        assertNull(ThreadTitles.fromGenerated("\"\""))
        assertNull(ThreadTitles.fromGenerated("Title:"))
        assertNull(ThreadTitles.fromGenerated("..."))
    }

    @Test
    fun banglaNamesKeepTheirTextAndLoseQuotesAndTheDanda() {
        assertEquals("ঢাকার আবহাওয়ার পূর্বাভাস", ThreadTitles.fromGenerated("“ঢাকার আবহাওয়ার পূর্বাভাস।”"))
        assertEquals("ঢাকার আবহাওয়া", ThreadTitles.fromGenerated("শিরোনাম: ঢাকার আবহাওয়া"))
    }

    @Test
    fun aLongBanglaNameIsCutAtAWord() {
        val name = ThreadTitles.fromGenerated("আবহাওয়া ".repeat(15))!!
        assertTrue(name.length <= 60)
        assertTrue(name.endsWith("আবহাওয়া…"))
    }
}
