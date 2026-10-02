package app.jonaki.core.storage

import app.jonaki.core.model.Role
import app.jonaki.core.model.ToolCall
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryMapperTest {

    private var nextPosition = 0L

    private fun row(role: String, text: String, toolCalls: List<ToolCall> = emptyList(), toolCallId: String? = null, isComplete: Boolean = true) =
        MessageEntity(
            id = "m$nextPosition",
            threadId = "t",
            position = nextPosition++,
            role = role,
            text = text,
            toolCallsJson = HistoryMapper.toolCallsToJson(toolCalls),
            toolCallId = toolCallId,
            isComplete = isComplete,
            createdAtMillis = 0,
        )

    @Test
    fun toolCallsSurviveTheJsonRoundTrip() {
        val calls = listOf(ToolCall("call_1", "web_search", """{"query":"rain \"Dhaka\""}"""))

        val decoded = HistoryMapper.toolCallsFromJson(HistoryMapper.toolCallsToJson(calls))

        assertEquals(calls, decoded)
    }

    @Test
    fun plainConversationMapsRoleForRole() {
        val rows = listOf(row("USER", "hi"), row("ASSISTANT", "hello"))

        val history = HistoryMapper.toHistory(rows)

        assertEquals(listOf(Role.USER, Role.ASSISTANT), history.map { it.role })
        assertEquals("hello", history[1].text)
    }

    @Test
    fun errorRowsAreLeftOutOfTheModelHistory() {
        val rows = listOf(row("USER", "hi"), row("ERROR", "Provider failed"), row("USER", "again"))

        val history = HistoryMapper.toHistory(rows)

        assertEquals(listOf("hi", "again"), history.map { it.text })
    }

    @Test
    fun backgroundUsageRowBetweenAToolCallAndItsResultChangesNothing() {
        val call = ToolCall("call_1", "web_search", "{}")
        val rows = listOf(
            row("USER", "hi"),
            row("ASSISTANT", "", toolCalls = listOf(call)),
            row(HistoryMapper.BACKGROUND_ROLE, ""),
            row("TOOL", "results", toolCallId = "call_1"),
            row("ASSISTANT", "done"),
        )

        val history = HistoryMapper.toHistory(rows)

        assertEquals(listOf(Role.USER, Role.ASSISTANT, Role.TOOL, Role.ASSISTANT), history.map { it.role })
        assertEquals("results", history[2].text)
    }

    @Test
    fun emptyAssistantRowLeftByAStopIsDropped() {
        val rows = listOf(row("USER", "hi"), row("ASSISTANT", "", isComplete = false))

        val history = HistoryMapper.toHistory(rows)

        assertEquals(1, history.size)
    }

    @Test
    fun toolCallWithoutResultGetsAStoppedResultSoTheProviderAcceptsTheHistory() {
        val calls = listOf(ToolCall("a", "web_fetch", "{}"), ToolCall("b", "web_fetch", "{}"))
        val rows = listOf(
            row("USER", "read two pages"),
            row("ASSISTANT", "", toolCalls = calls),
            row("TOOL", "page a", toolCallId = "a"),
            row("USER", "never mind"),
        )

        val history = HistoryMapper.toHistory(rows)

        assertEquals(listOf(Role.USER, Role.ASSISTANT, Role.TOOL, Role.TOOL, Role.USER), history.map { it.role })
        assertEquals("b", history[3].toolCallId)
        assertEquals(HistoryMapper.STOPPED_TOOL_RESULT, history[3].text)
    }

    @Test
    fun toolCallWithoutResultAtTheEndIsAlsoClosed() {
        val rows = listOf(row("USER", "go"), row("ASSISTANT", "", toolCalls = listOf(ToolCall("a", "read_file", "{}"))))

        val history = HistoryMapper.toHistory(rows)

        assertEquals(Role.TOOL, history.last().role)
        assertEquals("a", history.last().toolCallId)
    }
}
