package app.jonaki.core.storage

import app.jonaki.core.model.Role
import app.jonaki.core.model.ToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactionPlanTest {

    private var nextPosition = 0L

    private fun row(role: String, text: String, toolCalls: List<ToolCall> = emptyList(), toolCallId: String? = null) =
        MessageEntity(
            id = "m$nextPosition",
            threadId = "t",
            position = nextPosition++,
            role = role,
            text = text,
            toolCallsJson = HistoryMapper.toolCallsToJson(toolCalls),
            toolCallId = toolCallId,
            isComplete = true,
            createdAtMillis = 0,
        )

    /** U0 A1 U2 A3(call) T4 A5 U6 A7 U8: four user turns. */
    private fun fourTurns(): List<MessageEntity> = listOf(
        row("USER", "first question"),
        row("ASSISTANT", "first answer"),
        row("USER", "search please"),
        row("ASSISTANT", "", toolCalls = listOf(ToolCall("c1", "web_search", "{}"))),
        row("TOOL", "results", toolCallId = "c1"),
        row("ASSISTANT", "found it"),
        row("USER", "third question"),
        row("ASSISTANT", "third answer"),
        row("USER", "the new message"),
    )

    @Test
    fun compactsFromSeventyPercentOfTheContextWindow() {
        assertTrue(CompactionPlan.isNeeded(lastInputTokens = 70_000, contextWindowTokens = 100_000))
        assertFalse(CompactionPlan.isNeeded(lastInputTokens = 69_999, contextWindowTokens = 100_000))
        assertFalse(CompactionPlan.isNeeded(lastInputTokens = null, contextWindowTokens = 100_000))
    }

    @Test
    fun anUnknownWindowCountsAs128kTokens() {
        assertTrue(CompactionPlan.isNeeded(lastInputTokens = 90_000, contextWindowTokens = null))
        assertFalse(CompactionPlan.isNeeded(lastInputTokens = 89_000, contextWindowTokens = null))
    }

    @Test
    fun theLastTwoUserTurnsStayWordForWord() {
        // Turns starting at U6 and U8 are kept, so U0 to A5 are summarised.
        assertEquals(5L, CompactionPlan.cutPosition(fourTurns(), afterPosition = null))
    }

    @Test
    fun aCutNeverSplitsAToolCallFromItsResult() {
        val rows = fourTurns()
        val cut = CompactionPlan.cutPosition(rows, afterPosition = null, keepUserTurns = 3)!!

        // Turns from U2 are kept; the call A3 and its result T4 stay together.
        assertEquals(1L, cut)
    }

    @Test
    fun nothingNewToSummariseGivesNoCut() {
        assertNull(CompactionPlan.cutPosition(fourTurns(), afterPosition = 5L))
    }

    @Test
    fun tooFewTurnsGiveNoCut() {
        val rows = listOf(row("USER", "a"), row("ASSISTANT", "b"), row("USER", "c"))

        assertNull(CompactionPlan.cutPosition(rows, afterPosition = null))
    }

    @Test
    fun errorRowsAreNotUserTurns() {
        val rows = listOf(
            row("USER", "a"),
            row("ERROR", "timeout"),
            row("USER", "a again"),
            row("ASSISTANT", "b"),
            row("USER", "c"),
        )

        // Kept turns start at "a again" and "c", so only "a" and the error row are summarised.
        assertEquals(1L, CompactionPlan.cutPosition(rows, afterPosition = null))
    }

    @Test
    fun historyStartsWithTheSummaryInsideTheFirstKeptMessage() {
        val rows = fourTurns()

        val history = CompactionPlan.historyAfter(rows, summaryText = "Goal: test", upToPosition = 5L)

        assertEquals(listOf(Role.USER, Role.ASSISTANT, Role.USER), history.map { it.role })
        assertEquals(
            "${CompactionPlan.SUMMARY_HEADING}\nGoal: test\n\n${CompactionPlan.SUMMARY_END}\n\nthird question",
            history.first().text,
        )
    }

    @Test
    fun withoutSummaryTheHistoryIsUnchanged() {
        val rows = fourTurns()

        assertEquals(HistoryMapper.toHistory(rows), CompactionPlan.historyAfter(rows, summaryText = null, upToPosition = null))
    }

    @Test
    fun transcriptCoversOnlyTheNewPartAndShortensToolResults() {
        val rows = fourTurns().toMutableList()
        rows[4] = rows[4].copy(text = "x".repeat(5_000))

        val transcript = CompactionPlan.transcript(rows, afterPosition = 1L, upToPosition = 5L)

        assertTrue(transcript.startsWith("User: search please"))
        assertTrue(transcript.contains("Assistant called web_search"))
        assertTrue(transcript.contains("[result shortened]"))
        assertFalse(transcript.contains("first question"))
        assertFalse(transcript.contains("third question"))
    }
}
