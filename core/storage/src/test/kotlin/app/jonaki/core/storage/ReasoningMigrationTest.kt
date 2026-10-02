package app.jonaki.core.storage

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs Room's generated 4 to 5 migration (saved reasoning, D-054). */
class ReasoningMigrationTest {
    private val databases = MigrationTestDatabases()

    @After
    fun closeConnections() {
        databases.closeAll()
    }

    private fun openVersion4WithData(): SQLiteConnection {
        val connection = databases.openVersion(4)
        MemorySearchIndex.create(connection)
        connection.execSQL(
            "INSERT INTO threads (id, title, createdAtMillis, updatedAtMillis, webSearchEnabled, toolsAllowedForThread, " +
                "modelKey, memoryExtractedUpToPosition, disabledSkills) VALUES ('t1', 'Trip', 1, 2, 1, '', 'openrouter:x', 0, 'slides')",
        )
        connection.execSQL(
            "INSERT INTO messages (id, threadId, position, role, text, toolCallsJson, toolCallId, isComplete, createdAtMillis, costUsd) " +
                "VALUES ('m1', 't1', 0, 'ASSISTANT', 'চলো যাই', '[]', NULL, 1, 1, 0.001)",
        )
        return connection
    }

    @Test
    fun messagesSurviveWithNoReasoning() {
        val connection = openVersion4WithData()

        JonakiDatabase_AutoMigration_4_5_Impl().migrate(connection)

        assertEquals(
            listOf("m1|চলো যাই|0.001|null"),
            databases.queryStrings(connection, "SELECT id, text, costUsd, reasoningText FROM messages"),
        )
        assertEquals(listOf("t1|slides"), databases.queryStrings(connection, "SELECT id, disabledSkills FROM threads"))
    }

    @Test
    fun upgradedDatabaseHasTheSameTablesAsAFreshOne() {
        val upgraded = openVersion4WithData()
        JonakiDatabase_AutoMigration_4_5_Impl().migrate(upgraded)
        val fresh = databases.openVersion(5)
        MemorySearchIndex.create(fresh)

        assertEquals(databases.describeTables(fresh), databases.describeTables(upgraded))
    }
}
