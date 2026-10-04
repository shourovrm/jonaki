package app.jonaki.memory

import app.jonaki.core.storage.MemoryEntity
import app.jonaki.core.storage.MemoryOrigin
import app.jonaki.core.storage.MemoryScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MemoryEditsTest {
    private val fact = MemoryEntity(
        id = 3,
        scope = MemoryScope.GLOBAL,
        threadId = null,
        text = "থিসিস জমা ১২ ডিসেম্বর",
        keywords = "thesis deadline",
        origin = MemoryOrigin.EXTRACTED,
        pendingReview = true,
        createdAtMillis = 1,
        updatedAtMillis = 2,
    )

    @Test
    fun editingTheTextClearsTheKeywordsAndMakesTheFactTheUsers() {
        val edited = MemoryEdits.edited(fact, "থিসিস জমা ২০ ডিসেম্বর", now = 9)

        assertEquals("", edited.keywords)
        assertEquals("থিসিস জমা ২০ ডিসেম্বর", edited.text)
        assertEquals(MemoryOrigin.USER, edited.origin)
        assertFalse(edited.pendingReview)
        assertEquals(9L, edited.updatedAtMillis)
    }

    @Test
    fun savingTheSameTextKeepsTheKeywords() {
        assertEquals("thesis deadline", MemoryEdits.edited(fact, fact.text, now = 9).keywords)
    }
}
