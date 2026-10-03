package app.jonaki.core.storage

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** The incognito statements of D-PRJ-2, run on the version 7 schema. */
class IncognitoQueriesTest {
    private val databases = MigrationTestDatabases()
    private lateinit var connection: SQLiteConnection

    @Before
    fun openDatabase() {
        connection = databases.openVersion(8)
        insertThread("secret", incognito = true, createdAtMillis = 100)
        insertThread("regular", incognito = false, createdAtMillis = 100)
        insertThread("empty", incognito = true, createdAtMillis = 500)
        insertMessage("secret", position = 0, role = "USER", createdAtMillis = 1_000)
        insertMessage("secret", position = 1, role = "ASSISTANT", createdAtMillis = 2_000)
        // A memory or summary call after the last message must not extend the thread's life.
        insertMessage("secret", position = 2, role = "BACKGROUND", createdAtMillis = 9_000)
        insertMessage("regular", position = 0, role = "USER", createdAtMillis = 1_000)
    }

    @After
    fun closeConnections() {
        databases.closeAll()
    }

    private fun insertThread(id: String, incognito: Boolean, createdAtMillis: Long) {
        val flag = if (incognito) 1 else 0
        connection.execSQL(
            "INSERT INTO threads (id, title, createdAtMillis, updatedAtMillis, webSearchEnabled, toolsAllowedForThread, " +
                "disabledSkills, incognito) VALUES ('$id', '$id', $createdAtMillis, $createdAtMillis, 1, '', '', $flag)",
        )
    }

    private fun insertMessage(threadId: String, position: Int, role: String, createdAtMillis: Long) {
        connection.execSQL(
            "INSERT INTO messages (id, threadId, position, role, text, toolCallsJson, toolCallId, isComplete, createdAtMillis) " +
                "VALUES ('$threadId-$position', '$threadId', $position, '$role', 'text', '[]', NULL, 1, $createdAtMillis)",
        )
    }

    private fun activity(): List<String> =
        databases.queryStrings(connection, IncognitoQueries.ACTIVITY + " ORDER BY threads.id")

    @Test
    fun activityListsIncognitoThreadsWithTheirLastMessageTime() {
        assertEquals(listOf("empty|500", "secret|2000"), activity())
    }

    @Test
    fun keepStopsTheDeletionAndMarksPastMessagesAsRead() {
        databases.queryStrings(connection, IncognitoQueries.KEEP.replace(":threadId", "?1"), "secret")

        assertEquals(listOf("empty|500"), activity())
        assertEquals(
            listOf("0|2"),
            databases.queryStrings(connection, "SELECT incognito, memoryExtractedUpToPosition FROM threads WHERE id = 'secret'"),
        )
    }

    @Test
    fun keepWithoutMessagesLeavesNothingMarkedAsRead() {
        databases.queryStrings(connection, IncognitoQueries.KEEP.replace(":threadId", "?1"), "empty")

        assertEquals(
            listOf("0|null"),
            databases.queryStrings(connection, "SELECT incognito, memoryExtractedUpToPosition FROM threads WHERE id = 'empty'"),
        )
    }
}
