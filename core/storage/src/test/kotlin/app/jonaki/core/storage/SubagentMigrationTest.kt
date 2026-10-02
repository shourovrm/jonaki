package app.jonaki.core.storage

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs Room's generated 6 to 7 migration (subagents and approval modes, M7, D-058). */
class SubagentMigrationTest {
    private val databases = MigrationTestDatabases()

    @After
    fun closeConnections() {
        databases.closeAll()
    }

    private fun openVersion6WithData(): SQLiteConnection {
        val connection = databases.openVersion(6)
        MemorySearchIndex.create(connection)
        connection.execSQL(
            "INSERT INTO threads (id, title, createdAtMillis, updatedAtMillis, webSearchEnabled, toolsAllowedForThread, " +
                "modelKey, memoryExtractedUpToPosition, disabledSkills, thinkingLevel) " +
                "VALUES ('t1', 'Trip', 1, 2, 1, 'write_file', 'openrouter:x', 0, '', 'LOW')",
        )
        connection.execSQL(
            "INSERT INTO steps (toolCallId, threadId, toolName, argumentsJson, status, resultText, startedAtMillis, finishedAtMillis) " +
                "VALUES ('c1', 't1', 'read_file', '{}', 'DONE', 'ok', 1, 2)",
        )
        return connection
    }

    @Test
    fun existingThreadsFollowTheDefaultModeAndStepsBelongToTheThreadsAgent() {
        val connection = openVersion6WithData()

        JonakiDatabase_AutoMigration_6_7_Impl().migrate(connection)

        assertEquals(
            listOf("t1|write_file|LOW|null"),
            databases.queryStrings(connection, "SELECT id, toolsAllowedForThread, thinkingLevel, approvalMode FROM threads"),
        )
        assertEquals(listOf("c1|DONE|null"), databases.queryStrings(connection, "SELECT toolCallId, status, subagentId FROM steps"))
        assertEquals(listOf("0"), databases.queryStrings(connection, "SELECT COUNT(*) FROM subagents"))
    }

    @Test
    fun upgradedDatabaseHasTheSameTablesAsAFreshOne() {
        val upgraded = openVersion6WithData()
        JonakiDatabase_AutoMigration_6_7_Impl().migrate(upgraded)
        val fresh = databases.openVersion(7)
        MemorySearchIndex.create(fresh)

        assertEquals(databases.describeTables(fresh), databases.describeTables(upgraded))
    }

    @Test
    fun deletingAThreadDeletesItsSubagents() {
        val connection = openVersion6WithData()
        JonakiDatabase_AutoMigration_6_7_Impl().migrate(connection)
        connection.execSQL(
            "INSERT INTO subagents (id, threadId, parentToolCallId, orderInCall, agentType, task, model, status, " +
                "resultText, costUsd, startedAtMillis, finishedAtMillis) " +
                "VALUES ('s1', 't1', 'c1', 0, 'scout', 'Find', NULL, 'DONE', 'ok', 0.01, 1, 2)",
        )

        connection.execSQL("DELETE FROM threads WHERE id = 't1'")

        assertEquals(listOf("0"), databases.queryStrings(connection, "SELECT COUNT(*) FROM subagents"))
    }
}
