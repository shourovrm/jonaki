package app.jonaki.core.storage

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * The FTS5 trigram index over chat messages, for the search_chats tool
 * (spike results in spikes/message-index/results.md). Room does not know it:
 * it is created outside Room's versioning by [ensure], which the database
 * callback runs every time the database is created or opened.
 *
 * Design: a contentless FTS5 table (it keeps the trigrams but no text, so the
 * text is stored only once, in `messages`) plus a map from the FTS5 row id to
 * the message id. `messages.id` is TEXT, so `messages` has only an implicit
 * rowid, which SQLite may renumber on VACUUM; the map's INTEGER PRIMARY KEY
 * is stable. A view as the content source would also work, but a view that
 * names `messages` makes SQLite refuse the table rebuild that Room's
 * migrations do, so there is none.
 *
 * Only complete user and assistant messages with text are indexed: assistant
 * text changes while it streams, and the index would rewrite itself at every
 * chunk. The triggers keep the index right on insert, update and delete, and
 * a deleted thread cascades to its messages and so to the triggers.
 */
object MessageSearchIndex {
    const val TABLE = "message_search"
    const val MAP_TABLE = "message_search_map"

    private const val INSERT_TRIGGER = "message_search_after_insert"
    private const val DELETE_TRIGGER = "message_search_after_delete"
    private const val UPDATE_TRIGGER = "message_search_after_update"

    /** Whether the row [prefix] ("new" or "old") belongs in the index. */
    private fun isIndexed(prefix: String): String =
        "($prefix.role IN ('USER', 'ASSISTANT') AND $prefix.isComplete = 1 " +
            "AND trim($prefix.text, ' ' || char(9, 10, 13)) <> '')"

    private val tableStatements = listOf(
        "CREATE TABLE IF NOT EXISTS $MAP_TABLE (" +
            "indexRowId INTEGER PRIMARY KEY, messageId TEXT NOT NULL UNIQUE)",
        "CREATE VIRTUAL TABLE IF NOT EXISTS $TABLE USING fts5(" +
            "text, content='', contentless_delete=1, tokenize='trigram')",
    )

    private val triggerStatements = listOf(
        "CREATE TRIGGER IF NOT EXISTS $INSERT_TRIGGER AFTER INSERT ON messages WHEN ${isIndexed("new")} BEGIN " +
            "INSERT INTO $MAP_TABLE(messageId) VALUES (new.id); " +
            "INSERT INTO $TABLE(rowid, text) SELECT indexRowId, new.text FROM $MAP_TABLE WHERE messageId = new.id; END",
        "CREATE TRIGGER IF NOT EXISTS $DELETE_TRIGGER AFTER DELETE ON messages WHEN ${isIndexed("old")} BEGIN " +
            "DELETE FROM $TABLE WHERE rowid IN (SELECT indexRowId FROM $MAP_TABLE WHERE messageId = old.id); " +
            "DELETE FROM $MAP_TABLE WHERE messageId = old.id; END",
        // Remove the old entry, then add the new one if the row still belongs in the index.
        "CREATE TRIGGER IF NOT EXISTS $UPDATE_TRIGGER AFTER UPDATE OF text, role, isComplete ON messages " +
            "WHEN ${isIndexed("old")} OR ${isIndexed("new")} BEGIN " +
            "DELETE FROM $TABLE WHERE rowid IN (SELECT indexRowId FROM $MAP_TABLE WHERE messageId = old.id); " +
            "DELETE FROM $MAP_TABLE WHERE messageId = old.id; " +
            "INSERT INTO $MAP_TABLE(messageId) SELECT new.id WHERE ${isIndexed("new")}; " +
            "INSERT INTO $TABLE(rowid, text) SELECT indexRowId, new.text FROM $MAP_TABLE WHERE messageId = new.id; END",
    )

    private val dropStatements = listOf(
        "DROP TRIGGER IF EXISTS $INSERT_TRIGGER",
        "DROP TRIGGER IF EXISTS $DELETE_TRIGGER",
        "DROP TRIGGER IF EXISTS $UPDATE_TRIGGER",
        "DROP TABLE IF EXISTS $TABLE",
        "DROP TABLE IF EXISTS $MAP_TABLE",
    )

    private val backfillStatements = listOf(
        "INSERT INTO $MAP_TABLE(messageId) SELECT id FROM messages WHERE ${isIndexed("messages")} ORDER BY createdAtMillis",
        "INSERT INTO $TABLE(rowid, text) SELECT $MAP_TABLE.indexRowId, messages.text " +
            "FROM $MAP_TABLE JOIN messages ON messages.id = $MAP_TABLE.messageId",
    )

    /**
     * Creates what is missing. When the index table or the insert trigger is
     * missing, the index is dropped and rebuilt from `messages`: that is the
     * first open after the app gained search, and also the open after a
     * migration that rebuilt `messages` and so dropped its triggers. The
     * triggers are created last, so a crash during the rebuild leaves the
     * insert trigger missing and the next open rebuilds again.
     */
    fun ensure(connection: SQLiteConnection) {
        val needsRebuild = !exists(connection, "table", TABLE) || !exists(connection, "trigger", INSERT_TRIGGER)
        if (needsRebuild) {
            dropStatements.forEach { statement -> connection.execSQL(statement) }
        }
        tableStatements.forEach { statement -> connection.execSQL(statement) }
        if (needsRebuild && exists(connection, "table", "messages")) {
            backfillStatements.forEach { statement -> connection.execSQL(statement) }
        }
        triggerStatements.forEach { statement -> connection.execSQL(statement) }
    }

    private fun exists(connection: SQLiteConnection, type: String, name: String): Boolean =
        connection.prepare("SELECT 1 FROM sqlite_master WHERE type = ? AND name = ?").use { statement ->
            statement.bindText(1, type)
            statement.bindText(2, name)
            statement.step()
        }

    /**
     * The best matches of an FTS5 query ([FtsQuery.anyWordOf]) in messages of
     * regular threads, best first. A null :threadId searches every thread; the
     * incognito filter always applies (D-111). The parameters are :match,
     * :threadId, :limit and :offset.
     */
    const val SEARCH =
        "SELECT messages.id AS messageId, messages.threadId AS threadId, messages.position AS position, " +
            "messages.role AS role, messages.text AS text, messages.createdAtMillis AS createdAtMillis, " +
            "threads.title AS threadTitle " +
            "FROM $TABLE JOIN $MAP_TABLE ON $MAP_TABLE.indexRowId = $TABLE.rowid " +
            "JOIN messages ON messages.id = $MAP_TABLE.messageId " +
            "JOIN threads ON threads.id = messages.threadId " +
            "WHERE $TABLE MATCH :match AND threads.incognito = 0 " +
            "AND (:threadId IS NULL OR messages.threadId = :threadId) " +
            "ORDER BY bm25($TABLE), messages.createdAtMillis DESC LIMIT :limit OFFSET :offset"

    /** How many messages [SEARCH] would find without its limit; the parameters are :match and :threadId. */
    const val COUNT =
        "SELECT COUNT(*) FROM $TABLE JOIN $MAP_TABLE ON $MAP_TABLE.indexRowId = $TABLE.rowid " +
            "JOIN messages ON messages.id = $MAP_TABLE.messageId " +
            "JOIN threads ON threads.id = messages.threadId " +
            "WHERE $TABLE MATCH :match AND threads.incognito = 0 " +
            "AND (:threadId IS NULL OR messages.threadId = :threadId)"

    /** The role and text of the nearest user or assistant message with text before a position in a thread; parameters :threadId and :position. */
    const val PREVIOUS_MESSAGE =
        "SELECT role, text FROM messages WHERE threadId = :threadId AND position < :position " +
            "AND role IN ('USER', 'ASSISTANT') AND isComplete = 1 AND trim(text, ' ' || char(9, 10, 13)) <> '' " +
            "ORDER BY position DESC LIMIT 1"

    /** The same after a position. */
    const val NEXT_MESSAGE =
        "SELECT role, text FROM messages WHERE threadId = :threadId AND position > :position " +
            "AND role IN ('USER', 'ASSISTANT') AND isComplete = 1 AND trim(text, ' ' || char(9, 10, 13)) <> '' " +
            "ORDER BY position ASC LIMIT 1"
}
