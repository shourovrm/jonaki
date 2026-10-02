package app.jonaki.memory

import app.jonaki.core.storage.MemoryEntity
import app.jonaki.core.storage.MemoryOrigin
import app.jonaki.core.storage.MemoryScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FactTextTest {
    private fun memory(id: Long, text: String) = MemoryEntity(
        id = id,
        scope = MemoryScope.THREAD,
        threadId = "t1",
        text = text,
        origin = MemoryOrigin.TOOL,
        createdAtMillis = 0,
        updatedAtMillis = 0,
    )

    @Test
    fun caseSpacesAndFinalStopDoNotCount() {
        assertEquals(FactText.normalized("likes tea"), FactText.normalized("  Likes   tea. "))
        assertEquals(FactText.normalized("চা পছন্দ"), FactText.normalized("চা পছন্দ।"))
    }

    @Test
    fun findSameReturnsTheMatchingFact() {
        val facts = listOf(memory(1, "Thesis due 15 December"), memory(2, "Likes tea"))
        assertEquals(2L, FactText.findSame(facts, "likes TEA.")?.id)
        assertNull(FactText.findSame(facts, "Likes coffee"))
    }
}
