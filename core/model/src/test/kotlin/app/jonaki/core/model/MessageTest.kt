package app.jonaki.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageTest {
    @Test
    fun toolMessageKeepsItsCallId() {
        val result = Message(role = Role.TOOL, text = "42", toolCallId = "call_1")
        assertEquals("call_1", result.toolCallId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun toolMessageWithoutCallIdIsRejected() {
        Message(role = Role.TOOL, text = "42")
    }
}
