package app.jonaki.core.storage

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Which facts a thread sees once projects have their own facts (D-135), on the version 12 schema. */
class ProjectMemoryQueriesTest {
    private val databases = MigrationTestDatabases()
    private lateinit var connection: SQLiteConnection

    @Before
    fun openDatabase() {
        connection = databases.openVersion(12)
        insertThread("thesis-chat")
        insertThread("other-chat")
        insertFact(1, threadId = null, projectId = null, text = "Name is Riad")
        insertFact(2, threadId = null, projectId = "thesis", text = "Supervisor wants APA")
        insertFact(3, threadId = null, projectId = "garden", text = "Tomatoes in bed two")
        insertFact(4, threadId = "thesis-chat", projectId = null, text = "Chapter two first")
        insertFact(5, threadId = "other-chat", projectId = null, text = "Likes tea")
    }

    @After
    fun closeConnections() {
        databases.closeAll()
    }

    private fun insertThread(id: String) {
        connection.execSQL(
            "INSERT INTO threads (id, title, createdAtMillis, updatedAtMillis, webSearchEnabled, toolsAllowedForThread, " +
                "disabledSkills, instructions, incognito) VALUES ('$id', '$id', 0, 0, 1, '', '', '', 0)",
        )
    }

    private fun insertFact(id: Long, threadId: String?, projectId: String?, text: String) {
        val scope = when {
            threadId != null -> MemoryScope.THREAD
            projectId != null -> MemoryScope.PROJECT
            else -> MemoryScope.GLOBAL
        }
        connection.execSQL(
            "INSERT INTO memories (id, scope, threadId, projectId, text, pinned, origin, pendingReview, createdAtMillis, updatedAtMillis) " +
                "VALUES ($id, '$scope', ${quoted(threadId)}, ${quoted(projectId)}, '$text', 0, 'user', 0, 0, 0)",
        )
    }

    private fun quoted(value: String?): String = if (value == null) "NULL" else "'$value'"

    /** The ids [MemorySearchIndex.VISIBLE_FROM_THREAD] lets one thread see. */
    private fun visibleIds(threadId: String, projectId: String?): List<String> {
        val condition = MemorySearchIndex.VISIBLE_FROM_THREAD
            .replace(":threadId", "?1")
            .replace(":projectId", quoted(projectId))
        return databases.queryStrings(connection, "SELECT id FROM memories WHERE $condition ORDER BY id", threadId)
    }

    @Test
    fun aProjectThreadSeesGlobalItsProjectsAndItsOwnFacts() {
        assertEquals(listOf("1", "2", "4"), visibleIds("thesis-chat", "thesis"))
    }

    @Test
    fun aThreadWithoutAProjectSeesNoProjectFacts() {
        assertEquals(listOf("1", "5"), visibleIds("other-chat", null))
    }

    @Test
    fun theLikeSearchKeepsAnotherProjectsFactsOut() {
        val sql = MemorySearchIndex.LIKE_SEARCH
            .replace(":pattern", "'%e%'")
            .replace(":threadId", "?1")
            .replace(":projectId", "'thesis'")
            .replace(":limit", "50")
            .replace("SELECT *", "SELECT id")
        assertEquals(setOf("1", "2", "4"), databases.queryStrings(connection, sql, "thesis-chat").toSet())
    }
}
