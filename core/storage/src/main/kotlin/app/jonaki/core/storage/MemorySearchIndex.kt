package app.jonaki.core.storage

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * The FTS5 trigram index over memory texts (D-009, spike S-1). Room's
 * annotations only know FTS4, so the virtual table and the triggers that keep
 * it in step with `memories` are plain SQL, run when the database is created
 * and when version 2 migrates to 3.
 *
 * The index is an external-content table: it stores no copy of the text and
 * reads it from `memories` by id.
 */
object MemorySearchIndex {
    const val TABLE = "memories_fts"

    /** Trigram matching needs at least three characters (S-1); shorter queries use [LIKE_SEARCH]. */
    const val MINIMUM_MATCH_LENGTH = 3

    val createStatements: List<String> = listOf(
        "CREATE VIRTUAL TABLE IF NOT EXISTS $TABLE USING fts5(" +
            "text, content='memories', content_rowid='id', tokenize='trigram')",
        "CREATE TRIGGER IF NOT EXISTS memories_after_insert AFTER INSERT ON memories BEGIN " +
            "INSERT INTO $TABLE(rowid, text) VALUES (new.id, new.text); END",
        "CREATE TRIGGER IF NOT EXISTS memories_after_delete AFTER DELETE ON memories BEGIN " +
            "INSERT INTO $TABLE($TABLE, rowid, text) VALUES ('delete', old.id, old.text); END",
        "CREATE TRIGGER IF NOT EXISTS memories_after_update AFTER UPDATE OF text ON memories BEGIN " +
            "INSERT INTO $TABLE($TABLE, rowid, text) VALUES ('delete', old.id, old.text); " +
            "INSERT INTO $TABLE(rowid, text) VALUES (new.id, new.text); END",
    )

    /**
     * Facts visible from one thread (its own and the global ones) whose text
     * contains the query. The query is bound as a quoted FTS5 phrase, see [matchPhrase].
     */
    const val MATCH_SEARCH =
        "SELECT memories.* FROM memories JOIN $TABLE ON memories.id = $TABLE.rowid " +
            "WHERE $TABLE MATCH :phrase AND memories.pendingReview = 0 " +
            "AND (memories.threadId IS NULL OR memories.threadId = :threadId) " +
            "ORDER BY memories.pinned DESC, memories.updatedAtMillis DESC LIMIT :limit"

    /** The same search for one- and two-character queries, which trigrams cannot match. */
    const val LIKE_SEARCH =
        "SELECT * FROM memories WHERE text LIKE :pattern ESCAPE '\\' AND pendingReview = 0 " +
            "AND (threadId IS NULL OR threadId = :threadId) " +
            "ORDER BY pinned DESC, updatedAtMillis DESC LIMIT :limit"

    fun create(connection: SQLiteConnection) {
        for (statement in createStatements) {
            connection.execSQL(statement)
        }
    }

    /** An FTS5 phrase: the text in double quotes, inner quotes doubled, so no query syntax is read from it. */
    fun matchPhrase(query: String): String = "\"" + query.replace("\"", "\"\"") + "\""

    /** A LIKE pattern for "contains", with %, _ and the escape character escaped. */
    fun likePattern(query: String): String {
        val escaped = query
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return "%$escaped%"
    }

    /** Length in code points, so that a Bangla vowel sign counts as one character, as the trigram tokenizer counts it. */
    fun usesMatch(query: String): Boolean = query.codePointCount(0, query.length) >= MINIMUM_MATCH_LENGTH
}
