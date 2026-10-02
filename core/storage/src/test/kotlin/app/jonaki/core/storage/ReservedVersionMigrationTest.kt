package app.jonaki.core.storage

import androidx.sqlite.execSQL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Runs Room's generated 6 to 7 migration. Version 7 is held for the v0.8.0
 * branch (D-115), so until that lands the step must leave data and tables
 * exactly as they were.
 */
class ReservedVersionMigrationTest {
    private val databases = MigrationTestDatabases()

    @After
    fun closeConnections() {
        databases.closeAll()
    }

    @Test
    fun theReservedStepKeepsDataAndTables() {
        val upgraded = databases.openVersion(6)
        MemorySearchIndex.create(upgraded)
        upgraded.execSQL(
            "INSERT INTO threads (id, title, createdAtMillis, updatedAtMillis, webSearchEnabled, toolsAllowedForThread, " +
                "modelKey, memoryExtractedUpToPosition, disabledSkills, thinkingLevel) " +
                "VALUES ('t1', 'Trip', 1, 2, 1, '', 'openrouter:x', 0, '', 'HIGH')",
        )

        JonakiDatabase_AutoMigration_6_7_Impl().migrate(upgraded)

        assertEquals(listOf("t1|HIGH"), databases.queryStrings(upgraded, "SELECT id, thinkingLevel FROM threads"))
        val fresh = databases.openVersion(7)
        MemorySearchIndex.create(fresh)
        assertEquals(databases.describeTables(fresh), databases.describeTables(upgraded))
    }
}
