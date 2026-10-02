package app.jonaki.core.storage

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs Room's generated 3 to 4 migration (the thread's switched-off skills, D-040). */
class SkillsMigrationTest {
    private val databases = MigrationTestDatabases()

    @After
    fun closeConnections() {
        databases.closeAll()
    }

    private fun openVersion3WithData(): SQLiteConnection {
        val connection = databases.openVersion(3)
        MemorySearchIndex.create(connection)
        connection.execSQL(
            "INSERT INTO threads (id, title, createdAtMillis, updatedAtMillis, webSearchEnabled, toolsAllowedForThread, " +
                "modelKey, memoryExtractedUpToPosition) VALUES ('t1', 'Thesis plan', 1, 2, 0, 'write_file', 'deepseek:deepseek-chat', 7)",
        )
        connection.execSQL(
            "INSERT INTO messages (id, threadId, position, role, text, toolCallsJson, toolCallId, isComplete, createdAtMillis, costUsd) " +
                "VALUES ('m1', 't1', 0, 'USER', 'আমার থিসিস', '[]', NULL, 1, 1, 0.002)",
        )
        connection.execSQL(
            "INSERT INTO memories (id, scope, threadId, text, pinned, sourceMessageId, origin, pendingReview, " +
                "createdAtMillis, updatedAtMillis, lastUsedAtMillis) VALUES (1, 'thread', 't1', 'Supervisor is Dr Rahman', 1, 'm1', 'tool', 0, 1, 1, NULL)",
        )
        return connection
    }

    @Test
    fun threadsMessagesAndFactsSurviveAndNoSkillIsSwitchedOff() {
        val connection = openVersion3WithData()

        JonakiDatabase_AutoMigration_3_4_Impl().migrate(connection)

        assertEquals(
            listOf("t1|Thesis plan|0|write_file|deepseek:deepseek-chat|7|"),
            databases.queryStrings(
                connection,
                "SELECT id, title, webSearchEnabled, toolsAllowedForThread, modelKey, memoryExtractedUpToPosition, disabledSkills FROM threads",
            ),
        )
        assertEquals(listOf("m1|আমার থিসিস"), databases.queryStrings(connection, "SELECT id, text FROM messages"))
        assertEquals(listOf("1|Supervisor is Dr Rahman|1"), databases.queryStrings(connection, "SELECT id, text, pinned FROM memories"))
    }

    @Test
    fun upgradedDatabaseHasTheSameTablesAsAFreshOne() {
        val upgraded = openVersion3WithData()
        JonakiDatabase_AutoMigration_3_4_Impl().migrate(upgraded)
        val fresh = databases.openVersion(4)
        MemorySearchIndex.create(fresh)

        assertEquals(databases.describeTables(fresh), databases.describeTables(upgraded))
    }
}
