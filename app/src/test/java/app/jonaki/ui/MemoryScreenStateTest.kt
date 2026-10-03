package app.jonaki.ui

import app.jonaki.core.storage.MemoryEntity
import app.jonaki.feature.memory.FactScopeUi
import app.jonaki.core.storage.MemoryOrigin
import app.jonaki.core.storage.MemoryScope
import app.jonaki.core.storage.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryScreenStateTest {
    private fun fact(id: Long, threadId: String?, text: String, pending: Boolean = false, source: String? = null) = MemoryEntity(
        id = id,
        scope = if (threadId == null) MemoryScope.GLOBAL else MemoryScope.THREAD,
        threadId = threadId,
        text = text,
        sourceMessageId = source,
        origin = MemoryOrigin.EXTRACTED,
        pendingReview = pending,
        createdAtMillis = 0,
        updatedAtMillis = 0,
    )

    private val userMessage = MessageEntity(
        id = "msg-1", threadId = "t1", position = 0, role = "USER",
        text = "[Friday 2 October 2026, 14:44 Asia/Dhaka]\nMy thesis is due on 15 December.",
        toolCallsJson = "[]", toolCallId = null, isComplete = true, createdAtMillis = 0,
    )

    @Test
    fun threadViewSplitsWaitingFactsFromSavedOnesAndShowsTheSourceLine() {
        val state = MemoryScreenState.build(
            threadId = "t1",
            threadTitle = "Thesis plan",
            threadFacts = listOf(fact(1, "t1", "Thesis due 15 December", source = "msg-1"), fact(2, "t1", "Uses LaTeX", pending = true)),
            globalFacts = listOf(fact(3, null, "Name is Riad")),
            pendingFacts = emptyList(),
            sourceMessages = mapOf("msg-1" to userMessage),
            threadTitles = mapOf("t1" to "Thesis plan"),
            reviewMode = true,
        )

        assertEquals("Thesis plan", state.threadTitle)
        assertEquals(listOf(1L), state.threadFacts.map { it.id })
        assertEquals("My thesis is due on 15 December.", state.threadFacts.single().sourcePreview)
        assertEquals(listOf(2L), state.waitingForReview.map { it.id })
        assertNull(state.waitingForReview.single().threadTitle)
        assertEquals(FactScopeUi.GLOBAL, state.globalFacts.single().scope)
    }

    @Test
    fun settingsViewListsEveryThreadsWaitingFactsWithTheirThreadName() {
        val state = MemoryScreenState.build(
            threadId = null,
            threadTitle = null,
            threadFacts = emptyList(),
            globalFacts = emptyList(),
            pendingFacts = listOf(fact(5, "t2", "Likes tea", pending = true)),
            sourceMessages = emptyMap(),
            threadTitles = mapOf("t2" to "Daily notes"),
            reviewMode = true,
        )

        assertNull(state.threadTitle)
        assertEquals("Daily notes", state.waitingForReview.single().threadTitle)
        assertNull(state.waitingForReview.single().sourcePreview)
    }
}
