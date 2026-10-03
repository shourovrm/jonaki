package app.jonaki.ui

import app.jonaki.core.storage.MessageEntity
import app.jonaki.feature.chat.RequestTimeUi
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class RequestLogRowsTest {
    private var position = 0L

    private fun row(
        role: String = "ASSISTANT",
        sentAt: Long? = null,
        sentElapsed: Long? = null,
        firstText: Long? = null,
        firstShown: Long? = null,
    ) = MessageEntity(
        id = "m$position",
        threadId = "t",
        position = position++,
        role = role,
        text = "text",
        toolCallsJson = "[]",
        toolCallId = null,
        isComplete = true,
        createdAtMillis = 0,
        requestSentAtMillis = sentAt,
        requestSentElapsedMillis = sentElapsed,
        firstTextElapsedMillis = firstText,
        firstShownElapsedMillis = firstShown,
    )

    private fun build(rows: List<MessageEntity>, limit: Int = 10) =
        RequestLogRows.build(rows, limit = limit, zone = ZoneOffset.UTC, locale = java.util.Locale.UK)

    @Test
    fun providerWaitAndShownAfterAreTheGapsBetweenTheThreeTimes() {
        // 12:00:05 UTC; the answer's first text came 812 ms after sending and was drawn 23 ms later.
        val rows = listOf(row(sentAt = 43_205_000, sentElapsed = 10_000, firstText = 10_812, firstShown = 10_835))

        assertEquals(listOf(RequestTimeUi(sentAt = "12:00:05", providerWaitMillis = 812, shownAfterMillis = 23)), build(rows))
    }

    @Test
    fun aRequestWithoutVisibleTextHasNeitherGap() {
        val rows = listOf(row(sentAt = 43_205_000, sentElapsed = 10_000))

        assertEquals(listOf(RequestTimeUi("12:00:05", providerWaitMillis = null, shownAfterMillis = null)), build(rows))
    }

    @Test
    fun anAnswerNotYetDrawnHasAWaitButNoShownTime() {
        val rows = listOf(row(sentAt = 43_205_000, sentElapsed = 10_000, firstText = 10_400))

        assertEquals(RequestTimeUi("12:00:05", providerWaitMillis = 400, shownAfterMillis = null), build(rows).single())
    }

    @Test
    fun rowsWithoutTimesAreLeftOutAndTheNewestComeFirst() {
        val rows = listOf(
            row(role = "USER"),
            // Saved before version 9, or by a subagent or background call.
            row(),
            row(sentAt = 43_201_000, sentElapsed = 1_000),
            row(sentAt = 43_202_000, sentElapsed = 2_000),
            row(sentAt = 43_203_000, sentElapsed = 3_000),
        )

        assertEquals(listOf("12:00:03", "12:00:02"), build(rows, limit = 2).map { request -> request.sentAt })
    }
}
