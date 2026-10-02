package app.jonaki.settings

import app.jonaki.core.agent.AnswerStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnswerStylesTest {
    @Test
    fun theThreadsStyleWinsOverSettings() {
        assertEquals(AnswerStyle.CONCISE, AnswerStyles.effective(threadStyle = "CONCISE", globalStyle = AnswerStyle.DETAILED))
    }

    @Test
    fun aThreadWithoutAStyleFollowsSettings() {
        assertEquals(AnswerStyle.DETAILED, AnswerStyles.effective(threadStyle = null, globalStyle = AnswerStyle.DETAILED))
    }

    @Test
    fun anUnknownSavedNameFollowsSettings() {
        assertEquals(AnswerStyle.NORMAL, AnswerStyles.effective(threadStyle = "CHATTY", globalStyle = AnswerStyle.NORMAL))
        assertNull(AnswerStyles.fromName("CHATTY"))
        assertNull(AnswerStyles.fromName(null))
    }
}
