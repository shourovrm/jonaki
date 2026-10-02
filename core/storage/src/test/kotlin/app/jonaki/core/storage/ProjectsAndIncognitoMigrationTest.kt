package app.jonaki.core.storage

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs Room's generated 8 to 9 migration (projects and incognito chat, D-PRJ-1 and D-PRJ-2). */
class ProjectsAndIncognitoMigrationTest {
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
                "modelKey, memoryExtractedUpToPosition, disabledSkills, thinkingLevel) " +
                "VALUES ('t1', 'Trip', 1, 2, 1, '', 'openrouter:x', 0, '', 'HIGH')",
        )
        return connection
    }

    @Test
    fun existingThreadsAreRegularAndWithoutAProject() {
        val connection = openVersion8WithData()

        JonakiDatabase_AutoMigration_8_9_Impl().migrate(connection)

        assertEquals(
            listOf("t1|HIGH|null|0"),
            databases.queryStrings(connection, "SELECT id, thinkingLevel, projectId, incognito FROM threads"),
        )
        assertEquals(listOf("0"), databases.queryStrings(connection, "SELECT COUNT(*) FROM projects"))
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
