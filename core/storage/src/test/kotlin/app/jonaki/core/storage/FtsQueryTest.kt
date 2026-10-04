package app.jonaki.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FtsQueryTest {
    @Test
    fun eachWordBecomesItsOwnPhraseJoinedWithOr() {
        assertEquals("\"thesis\" OR \"deadline\"", FtsQuery.anyWordOf("thesis  deadline"))
    }

    @Test
    fun wordsTooShortForTrigramsAreLeftOut() {
        assertEquals("\"thesis\"", FtsQuery.anyWordOf("my thesis is"))
    }

    @Test
    fun aQueryOfShortWordsOnlyGivesNull() {
        assertNull(FtsQuery.anyWordOf("is it"))
        assertNull(FtsQuery.anyWordOf("   "))
    }

    @Test
    fun banglaWordsAreCountedInCodePoints() {
        // "জমা" has three code points, so the index can match it.
        assertEquals("\"থিসিস\" OR \"জমা\"", FtsQuery.anyWordOf("থিসিস জমা"))
    }

    @Test
    fun operatorsAndQuotesInTheTextStayPlainText() {
        assertEquals("\"NOT\" OR \"say \"\"hi\"\"\"".replace("say ", "say"), FtsQuery.anyWordOf("NOT say\"hi\""))
    }

    @Test
    fun repeatedWordsAppearOnce() {
        assertEquals("\"thesis\"", FtsQuery.anyWordOf("thesis thesis"))
    }
}
