package app.jonaki.core.storage

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs Room's generated 2 to 3 migration on the JVM build of the bundled
 * SQLite (the same SQLite 3.46 the app ships, spike S-1), then checks that
 * the upgraded database matches a fresh version 3 and that the FTS5 index
 * follows inserts, edits, deletes and thread deletion.
 */
class MemoryMigrationTest {
    private val databases = MigrationTestDatabases()

    @After
    fun closeConnections() {
        databases.closeAll()
    }

    private fun openVersion(version: Int): SQLiteConnection = databases.openVersion(version)

    private fun queryStrings(connection: SQLiteConnection, sql: String, vararg arguments: String): List<String> =
        databases.queryStrings(connection, sql, *arguments)

    private fun describeTables(connection: SQLiteConnection): Map<String, List<String>> = databases.describeTables(connection)

    private fun openVersion2WithOneThread(): SQLiteConnection {
        val connection = openVersion(2)
        connection.execSQL(
            "INSERT INTO threads (id, title, createdAtMillis, updatedAtMillis, webSearchEnabled, toolsAllowedForThread, modelKey) " +
                "VALUES ('t1', 'Thesis plan', 1, 2, 1, '', 'openrouter:z-ai/glm-5.3')",
        )
        connection.execSQL(
            "INSERT INTO messages (id, threadId, position, role, text, toolCallsJson, toolCallId, isComplete, createdAtMillis, costUsd) " +
                "VALUES ('m1', 't1', 0, 'USER', 'আমার থিসিস', '[]', NULL, 1, 1, 0.002)",
        )
        return connection
    }

    private fun migrateTo3(connection: SQLiteConnection) {
        JonakiDatabase_AutoMigration_2_3_Impl().migrate(connection)
    }

    private fun insertMemory(connection: SQLiteConnection, id: Long, threadId: String?, text: String) {
        connection.prepare(
            "INSERT INTO memories (id, scope, threadId, text, pinned, sourceMessageId, origin, pendingReview, " +
                "createdAtMillis, updatedAtMillis, lastUsedAtMillis) VALUES (?, ?, ?, ?, 0, NULL, 'tool', 0, 1, 1, NULL)",
        ).use { statement ->
            statement.bindLong(1, id)
            statement.bindText(2, if (threadId == null) MemoryScope.GLOBAL else MemoryScope.THREAD)
            if (threadId == null) statement.bindNull(3) else statement.bindText(3, threadId)
            statement.bindText(4, text)
            statement.step()
        }
    }

    private fun matchIds(connection: SQLiteConnection, query: String, threadId: String): List<String> {
        val sql = MemorySearchIndex.MATCH_SEARCH
            .replace(":phrase", "?").replace(":threadId", "?").replace(":limit", "50")
        return queryStrings(connection, "SELECT id FROM ($sql)", MemorySearchIndex.matchPhrase(query), threadId)
    }

    private fun likeIds(connection: SQLiteConnection, query: String, threadId: String): List<String> {
        val sql = MemorySearchIndex.LIKE_SEARCH
            .replace(":pattern", "?").replace(":threadId", "?").replace(":limit", "50")
        return queryStrings(connection, "SELECT id FROM ($sql)", MemorySearchIndex.likePattern(query), threadId)
    }

    @Test
    fun threadsAndMessagesSurviveTheMigration() {
        val connection = openVersion2WithOneThread()

        migrateTo3(connection)

        assertEquals(
            listOf("t1|Thesis plan|openrouter:z-ai/glm-5.3|null"),
            queryStrings(connection, "SELECT id, title, modelKey, memoryExtractedUpToPosition FROM threads"),
        )
        assertEquals(listOf("m1|আমার থিসিস|0.002"), queryStrings(connection, "SELECT id, text, costUsd FROM messages"))
    }

    @Test
    fun upgradedDatabaseHasTheSameTablesAsAFreshOne() {
        val upgraded = openVersion2WithOneThread()
        migrateTo3(upgraded)
        val fresh = openVersion(3)
        MemorySearchIndex.create(fresh)

        assertEquals(describeTables(fresh), describeTables(upgraded))
        assertTrue(describeTables(upgraded).containsKey("memories_fts"))
    }

    @Test
    fun indexFindsBanglaAndEnglishFragmentsOfGlobalAndOwnThreadFacts() {
        val connection = openVersion2WithOneThread()
        migrateTo3(connection)
        connection.execSQL("INSERT INTO threads (id, title, createdAtMillis, updatedAtMillis, webSearchEnabled, toolsAllowedForThread) VALUES ('t2', '', 1, 1, 1, '')")
        insertMemory(connection, 1, null, "User prefers DeepSeek for coding")
        insertMemory(connection, 2, "t1", "থিসিস সুপারভাইজার ড. রহমান")
        insertMemory(connection, 3, "t2", "Thesis about deep rivers")

        assertEquals(listOf("1"), matchIds(connection, "dee", "t1"))
        assertEquals(listOf("2"), matchIds(connection, "থিস", "t1"))
        assertEquals(listOf("1", "3"), matchIds(connection, "dee", "t2").sorted())
    }

    @Test
    fun queriesShorterThanThreeCharactersUseLike() {
        val connection = openVersion2WithOneThread()
        migrateTo3(connection)
        insertMemory(connection, 1, "t1", "ঢাকা থেকে Sylhet")
        insertMemory(connection, 2, "t1", "100% sure")

        assertEquals(false, MemorySearchIndex.usesMatch("ঢা"))
        assertEquals(listOf("1"), likeIds(connection, "ঢা", "t1"))
        assertEquals(listOf("2"), likeIds(connection, "%", "t1"))
        assertEquals(emptyList<String>(), matchIds(connection, "ঢা", "t1"))
    }

    @Test
    fun quotesInAQueryAreSearchedLiterally() {
        val connection = openVersion2WithOneThread()
        migrateTo3(connection)
        insertMemory(connection, 1, "t1", "Call it \"Project Jonaki\" in letters")

        assertEquals(listOf("1"), matchIds(connection, "\"Project", "t1"))
        assertEquals(emptyList<String>(), matchIds(connection, "OR AND", "t1"))
    }

    @Test
    fun editedTextIsFoundByItsNewWordsOnly() {
        val connection = openVersion2WithOneThread()
        migrateTo3(connection)
        insertMemory(connection, 1, "t1", "Train at 7 in the morning")

        connection.execSQL("UPDATE memories SET text = 'Bus at 9 in the evening' WHERE id = 1")

        assertEquals(emptyList<String>(), matchIds(connection, "Train", "t1"))
        assertEquals(listOf("1"), matchIds(connection, "evening", "t1"))
    }

    @Test
    fun deletingAFactOrItsThreadRemovesItFromTheIndex() {
        val connection = openVersion2WithOneThread()
        migrateTo3(connection)
        insertMemory(connection, 1, null, "Global fact about rivers")
        insertMemory(connection, 2, "t1", "Thread fact about rivers")

        connection.execSQL("DELETE FROM memories WHERE id = 1")
        connection.execSQL("DELETE FROM threads WHERE id = 't1'")

        assertEquals(listOf("0"), queryStrings(connection, "SELECT COUNT(*) FROM memories"))
        // The index must be empty too; an integrity check fails if it holds a deleted row.
        connection.execSQL("INSERT INTO memories_fts(memories_fts) VALUES ('integrity-check')")
        assertEquals(listOf("0"), queryStrings(connection, "SELECT COUNT(*) FROM memories_fts"))
    }

    @Test
    fun compactionsGoWithTheirThread() {
        val connection = openVersion2WithOneThread()
        migrateTo3(connection)
        connection.execSQL(
            "INSERT INTO compactions (id, threadId, upToPosition, summaryText, createdAtMillis, model, costUsd) " +
                "VALUES ('c1', 't1', 40, 'Goal: thesis', 5, NULL, NULL)",
        )

        connection.execSQL("DELETE FROM threads WHERE id = 't1'")

        assertEquals(listOf("0"), queryStrings(connection, "SELECT COUNT(*) FROM compactions"))
    }
}
