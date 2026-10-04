package app.jonaki.core.storage

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** The cost total and the guard note statements, run on the version 12 schema. */
class GuardQueriesTest {
    private val databases = MigrationTestDatabases()
    private lateinit var connection: SQLiteConnection

    @Before
    fun openDatabase() {
        connection = databases.openVersion(12)
        connection.execSQL(
            "INSERT INTO threads (id, title, createdAtMillis, updatedAtMillis, webSearchEnabled, toolsAllowedForThread, " +
                "disabledSkills, incognito) VALUES ('t', 't', 1, 1, 1, '', '', 0)",
        )
        connection.execSQL(
            "INSERT INTO steps (toolCallId, threadId, toolName, argumentsJson, status, startedAtMillis) " +
                "VALUES ('call-1', 't', 'phone', '{}', 'RUNNING', 1)",
        )
    }

    @After
    fun closeConnections() {
        databases.closeAll()
    }

    private fun insertMessage(position: Int, role: String, model: String, costUsd: Double) {
        connection.execSQL(
            "INSERT INTO messages (id, threadId, position, role, text, toolCallsJson, toolCallId, isComplete, createdAtMillis, " +
                "model, costUsd) VALUES ('m$position', 't', $position, '$role', '', '[]', NULL, 1, $position, '$model', $costUsd)",
        )
    }

    private fun threadCost(): Double =
        databases.queryStrings(connection, GuardQueries.THREAD_COST.replace(":threadId", "'t'")).single().toDouble()

    private fun appendNote(toolCallId: String, note: String) {
        // The statement names :note before :toolCallId, so SQLite numbers them in that order.
        connection.prepare(GuardQueries.APPEND_GUARD_NOTE).use { statement ->
            statement.bindText(1, note)
            statement.bindText(2, toolCallId)
            statement.step()
        }
    }

    private fun noteOf(toolCallId: String): String =
        databases.queryStrings(connection, "SELECT guardNote FROM steps WHERE toolCallId = '$toolCallId'").single()

    @Test
    fun aHiddenGuardRowAddsItsCostToTheThreadTotal() {
        insertMessage(1, "ASSISTANT", "openrouter:some/model", 0.01)
        insertMessage(2, "BACKGROUND", "openrouter:typesafe/jev-1.13", 0.00002)

        assertEquals(0.01002, threadCost(), 1e-12)
    }

    @Test
    fun theFirstGuardLineBecomesTheNote() {
        appendNote("call-1", "Jev: card shown (not sure the user asked, 0.41)")

        assertEquals("Jev: card shown (not sure the user asked, 0.41)", noteOf("call-1"))
    }

    @Test
    fun aSecondGuardLineGoesUnderTheFirstAfterALineBreak() {
        appendNote("call-1", "Jev: ran without a card (reversible 0.97, asked for 0.81)")
        appendNote("call-1", "Jev: result clear (0.04)")

        assertEquals(
            "Jev: ran without a card (reversible 0.97, asked for 0.81)\nJev: result clear (0.04)",
            noteOf("call-1"),
        )
    }

    @Test
    fun aNoteForAnUnknownStepChangesNothing() {
        appendNote("no-such-call", "Jev: result clear (0.04)")

        assertEquals("null", noteOf("call-1"))
    }
}
