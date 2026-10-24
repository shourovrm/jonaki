package app.jonaki.memory

import kotlin.test.Test
import kotlin.test.assertEquals

/** D-188: a thread is read for facts after 8 new messages, not 20; leaving still needs 2. */
class ExtractionTriggerTest {
    @Test
    fun `eight new messages trigger an extraction after a run`() {
        assertEquals(8, MemoryExtractor.MESSAGES_PER_EXTRACTION)
    }

    @Test
    fun `two new messages trigger an extraction on leaving`() {
        assertEquals(2, MemoryExtractor.MESSAGES_FOR_EXTRACTION_ON_LEAVE)
    }
}
