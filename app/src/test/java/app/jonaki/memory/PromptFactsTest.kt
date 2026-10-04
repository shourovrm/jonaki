package app.jonaki.memory

import app.jonaki.core.agent.PromptFactScope
import app.jonaki.core.storage.MemoryEntity
import app.jonaki.core.storage.MemoryOrigin
import app.jonaki.core.storage.MemoryScope
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PromptFactsTest {
    private val dhaka = ZoneId.of("Asia/Dhaka")

    private fun memory(id: Long, text: String, updatedAtMillis: Long, threadId: String? = "t1") = MemoryEntity(
        id = id,
        scope = if (threadId == null) MemoryScope.GLOBAL else MemoryScope.THREAD,
        threadId = threadId,
        text = text,
        origin = MemoryOrigin.TOOL,
        createdAtMillis = 0,
        updatedAtMillis = updatedAtMillis,
    )

    @Test
    fun theDayIsTheLocalDayOfTheLastChange() {
        // 2026-10-03 20:30 UTC is already 4 October in Dhaka (UTC+6).
        val changedAt = 1_791_059_400_000L

        val fact = PromptFacts.of(memory(1, "Thesis due 20 December", changedAt), dhaka)

        assertEquals("2026-10-04", fact.savedOn)
        assertEquals(PromptFactScope.THREAD, fact.scope)
    }

    @Test
    fun idsSurviveBeingStoredAsText() {
        assertEquals(listOf(3L, 12L, 40L), PromptFacts.idsOf(PromptFacts.idsText(listOf(3, 12, 40))))
        assertNull(PromptFacts.idsText(emptyList()))
        assertEquals(emptyList<Long>(), PromptFacts.idsOf(null))
    }

    @Test
    fun theSheetListsTheLastRunsFactsWithoutThoseDeletedSince() {
        val facts = listOf(memory(1, "A", 0), memory(2, "B", 0), memory(3, "C", 0)).map { PromptFacts.of(it, dhaka) }

        val lines = PromptFacts.sheetLines(facts, lastRunIds = listOf(3, 1, 9), nextRequestIds = listOf(1, 2, 3))

        assertEquals(listOf("(1970-01-01) A", "(1970-01-01) C"), lines)
    }

    @Test
    fun beforeTheFirstRunTheSheetListsWhatTheNextRequestSends() {
        val facts = listOf(memory(1, "A", 0), memory(2, "B", 0)).map { PromptFacts.of(it, dhaka) }

        val lines = PromptFacts.sheetLines(facts, lastRunIds = null, nextRequestIds = listOf(2))

        assertEquals(listOf("(1970-01-01) B"), lines)
    }
}
