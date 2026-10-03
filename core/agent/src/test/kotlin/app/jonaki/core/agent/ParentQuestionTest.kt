package app.jonaki.core.agent

import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import org.junit.Assert.assertEquals
import org.junit.Test

class ParentQuestionTest {
    private val search = call("c1", "web_search")
    private val delegate = call("c2", "delegate")
    private val history = listOf(
        Message(Role.USER, "Compare three laptops"),
        Message(Role.ASSISTANT, "", toolCalls = listOf(search)),
        Message(Role.TOOL, "results", toolCallId = "c1"),
        Message(Role.ASSISTANT, "Delegating.", toolCalls = listOf(delegate)),
    )

    @Test
    fun theConversationEndsBeforeTheDelegateCallAndAsksTheQuestion() {
        val conversation = ParentQuestion.conversation(history, delegateToolCallId = "c2", agentLabel = "researcher 2", question = "Which budget?")

        // The same messages as the thread's last request, so the prompt cache holds.
        assertEquals(history.subList(0, 3), conversation.subList(0, 3))
        assertEquals(Role.USER, conversation.last().role)
        assertEquals(
            "[A subagent you delegated to (researcher 2) asks: Which budget?\nAnswer it in a few sentences; you cannot call tools now.]",
            conversation.last().text,
        )
    }

    @Test
    fun anUnknownCallKeepsTheWholeHistoryButItsOpenCalls() {
        val conversation = ParentQuestion.conversation(history, delegateToolCallId = "other", agentLabel = "scout", question = "Where?")

        assertEquals(history.subList(0, 3), conversation.subList(0, 3))
        assertEquals(4, conversation.size)
    }
}
