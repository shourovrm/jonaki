package app.jonaki.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryLineTextTest {
    @Test
    fun `added facts are remembered`() {
        assertEquals("Remembered 2 facts", MemoryLineText.from("2 added"))
        assertEquals("Remembered 1 fact", MemoryLineText.from("1 added"))
    }

    @Test
    fun `facts waiting for review are counted`() {
        assertEquals("1 fact to review", MemoryLineText.from("1 to review"))
        assertEquals("3 facts to review", MemoryLineText.from("3 to review"))
    }

    @Test
    fun `added and waiting facts are both named`() {
        assertEquals("Remembered 1 fact, 2 to review", MemoryLineText.from("1 added, 2 to review"))
    }

    @Test
    fun `nothing new has no line`() {
        assertNull(MemoryLineText.from("Nothing new"))
    }

    @Test
    fun `any other result is a failure`() {
        assertEquals("Memory not updated: no key for Gemini", MemoryLineText.from("no key for Gemini"))
        assertEquals("Memory not updated: Answer unreadable: cut off", MemoryLineText.from("Answer unreadable: cut off"))
    }
}
