package app.jonaki.core.storage

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageSearchIndexTest {
    private val databases = MigrationTestDatabases()

    @After
    fun closeConnections() {
        databases.closeAll()
    }

    private fun openDatabase(): SQLiteConnection = databases.openVersion(12)

    private fun addThread(connection: SQLiteConnection, id: String, title: String = "Title $id", incognito: Boolean = false) {
        connection.execSQL(
            "INSERT INTO threads (id, title, createdAtMillis, updatedAtMillis, webSearchEnabled, toolsAllowedForThread, incognito) " +
                "VALUES ('$id', '$title', 1, 1, 0, '', ${if (incognito) 1 else 0})",
        )
    }

    private fun addMessage(
        connection: SQLiteConnection,
        id: String,
        threadId: String,
        text: String,
        role: String = "USER",
        isComplete: Boolean = true,
        position: Int = id.filter { character -> character.isDigit() }.toIntOrNull() ?: 0,
    ) {
        connection.prepare(
            "INSERT INTO messages (id, threadId, position, role, text, toolCallsJson, isComplete, createdAtMillis) " +
                "VALUES (?, ?, ?, ?, ?, '[]', ?, ?)",
        ).use { statement ->
            statement.bindText(1, id)
            statement.bindText(2, threadId)
            statement.bindLong(3, position.toLong())
            statement.bindText(4, role)
            statement.bindText(5, text)
            statement.bindLong(6, if (isComplete) 1 else 0)
            statement.bindLong(7, position.toLong())
            statement.step()
        }
    }

    /** The ids of the messages that [words] find, best first; [threadId] null searches every thread. */
    private fun search(connection: SQLiteConnection, words: String, threadId: String? = null): List<String> {
        val match = checkNotNull(FtsQuery.anyWordOf(words))
        // SQLite numbers named parameters by first appearance: :match, :threadId, :limit, :offset.
        return connection.prepare(MessageSearchIndex.SEARCH).use { statement ->
            statement.bindText(1, match)
            if (threadId == null) statement.bindNull(2) else statement.bindText(2, threadId)
            statement.bindLong(3, 50)
            statement.bindLong(4, 0)
            val ids = mutableListOf<String>()
            while (statement.step()) {
                ids += statement.getText(0)
            }
            ids
        }
    }

    private fun indexedRowCount(connection: SQLiteConnection): String =
        databases.queryStrings(connection, "SELECT COUNT(*) FROM ${MessageSearchIndex.MAP_TABLE}").single()

    @Test
    fun aFreshDatabaseFindsMessagesInBanglaAndEnglish() {
        val connection = openDatabase()
        MessageSearchIndex.ensure(connection)
        addThread(connection, "t1")
        addMessage(connection, "m1", "t1", "আমার থিসিসের সুপারভাইজার ড. রহমান")
        addMessage(connection, "m2", "t1", "The thesis deadline is in December", role = "ASSISTANT")
        addMessage(connection, "m3", "t1", "Something about trains")

        assertEquals(listOf("m1"), search(connection, "থিসিস"))
        assertEquals(listOf("m2"), search(connection, "deadline"))
        assertEquals(setOf("m1", "m2"), search(connection, "থিসিসের deadline").toSet())
    }

    @Test
    fun anOrQueryPutsTheMessageWithMoreOfTheWordsFirst() {
        val connection = openDatabase()
        MessageSearchIndex.ensure(connection)
        addThread(connection, "t1")
        addMessage(connection, "m1", "t1", "We talked about the weather and a train yesterday")
        addMessage(connection, "m2", "t1", "We talked about the cheap train and the tickets yesterday")
        addMessage(connection, "m3", "t1", "We talked about the cheap train tickets to Sylhet yesterday")

        val found = search(connection, "train tickets sylhet cheap")

        assertEquals(3, found.size)
        assertEquals("m3", found.first())
    }

    @Test
    fun aDatabaseOpenedForTheFirstTimeAfterTheUpgradeIndexesOldMessages() {
        val connection = openDatabase()
        addThread(connection, "t1")
        addMessage(connection, "m1", "t1", "ঢাকা থেকে সিলেটের ট্রেন")
        addMessage(connection, "m2", "t1", "Old answer about trains", role = "ASSISTANT")
        addMessage(connection, "m3", "t1", "tool output with trains", role = "TOOL")
        addMessage(connection, "m4", "t1", "half streamed trains", role = "ASSISTANT", isComplete = false)
        addMessage(connection, "m5", "t1", "   ")

        MessageSearchIndex.ensure(connection)

        assertEquals(listOf("m1"), search(connection, "ট্রেন"))
        assertEquals(listOf("m2"), search(connection, "trains"))
        assertEquals("2", indexedRowCount(connection))
    }

    @Test
    fun openingTheDatabaseAgainKeepsTheIndexWithoutDuplicates() {
        val connection = openDatabase()
        MessageSearchIndex.ensure(connection)
        addThread(connection, "t1")
        addMessage(connection, "m1", "t1", "thesis chapter")

        MessageSearchIndex.ensure(connection)

        assertEquals(listOf("m1"), search(connection, "thesis"))
        assertEquals("1", indexedRowCount(connection))
    }

    @Test
    fun aMissingTriggerBringsARebuildSoMessagesAddedMeanwhileAreFound() {
        val connection = openDatabase()
        MessageSearchIndex.ensure(connection)
        addThread(connection, "t1")
        addMessage(connection, "m1", "t1", "before")
        // What a migration that rebuilt the messages table leaves: no triggers, rows copied over.
        connection.execSQL("DROP TRIGGER message_search_after_insert")
        addMessage(connection, "m2", "t1", "meanwhile word")

        MessageSearchIndex.ensure(connection)

        assertEquals(listOf("m2"), search(connection, "meanwhile"))
        assertEquals(listOf("m1"), search(connection, "before"))
        assertEquals("2", indexedRowCount(connection))
    }

    @Test
    fun aMessageIsFoundOnlyOnceItIsComplete() {
        val connection = openDatabase()
        MessageSearchIndex.ensure(connection)
        addThread(connection, "t1")
        addMessage(connection, "m1", "t1", "partial streamed answer", role = "ASSISTANT", isComplete = false)
        assertEquals(emptyList<String>(), search(connection, "streamed"))

        connection.execSQL("UPDATE messages SET text = 'full streamed answer about rivers', isComplete = 1 WHERE id = 'm1'")

        assertEquals(listOf("m1"), search(connection, "streamed"))
        assertEquals(emptyList<String>(), search(connection, "partial"))
    }

    @Test
    fun updatingTheTextOfAnIndexedMessageReplacesWhatIsFound() {
        val connection = openDatabase()
        MessageSearchIndex.ensure(connection)
        addThread(connection, "t1")
        addMessage(connection, "m1", "t1", "first wording about rivers")

        connection.execSQL("UPDATE messages SET text = 'second wording about mountains' WHERE id = 'm1'")

        assertEquals(emptyList<String>(), search(connection, "rivers"))
        assertEquals(listOf("m1"), search(connection, "mountains"))
        assertEquals("1", indexedRowCount(connection))
    }

    @Test
    fun updatingAnotherColumnLeavesTheIndexAlone() {
        val connection = openDatabase()
        MessageSearchIndex.ensure(connection)
        addThread(connection, "t1")
        addMessage(connection, "m1", "t1", "wording about rivers")

        connection.execSQL("UPDATE messages SET reasoningText = 'thinking', costUsd = 0.5 WHERE id = 'm1'")

        assertEquals(listOf("m1"), search(connection, "rivers"))
    }

    @Test
    fun deletingAMessageRemovesItFromTheIndex() {
        val connection = openDatabase()
        MessageSearchIndex.ensure(connection)
        addThread(connection, "t1")
        addMessage(connection, "m1", "t1", "wording about rivers")
        addMessage(connection, "m2", "t1", "other wording about rivers")

        connection.execSQL("DELETE FROM messages WHERE id = 'm1'")

        assertEquals(listOf("m2"), search(connection, "rivers"))
        assertEquals("1", indexedRowCount(connection))
    }

    @Test
    fun deletingAThreadRemovesItsMessagesFromTheIndex() {
        val connection = openDatabase()
        MessageSearchIndex.ensure(connection)
        addThread(connection, "t1")
        addThread(connection, "t2")
        addMessage(connection, "m1", "t1", "wording about rivers")
        addMessage(connection, "m2", "t2", "wording about rivers too")

        connection.execSQL("DELETE FROM threads WHERE id = 't1'")

        assertEquals(listOf("m2"), search(connection, "rivers"))
        assertEquals("1", indexedRowCount(connection))
    }

    @Test
    fun theIndexSurvivesAVacuumThatRenumbersMessageRowIds() {
        val connection = openDatabase()
        MessageSearchIndex.ensure(connection)
        addThread(connection, "t1")
        for (number in 1..6) {
            addMessage(connection, "m$number", "t1", "message number$number about rivers")
        }
        connection.execSQL("DELETE FROM messages WHERE id IN ('m1', 'm2', 'm3')")

        connection.execSQL("VACUUM")

        assertEquals(listOf("m5"), search(connection, "number5"))
        assertEquals(setOf("m4", "m5", "m6"), search(connection, "rivers").toSet())
    }

    @Test
    fun incognitoThreadsAreNeverFound() {
        val connection = openDatabase()
        MessageSearchIndex.ensure(connection)
        addThread(connection, "regular")
        addThread(connection, "secret", incognito = true)
        addMessage(connection, "m1", "regular", "a note about rivers")
        addMessage(connection, "m2", "secret", "a secret note about rivers")

        assertEquals(listOf("m1"), search(connection, "rivers"))
        assertEquals(emptyList<String>(), search(connection, "rivers", threadId = "secret"))
    }

    @Test
    fun aKeptIncognitoThreadBecomesSearchable() {
        val connection = openDatabase()
        MessageSearchIndex.ensure(connection)
        addThread(connection, "secret", incognito = true)
        addMessage(connection, "m1", "secret", "a secret note about rivers")

        connection.execSQL("UPDATE threads SET incognito = 0 WHERE id = 'secret'")

        assertEquals(listOf("m1"), search(connection, "rivers"))
    }

    @Test
    fun theThreadScopeSearchesOnlyThatThread() {
        val connection = openDatabase()
        MessageSearchIndex.ensure(connection)
        addThread(connection, "t1")
        addThread(connection, "t2")
        addMessage(connection, "m1", "t1", "a note about rivers")
        addMessage(connection, "m2", "t2", "another note about rivers")

        assertEquals(listOf("m2"), search(connection, "rivers", threadId = "t2"))
        assertEquals(setOf("m1", "m2"), search(connection, "rivers").toSet())
    }

    @Test
    fun theNeighbourQueriesSkipToolMessagesAndIncompleteOnes() {
        val connection = openDatabase()
        MessageSearchIndex.ensure(connection)
        addThread(connection, "t1")
        addMessage(connection, "m1", "t1", "question", position = 1)
        addMessage(connection, "m2", "t1", "tool output", role = "TOOL", position = 2)
        addMessage(connection, "m3", "t1", "answer", role = "ASSISTANT", position = 3)
        addMessage(connection, "m4", "t1", "half", role = "ASSISTANT", isComplete = false, position = 4)

        assertEquals(
            listOf("question"),
            databases.queryStrings(connection, MessageSearchIndex.PREVIOUS_MESSAGE.replace(":threadId", "'t1'").replace(":position", "3")),
        )
        assertEquals(
            emptyList<String>(),
            databases.queryStrings(connection, MessageSearchIndex.NEXT_MESSAGE.replace(":threadId", "'t1'").replace(":position", "3")),
        )
    }

    @Test
    fun theCountQueryCountsEveryMatch() {
        val connection = openDatabase()
        MessageSearchIndex.ensure(connection)
        addThread(connection, "t1")
        for (number in 1..7) {
            addMessage(connection, "m$number", "t1", "note $number about rivers")
        }
        val count = connection.prepare(MessageSearchIndex.COUNT).use { statement ->
            statement.bindText(1, checkNotNull(FtsQuery.anyWordOf("rivers")))
            statement.bindNull(2)
            statement.step()
            statement.getLong(0)
        }

        assertEquals(7L, count)
    }
}
