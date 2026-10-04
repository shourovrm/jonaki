package app.jonaki.core.storage

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs Room's generated 11 to 12 migration and its spec (keywords, superseded facts, prompt facts, guard notes). */
class KeywordsMigrationTest {
    private val databases = MigrationTestDatabases()

    @After
    fun closeConnections() {
        databases.closeAll()
    }

    private fun openVersion11WithFacts(): SQLiteConnection {
        val connection = databases.openVersion(11)
        MemorySearchIndex.create(connection)
        connection.execSQL(
            "INSERT INTO memories (id, scope, threadId, text, pinned, origin, pendingReview, createdAtMillis, updatedAtMillis) " +
                "VALUES (1, 'global', NULL, 'থিসিস জমা ১২ ডিসেম্বর', 0, 'user', 0, 1, 1), " +
                "(2, 'global', NULL, 'Uses LaTeX', 0, 'tool', 0, 1, 1)",
        )
        return connection
    }

    private fun migrate(connection: SQLiteConnection) {
        JonakiDatabase_AutoMigration_11_12_Impl().migrate(connection)
    }

    private fun matchingIds(connection: SQLiteConnection, word: String): List<String> = databases.queryStrings(
        connection,
        "SELECT rowid FROM ${MemorySearchIndex.TABLE} WHERE ${MemorySearchIndex.TABLE} MATCH ? ORDER BY rowid",
        MemorySearchIndex.matchPhrase(word),
    )

    @Test
    fun oldFactsGetEmptyKeywordsAndAreNotSuperseded() {
        val connection = openVersion11WithFacts()

        migrate(connection)

        assertEquals(
            listOf("1||null", "2||null"),
            databases.queryStrings(connection, "SELECT id, keywords, supersededAtMillis FROM memories ORDER BY id"),
        )
    }

    @Test
    fun theRebuiltIndexStillFindsOldFactsByTheirText() {
        val connection = openVersion11WithFacts()

        migrate(connection)

        assertEquals(listOf("1"), matchingIds(connection, "থিসিস"))
        assertEquals(listOf("2"), matchingIds(connection, "LaTeX"))
    }

    @Test
    fun aFactIsFoundByAKeywordInTheOtherScript() {
        val connection = openVersion11WithFacts()
        migrate(connection)

        connection.execSQL("UPDATE memories SET keywords = 'thesis deadline' WHERE id = 1")

        assertEquals(listOf("1"), matchingIds(connection, "thesis"))
    }

    @Test
    fun changedKeywordsAndDeletedFactsLeaveTheIndex() {
        val connection = openVersion11WithFacts()
        migrate(connection)
        connection.execSQL("UPDATE memories SET keywords = 'thesis' WHERE id = 1")

        connection.execSQL("UPDATE memories SET keywords = 'dissertation' WHERE id = 1")
        connection.execSQL("DELETE FROM memories WHERE id = 2")

        assertEquals(emptyList<String>(), matchingIds(connection, "thesis"))
        assertEquals(listOf("1"), matchingIds(connection, "dissertation"))
        assertEquals(emptyList<String>(), matchingIds(connection, "LaTeX"))
    }

    @Test
    fun upgradedDatabaseHasTheSameTablesAsAFreshOne() {
        val upgraded = openVersion11WithFacts()
        migrate(upgraded)
        val fresh = databases.openVersion(12)
        MemorySearchIndex.createWithKeywords(fresh)

        assertEquals(databases.describeTables(fresh), databases.describeTables(upgraded))
    }
}
