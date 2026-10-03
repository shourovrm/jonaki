package app.jonaki.core.storage

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** The thread list's summary query, run on the version 8 schema. */
class ThreadListQueriesTest {
    private val databases = MigrationTestDatabases()
    private lateinit var connection: SQLiteConnection

    @Before
    fun openDatabase() {
        connection = databases.openVersion(8)
    }

    @After
    fun closeConnections() {
        databases.closeAll()
    }

    private fun insertThread(id: String) {
        connection.execSQL(
            "INSERT INTO threads (id, title, createdAtMillis, updatedAtMillis, webSearchEnabled, toolsAllowedForThread, " +
                "disabledSkills, incognito) VALUES ('$id', '$id', 100, 100, 1, '', '', 0)",
        )
    }

    private fun insertMessage(threadId: String, position: Int, role: String, text: String) {
        connection.execSQL(
            "INSERT INTO messages (id, threadId, position, role, text, toolCallsJson, toolCallId, isComplete, createdAtMillis) " +
                "VALUES ('$threadId-$position', '$threadId', $position, '$role', '$text', '[]', NULL, 1, $position)",
        )
    }

    private fun lastTextAndRole(): List<String> =
        databases.queryStrings(connection, "SELECT id, lastText, lastRole FROM (${ThreadListQueries.SUMMARIES}) ORDER BY id")

    @Test
    fun aRunningThreadNamesTheRoleOfTheRowItsTextCameFrom() {
        // While a run works, the newest row is the assistant's tool call, which has no text.
        insertThread("running")
        insertMessage("running", position = 0, role = "USER", text = "[Saturday 3 October 2026, 10:43 Asia/Dhaka]\nhello")
        insertMessage("running", position = 1, role = "ASSISTANT", text = "")
        insertMessage("running", position = 2, role = "TOOL", text = "result")

        assertEquals(listOf("running|[Saturday 3 October 2026, 10:43 Asia/Dhaka]\nhello|USER"), lastTextAndRole())
    }

    @Test
    fun aFinishedThreadShowsTheAnswer() {
        insertThread("done")
        insertMessage("done", position = 0, role = "USER", text = "question")
        insertMessage("done", position = 1, role = "ASSISTANT", text = "answer")
        insertMessage("done", position = 2, role = "BACKGROUND", text = "summary call")

        assertEquals(listOf("done|answer|ASSISTANT"), lastTextAndRole())
    }

    @Test
    fun aThreadWithoutMessagesHasNoText() {
        insertThread("empty")

        assertEquals(listOf("empty|null|null"), lastTextAndRole())
    }
}
