package app.jonaki.run

import app.jonaki.core.storage.IncognitoThreadActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncognitoCleanupTest {
    private val hour = 60L * 60 * 1_000
    private val now = 1_000L * hour

    private val deleted = mutableListOf<String>()
    private var incognitoThreads = listOf<IncognitoThreadActivity>()
    private var running = setOf<String>()

    private val cleanup = IncognitoCleanup(
        listIncognito = { incognitoThreads },
        isRunning = { threadId -> threadId in running },
        deleteThread = { threadId -> deleted += threadId },
        clock = { now },
    )

    @Test
    fun expiresExactlyOneDayAfterTheLastMessage() {
        assertFalse(IncognitoCleanup.isExpired(lastMessageAtMillis = now - 24 * hour + 1, nowMillis = now))
        assertTrue(IncognitoCleanup.isExpired(lastMessageAtMillis = now - 24 * hour, nowMillis = now))
    }

    @Test
    fun deletesOnlyThreadsWhoseLastMessageIsADayOld() = runBlocking {
        incognitoThreads = listOf(
            IncognitoThreadActivity("old", lastMessageAtMillis = now - 25 * hour),
            IncognitoThreadActivity("recent", lastMessageAtMillis = now - 23 * hour),
        )

        val removed = cleanup.deleteExpired()

        assertEquals(listOf("old"), removed)
        assertEquals(listOf("old"), deleted)
    }

    @Test
    fun aRunningThreadIsNeverDeleted() = runBlocking {
        incognitoThreads = listOf(IncognitoThreadActivity("busy", lastMessageAtMillis = now - 30 * hour))
        running = setOf("busy")

        cleanup.deleteExpired()

        assertEquals(emptyList<String>(), deleted)
    }

    @Test
    fun aKeptThreadIsNoLongerListedSoItStays() = runBlocking {
        // Keep turns incognito off, and the activity query lists incognito threads only.
        incognitoThreads = emptyList()

        cleanup.deleteExpired()

        assertEquals(emptyList<String>(), deleted)
    }
}
