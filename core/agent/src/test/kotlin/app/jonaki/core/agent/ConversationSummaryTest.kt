package app.jonaki.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSummaryTest {

    @Test
    fun theSystemPromptNamesEverySectionInOrder() {
        val positions = ConversationSummary.SECTIONS.map { section -> ConversationSummary.SYSTEM_PROMPT.indexOf(section) }

        assertTrue(positions.all { position -> position >= 0 })
        assertEquals(positions.sorted(), positions)
    }

    @Test
    fun aFirstSummaryHoldsOnlyTheConversation() {
        assertEquals("Conversation to add:\nUser: hi", ConversationSummary.requestText(previousSummary = null, transcript = "User: hi"))
    }

    @Test
    fun aPreviousSummaryComesBeforeTheNewPart() {
        val text = ConversationSummary.requestText(previousSummary = "## Goal\nOld goal", transcript = "User: more")

        assertTrue(text.indexOf("Old goal") < text.indexOf("User: more"))
    }
}
