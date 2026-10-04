package app.jonaki.core.storage

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs Room's generated 10 to 11 migration (thread allowance and the outside-content fact). */
class ApprovalStateMigrationTest {
    private val databases = MigrationTestDatabases()

    @After
    fun closeConnections() {
        databases.closeAll()
    }

    private fun insertThread(connection: SQLiteConnection, id: String) {
        connection.execSQL(
            "INSERT INTO threads (id, title, createdAtMillis, updatedAtMillis, webSearchEnabled, toolsAllowedForThread, " +
                "disabledSkills, incognito) VALUES ('$id', 'Trip', 1, 2, 1, 'write_file', '', 0)",
        )
    }

    private fun insertStep(connection: SQLiteConnection, id: String, threadId: String, toolName: String, arguments: String) {
        connection.execSQL(
            "INSERT INTO steps (toolCallId, threadId, toolName, argumentsJson, status, resultText, startedAtMillis, finishedAtMillis) " +
                "VALUES ('$id', '$threadId', '$toolName', '${arguments.replace("'", "''")}', 'DONE', 'ok', 1, 2)",
        )
    }

    private fun openVersion10WithThreads(): SQLiteConnection {
        val connection = databases.openVersion(10)
        MemorySearchIndex.create(connection)
        for (id in listOf("plain", "web", "mcpRead", "mcpCall", "inboxImage", "ownImage", "subagent")) {
            insertThread(connection, id)
        }
        insertStep(connection, "s1", "plain", "read_file", "{}")
        insertStep(connection, "s2", "web", "web_fetch", """{"url":"https://a.example"}""")
        insertStep(connection, "s3", "mcpRead", "mcp", """{"action":"search","query":"x"}""")
        insertStep(connection, "s4", "mcpCall", "mcp", """{"action":"call","server":"a","tool":"b"}""")
        insertStep(connection, "s5", "inboxImage", "view_image", """{"path":"inbox/a.png"}""")
        insertStep(connection, "s6", "ownImage", "view_image", """{"path":"artifacts/chart.png"}""")
        insertStep(connection, "s7", "subagent", "delegate", """{"agent":"researcher","task":"x"}""")
        return connection
    }

    @Test
    fun newThreadColumnsStartOffAndOldValuesStay() {
        val connection = databases.openVersion(10)
        MemorySearchIndex.create(connection)
        insertThread(connection, "t1")

        JonakiDatabase_AutoMigration_10_11_Impl().migrate(connection)

        assertEquals(
            listOf("t1|write_file|0|0"),
            databases.queryStrings(connection, "SELECT id, toolsAllowedForThread, allowAllInThread, readOutsideContent FROM threads"),
        )
    }

    @Test
    fun threadsThatAlreadyReadOutsideContentAreMarked() {
        val connection = openVersion10WithThreads()

        JonakiDatabase_AutoMigration_10_11_Impl().migrate(connection)

        assertEquals(
            listOf("inboxImage|1", "mcpCall|1", "mcpRead|0", "ownImage|0", "plain|0", "subagent|1", "web|1"),
            databases.queryStrings(connection, "SELECT id, readOutsideContent FROM threads ORDER BY id"),
        )
    }

    @Test
    fun upgradedDatabaseHasTheSameTablesAsAFreshOne() {
        val upgraded = openVersion10WithThreads()
        JonakiDatabase_AutoMigration_10_11_Impl().migrate(upgraded)
        val fresh = databases.openVersion(11)
        MemorySearchIndex.create(fresh)

        assertEquals(databases.describeTables(fresh), databases.describeTables(upgraded))
    }
}
