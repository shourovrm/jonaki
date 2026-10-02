package app.jonaki.ui

import app.jonaki.core.model.ToolCall
import app.jonaki.core.storage.HistoryMapper
import app.jonaki.core.storage.MessageEntity
import app.jonaki.core.storage.StepEntity
import app.jonaki.feature.chat.ChatItem
import app.jonaki.feature.chat.StepUiStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatItemsTest {
    private var position = 0L

    private fun row(id: String, role: String, text: String, calls: List<ToolCall> = emptyList(), callId: String? = null, complete: Boolean = true) =
        MessageEntity(id, "t", position++, role, text, HistoryMapper.toolCallsToJson(calls), callId, complete, 0)

    private fun step(id: String, tool: String, status: String, arguments: String = "{}", started: Long = 0, finished: Long? = null) =
        StepEntity(id, "t", tool, arguments, status, null, started, finished)

    private val searchCall = ToolCall("c1", "web_search", """{"query":"rain dhaka"}""")

    @Test
    fun userTextLosesTheTimeLineAddedForTheModel() {
        val items = ChatItems.build(listOf(row("u1", "USER", "[Friday 2 October 2026, 21:47 Asia/Dhaka]\nhello")), emptyList(), isRunning = false, pendingApproval = null)

        assertEquals(ChatItem.UserMessage("u1", "hello"), items.single())
    }

    @Test
    fun aTurnShowsUserThenRunThenAnswer() {
        val rows = listOf(
            row("u1", "USER", "weather?"),
            row("a1", "ASSISTANT", "", calls = listOf(searchCall)),
            row("r1", "TOOL", "results", callId = "c1"),
            row("a2", "ASSISTANT", "It will rain."),
        )
        val steps = listOf(step("c1", "web_search", "DONE", searchCall.argumentsJson, started = 1_000, finished = 3_100))

        val items = ChatItems.build(rows, steps, isRunning = false, pendingApproval = null)

        assertEquals(listOf("u1", "run-u1", "a2"), items.map { it.id })
        val run = items[1] as ChatItem.Run
        assertEquals(false, run.isActive)
        assertEquals("rain dhaka", run.steps.single().query)
        assertEquals(2_100L, run.steps.single().durationMillis)
        assertEquals(StepUiStatus.DONE, run.steps.single().status)
    }

    @Test
    fun onlyTheLastTurnsRunIsActiveWhileRunning() {
        val rows = listOf(
            row("u1", "USER", "one"),
            row("a1", "ASSISTANT", "", calls = listOf(ToolCall("c1", "read_file", "{}"))),
            row("u2", "USER", "two"),
            row("a2", "ASSISTANT", "", calls = listOf(ToolCall("c2", "read_file", "{}"))),
        )
        val steps = listOf(step("c1", "read_file", "DONE"), step("c2", "read_file", "RUNNING"))

        val runs = ChatItems.build(rows, steps, isRunning = true, pendingApproval = null).filterIsInstance<ChatItem.Run>()

        assertEquals(listOf(false, true), runs.map { it.isActive })
        assertEquals(null, runs[1].steps.single().durationMillis)
    }

    @Test
    fun streamingAnswerIsMarkedUntilComplete() {
        val rows = listOf(row("u1", "USER", "hi"), row("a1", "ASSISTANT", "Hel", complete = false))

        val answer = ChatItems.build(rows, emptyList(), isRunning = true, pendingApproval = null).last() as ChatItem.AssistantMessage

        assertTrue(answer.isStreaming)
    }

    @Test
    fun pendingApprovalFollowsTheRun() {
        val writeCall = ToolCall("c9", "write_file", """{"path":"work/notes.md"}""")
        val rows = listOf(row("u1", "USER", "save"), row("a1", "ASSISTANT", "", calls = listOf(writeCall)))
        val steps = listOf(step("c9", "write_file", "WAITING_FOR_APPROVAL", writeCall.argumentsJson))

        val items = ChatItems.build(rows, steps, isRunning = true, pendingApproval = writeCall)

        assertEquals(ChatItem.Approval("c9", "write_file", "work/notes.md"), items.last())
    }

    @Test
    fun onlyTheLastErrorCanBeRetriedAndOnlyWhenIdle() {
        val rows = listOf(row("u1", "USER", "a"), row("e1", "ERROR", "Busy"), row("u2", "USER", "b"), row("e2", "ERROR", "Busy"))

        val errors = ChatItems.build(rows, emptyList(), isRunning = false, pendingApproval = null).filterIsInstance<ChatItem.Error>()
        val whileRunning = ChatItems.build(rows, emptyList(), isRunning = true, pendingApproval = null).filterIsInstance<ChatItem.Error>()

        assertEquals(listOf(false, true), errors.map { it.canRetry })
        assertEquals(listOf(false, false), whileRunning.map { it.canRetry })
    }

    @Test
    fun aRunWithNothingVisibleYetShowsTheLiveCaret() {
        val items = ChatItems.build(listOf(row("u1", "USER", "hi")), emptyList(), isRunning = true, pendingApproval = null)

        assertEquals(ChatItem.AssistantMessage(ChatItems.WAITING_ID, "", isStreaming = true), items.last())
    }

    @Test
    fun noCaretWhileAStepIsRunningOrTextIsStreaming() {
        val running = ChatItems.build(
            listOf(row("u1", "USER", "go"), row("a1", "ASSISTANT", "", calls = listOf(searchCall))),
            listOf(step("c1", "web_search", "RUNNING")),
            isRunning = true,
            pendingApproval = null,
        )
        val streaming = ChatItems.build(
            listOf(row("u2", "USER", "hi"), row("a2", "ASSISTANT", "He", complete = false)),
            emptyList(),
            isRunning = true,
            pendingApproval = null,
        )

        assertEquals(false, running.any { it.id == ChatItems.WAITING_ID })
        assertEquals(false, streaming.any { it.id == ChatItems.WAITING_ID })
    }

    @Test
    fun aFinishedRunShowsTheCostOfItsTurn() {
        val rows = listOf(
            row("u1", "USER", "weather?"),
            row("a1", "ASSISTANT", "", calls = listOf(searchCall)).copy(costUsd = 0.0010),
            row("r1", "TOOL", "results", callId = "c1"),
            row("a2", "ASSISTANT", "Rain.").copy(costUsd = 0.0031),
        )
        val steps = listOf(step("c1", "web_search", "DONE", started = 0, finished = 1))

        val run = ChatItems.build(rows, steps, isRunning = false, pendingApproval = null).filterIsInstance<ChatItem.Run>().single()

        assertEquals(0.0041, run.costUsd!!, 1e-9)
    }

    @Test
    fun aRunStillWorkingShowsNoCostYet() {
        val rows = listOf(row("u1", "USER", "go"), row("a1", "ASSISTANT", "", calls = listOf(searchCall)).copy(costUsd = 0.001))
        val steps = listOf(step("c1", "web_search", "RUNNING"))

        val run = ChatItems.build(rows, steps, isRunning = true, pendingApproval = null).filterIsInstance<ChatItem.Run>().single()

        assertEquals(null, run.costUsd)
    }

    @Test
    fun routingFallbackAddsOneNoteAfterTheAnswer() {
        val rows = listOf(row("u1", "USER", "hi"), row("a1", "ASSISTANT", "Hello").copy(routingFallback = true))

        val items = ChatItems.build(rows, emptyList(), isRunning = false, pendingApproval = null, fallbackNote = "used cheapest")

        assertEquals(ChatItem.Note("note-a1", "used cheapest"), items.last())
    }
}
