package app.jonaki.core.storage

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs Room's generated 9 to 10 migration (project facts, D-135). */
class ProjectMemoryMigrationTest {
    private val databases = MigrationTestDatabases()

    @After
    fun closeConnections() {
        databases.closeAll()
    }

    private fun openVersion9WithFacts(): SQLiteConnection {
        val connection = databases.openVersion(9)
        MemorySearchIndex.create(connection)
        connection.execSQL(
            "INSERT INTO threads (id, title, createdAtMillis, updatedAtMillis, webSearchEnabled, toolsAllowedForThread, " +
                "disabledSkills, incognito) VALUES ('t1', 'Thesis', 1, 2, 1, '', '', 0)",
        )
        connection.execSQL(
            "INSERT INTO memories (id, scope, threadId, text, pinned, origin, pendingReview, createdAtMillis, updatedAtMillis) " +
                "VALUES (1, 'global', NULL, 'নাম রিয়াদ', 1, 'user', 0, 1, 1), (2, 'thread', 't1', 'Uses LaTeX', 0, 'tool', 0, 1, 1)",
        )
        return connection
    }

    @Test
    fun oldFactsKeepTheirScopeAndHaveNoProject() {
        val connection = openVersion9WithFacts()

        JonakiDatabase_AutoMigration_9_10_Impl().migrate(connection)

        assertEquals(
            listOf("1|global|null|null|নাম রিয়াদ", "2|thread|t1|null|Uses LaTeX"),
            databases.queryStrings(connection, "SELECT id, scope, threadId, projectId, text FROM memories ORDER BY id"),
        )
    }

    @Test
    fun theSearchIndexStillFindsOldFactsAfterTheMigration() {
        val connection = openVersion9WithFacts()

        JonakiDatabase_AutoMigration_9_10_Impl().migrate(connection)

        assertEquals(
            listOf("2"),
            databases.queryStrings(connection, "SELECT rowid FROM ${MemorySearchIndex.TABLE} WHERE ${MemorySearchIndex.TABLE} MATCH '\"LaTeX\"'"),
        )
    }

    @Test
    fun upgradedDatabaseHasTheSameTablesAsAFreshOne() {
        val upgraded = openVersion9WithFacts()
        JonakiDatabase_AutoMigration_9_10_Impl().migrate(upgraded)
        val fresh = databases.openVersion(10)
        MemorySearchIndex.create(fresh)

        assertEquals(databases.describeTables(fresh), databases.describeTables(upgraded))
    }
}
