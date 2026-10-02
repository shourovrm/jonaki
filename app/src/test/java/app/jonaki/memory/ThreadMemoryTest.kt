package app.jonaki.memory

import app.jonaki.core.storage.ThreadEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadMemoryTest {
    private val regular = ThreadEntity(
        id = "t1",
        title = "Trip",
        createdAtMillis = 1,
        updatedAtMillis = 1,
        webSearchEnabled = true,
    )

    @Test
    fun aRegularThreadUsesMemory() {
        assertTrue(ThreadMemory.isOn(regular))
    }

    @Test
    fun anIncognitoThreadUsesNoMemory() {
        assertFalse(ThreadMemory.isOn(regular.copy(incognito = true)))
    }

    @Test
    fun anIncognitoThreadGetsNoMemoryToolAndNoMemorySection() {
        val incognito = regular.copy(incognito = true)

        assertTrue(ThreadMemory.storeFor(incognito) { error("no store for an incognito thread") } == null)
        assertTrue(ThreadMemory.sectionFor(incognito) { error("no facts for an incognito thread") }.isEmpty())
    }
}
