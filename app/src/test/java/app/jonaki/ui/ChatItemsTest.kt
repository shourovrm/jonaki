package app.jonaki.ui

import app.jonaki.core.model.ToolCall
import app.jonaki.core.storage.HistoryMapper
import app.jonaki.core.storage.MessageEntity
import app.jonaki.core.storage.StepEntity
import app.jonaki.feature.chat.ChatItem
import app.jonaki.feature.chat.WorkingActivity
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
    fun backgroundUsageRowsAreNotShownAndNotCountedInTheRunCost() {
        val rows = listOf(
            row("u1", "USER", "weather?"),
            row("a1", "ASSISTANT", "", calls = listOf(searchCall)).copy(costUsd = 0.01),
            row("r1", "TOOL", "results", callId = "c1"),
            row("a2", "ASSISTANT", "It will rain.").copy(costUsd = 0.02),
            row("b1", HistoryMapper.BACKGROUND_ROLE, "").copy(costUsd = 0.5),
        )
        val steps = listOf(step("c1", "web_search", "DONE", searchCall.argumentsJson))

        val items = ChatItems.build(rows, steps, isRunning = false, pendingApproval = null)

        assertEquals(listOf("u1", "run-u1", "a2"), items.map { it.id })
        assertEquals(0.03, (items[1] as ChatItem.Run).costUsd!!, 1e-9)
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

        val answer = ChatItems.build(rows, emptyList(), isRunning = true, pendingApproval = null)
            .filterIsInstance<ChatItem.AssistantMessage>().single()

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
    fun aRunWithNothingVisibleYetShowsThinking() {
        val user = row("u1", "USER", "hi").copy(createdAtMillis = 5_000)

        val items = ChatItems.build(listOf(user), emptyList(), isRunning = true, pendingApproval = null)

        assertEquals(ChatItem.Working(ChatItems.WORKING_ID, WorkingActivity.Thinking, sinceMillis = 5_000), items.last())
    }

    @Test
    fun theIndicatorNamesTheRunningToolOrTheWriting() {
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

        assertEquals(WorkingActivity.Tool("web_search"), (running.last() as ChatItem.Working).activity)
        assertEquals(WorkingActivity.Writing, (streaming.last() as ChatItem.Working).activity)
    }

    @Test
    fun noIndicatorWhenIdleOrWaitingForApproval() {
        val rows = listOf(row("u1", "USER", "write it"), row("a1", "ASSISTANT", "", calls = listOf(searchCall)))

        val idle = ChatItems.build(rows, emptyList(), isRunning = false, pendingApproval = null)
        val waiting = ChatItems.build(rows, emptyList(), isRunning = true, pendingApproval = searchCall)

        assertEquals(false, idle.any { it is ChatItem.Working })
        assertEquals(false, waiting.any { it is ChatItem.Working })
    }

    @Test
    fun reasoningStreamsOpenThenStaysFoldedBeforeItsAnswer() {
        val thinking = ChatItems.build(
            listOf(row("u1", "USER", "why?"), row("a1", "ASSISTANT", "", complete = false).copy(reasoningText = "Let me see")),
            emptyList(),
            isRunning = true,
            pendingApproval = null,
        )
        val done = ChatItems.build(
            listOf(row("u2", "USER", "why?"), row("a2", "ASSISTANT", "Because.").copy(reasoningText = "Let me see")),
            emptyList(),
            isRunning = false,
            pendingApproval = null,
        )

        assertEquals(ChatItem.Reasoning("reasoning-a1", "Let me see", isStreaming = true), thinking[1])
        assertEquals(WorkingActivity.Thinking, (thinking.last() as ChatItem.Working).activity)
        assertEquals(ChatItem.Reasoning("reasoning-a2", "Let me see", isStreaming = false), done[1])
        assertEquals(ChatItem.AssistantMessage("a2", "Because.", isStreaming = false), done[2])
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

    @Test
    fun aShownArtifactGetsOneCardAfterItsRun() {
        val calls = listOf(
            ToolCall("c1", "artifact", """{"path":"artifacts/report.html"}"""),
            ToolCall("c2", "artifact", """{"path":"artifacts/report.html"}"""),
            ToolCall("c3", "artifact", """{"path":"artifacts/missing.html"}"""),
        )
        val rows = listOf(
            row("u", "USER", "make a report"),
            row("a1", "ASSISTANT", "", calls = calls),
            row("a2", "ASSISTANT", "Done."),
        )
        val steps = listOf(
            step("c1", "artifact", "DONE", """{"path":"artifacts/report.html"}"""),
            step("c2", "artifact", "DONE", """{"path":"artifacts/report.html"}""", started = 1),
            step("c3", "artifact", "FAILED", """{"path":"artifacts/missing.html"}""", started = 2),
        )

        val items = ChatItems.build(rows, steps, isRunning = false, pendingApproval = null)

        val cards = items.filterIsInstance<ChatItem.Artifact>()
        assertEquals(listOf("artifacts/report.html"), cards.map { card -> card.path })
        assertTrue(items.indexOf(cards.single()) > items.indexOfFirst { item -> item is ChatItem.Run })
    }
}
