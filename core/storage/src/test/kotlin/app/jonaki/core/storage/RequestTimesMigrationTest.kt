package app.jonaki.core.storage

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs Room's generated 8 to 9 migration (request-log times, D-132). */
class RequestTimesMigrationTest {
    private val databases = MigrationTestDatabases()

    @After
    fun closeConnections() {
        databases.closeAll()
    }

    private fun openVersion8WithData(): SQLiteConnection {
        val connection = databases.openVersion(8)
        MemorySearchIndex.create(connection)
        connection.execSQL(
            "INSERT INTO threads (id, title, createdAtMillis, updatedAtMillis, webSearchEnabled, toolsAllowedForThread, " +
                "disabledSkills, incognito) VALUES ('t1', 'Trip', 1, 2, 1, '', '', 0)",
        )
        connection.execSQL(
            "INSERT INTO messages (id, threadId, position, role, text, toolCallsJson, toolCallId, isComplete, createdAtMillis, " +
                "outputTokens) VALUES ('m1', 't1', 0, 'ASSISTANT', 'চলো যাই', '[]', NULL, 1, 1, 42)",
        )
        return connection
    }

    @Test
    fun oldMessagesKeepTheirTextAndHaveNoTimes() {
        val connection = openVersion8WithData()

        JonakiDatabase_AutoMigration_8_9_Impl().migrate(connection)

        assertEquals(
            listOf("m1|চলো যাই|42|null|null|null|null"),
            databases.queryStrings(
                connection,
                "SELECT id, text, outputTokens, requestSentAtMillis, requestSentElapsedMillis, firstTextElapsedMillis, " +
                    "firstShownElapsedMillis FROM messages",
            ),
        )
    }

    @Test
    fun upgradedDatabaseHasTheSameTablesAsAFreshOne() {
        val upgraded = openVersion8WithData()
        JonakiDatabase_AutoMigration_8_9_Impl().migrate(upgraded)
        val fresh = databases.openVersion(9)
        MemorySearchIndex.create(fresh)

        assertEquals(databases.describeTables(fresh), databases.describeTables(upgraded))
    }
}
