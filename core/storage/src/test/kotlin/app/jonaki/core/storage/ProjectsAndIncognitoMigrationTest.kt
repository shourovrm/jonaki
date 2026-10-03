package app.jonaki.core.storage

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs Room's generated 7 to 8 migration for projects and incognito chat (D-110, D-111). */
class ProjectsAndIncognitoMigrationTest {
    private val databases = MigrationTestDatabases()

    @After
    fun closeConnections() {
        databases.closeAll()
    }

    private fun openVersion7WithData(): SQLiteConnection {
        val connection = databases.openVersion(7)
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
        val connection = openVersion7WithData()

        JonakiDatabase_AutoMigration_7_8_Impl().migrate(connection)

        assertEquals(
            listOf("t1|HIGH|null|0"),
            databases.queryStrings(connection, "SELECT id, thinkingLevel, projectId, incognito FROM threads"),
        )
        assertEquals(listOf("0"), databases.queryStrings(connection, "SELECT COUNT(*) FROM projects"))
    }

    @Test
    fun upgradedDatabaseHasTheSameTablesAsAFreshOne() {
        val upgraded = openVersion7WithData()
        JonakiDatabase_AutoMigration_7_8_Impl().migrate(upgraded)
        val fresh = databases.openVersion(8)
        MemorySearchIndex.create(fresh)

        assertEquals(databases.describeTables(fresh), databases.describeTables(upgraded))
    }
}
