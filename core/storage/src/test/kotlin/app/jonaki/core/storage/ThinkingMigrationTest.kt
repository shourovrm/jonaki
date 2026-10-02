package app.jonaki.core.storage

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs Room's generated 5 to 6 migration (a thread's thinking level, D-057). */
class ThinkingMigrationTest {
    private val databases = MigrationTestDatabases()

    @After
    fun closeConnections() {
        databases.closeAll()
    }

    private fun openVersion5WithData(): SQLiteConnection {
        val connection = databases.openVersion(5)
        MemorySearchIndex.create(connection)
        connection.execSQL(
            "INSERT INTO threads (id, title, createdAtMillis, updatedAtMillis, webSearchEnabled, toolsAllowedForThread, " +
                "modelKey, memoryExtractedUpToPosition, disabledSkills) VALUES ('t1', 'Trip', 1, 2, 1, '', 'openrouter:x', 0, '')",
        )
        connection.execSQL(
            "INSERT INTO messages (id, threadId, position, role, text, toolCallsJson, toolCallId, isComplete, createdAtMillis, reasoningText) " +
                "VALUES ('m1', 't1', 0, 'ASSISTANT', 'ok', '[]', NULL, 1, 1, 'thought')",
        )
        return connection
    }

    @Test
    fun threadsFollowTheModelAfterTheUpgrade() {
        val connection = openVersion5WithData()

        JonakiDatabase_AutoMigration_5_6_Impl().migrate(connection)

        assertEquals(listOf("t1|openrouter:x|null"), databases.queryStrings(connection, "SELECT id, modelKey, thinkingLevel FROM threads"))
        assertEquals(listOf("m1|thought"), databases.queryStrings(connection, "SELECT id, reasoningText FROM messages"))
    }

    @Test
    fun upgradedDatabaseHasTheSameTablesAsAFreshOne() {
        val upgraded = openVersion5WithData()
        JonakiDatabase_AutoMigration_5_6_Impl().migrate(upgraded)
        val fresh = databases.openVersion(6)
        MemorySearchIndex.create(fresh)

        assertEquals(databases.describeTables(fresh), databases.describeTables(upgraded))
    }
}
