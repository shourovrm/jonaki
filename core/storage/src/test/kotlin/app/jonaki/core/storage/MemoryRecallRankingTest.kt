package app.jonaki.core.storage

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** The recall search on the version 12 schema: any word of the query, keywords, best match first. */
class MemoryRecallRankingTest {
    private val databases = MigrationTestDatabases()
    private lateinit var connection: SQLiteConnection

    @Before
    fun openDatabase() {
        connection = databases.openVersion(12)
        MemorySearchIndex.createWithKeywords(connection)
        connection.execSQL(
            "INSERT INTO threads (id, title, createdAtMillis, updatedAtMillis, webSearchEnabled, toolsAllowedForThread, " +
                "disabledSkills, instructions, incognito) VALUES ('chat', 'chat', 0, 0, 1, '', '', '', 0)",
        )
    }

    @After
    fun closeConnections() {
        databases.closeAll()
    }

    /** A global fact; the update time is the id, so a higher id is a newer fact. */
    private fun insertFact(
        id: Long,
        text: String,
        keywords: String = "",
        pinned: Boolean = false,
        pendingReview: Boolean = false,
        supersededAtMillis: Long? = null,
    ) {
        connection.prepare(
            "INSERT INTO memories (id, scope, threadId, text, keywords, pinned, origin, pendingReview, " +
                "supersededAtMillis, createdAtMillis, updatedAtMillis) VALUES (?, 'global', NULL, ?, ?, ?, 'user', ?, ?, 0, ?)",
        ).use { statement ->
            statement.bindLong(1, id)
            statement.bindText(2, text)
            statement.bindText(3, keywords)
            statement.bindLong(4, if (pinned) 1 else 0)
            statement.bindLong(5, if (pendingReview) 1 else 0)
            if (supersededAtMillis == null) statement.bindNull(6) else statement.bindLong(6, supersededAtMillis)
            statement.bindLong(7, id)
            statement.step()
        }
    }

    /** The ids the recall search returns for [query], best first, as [FtsQuery] and the DAO would run it. */
    private fun recalledIds(query: String): List<String> {
        val match = FtsQuery.anyWordOf(query)
        val sql = if (match != null) {
            MemorySearchIndex.MATCH_SEARCH.replace(":match", "?1")
        } else {
            MemorySearchIndex.LIKE_SEARCH.replace(":pattern", "?1")
        }.replace(":threadId", "'chat'").replace(":projectId", "NULL").replace(":limit", "50")
        val bound = match ?: MemorySearchIndex.likePattern(query)
        return databases.queryStrings(connection, "SELECT id FROM ($sql)", bound)
    }

    @Test
    fun theWordThesisFindsABanglaFactThroughItsKeywords() {
        insertFact(1, "থিসিস জমা ১২ ডিসেম্বর", keywords = "thesis submission deadline")
        insertFact(2, "পছন্দের চা দার্জিলিং")

        assertEquals(listOf("1"), recalledIds("thesis"))
    }

    @Test
    fun aTextMatchOutranksAKeywordOnlyMatch() {
        // The keyword-only fact is the newer one, so only the ranking can put the text match first.
        insertFact(1, "The thesis is about river erosion")
        insertFact(2, "থিসিস জমা ১২ ডিসেম্বর", keywords = "thesis")

        assertEquals(listOf("1", "2"), recalledIds("thesis"))
    }

    @Test
    fun aFactHoldingMoreOfTheWordsComesFirst() {
        insertFact(1, "Thesis chapter two is due Friday")
        insertFact(2, "Thesis supervisor is Dr Rahman")
        insertFact(3, "Supervisor prefers APA, chapter two first, thesis in LaTeX")

        val ids = recalledIds("thesis supervisor chapter")

        assertEquals("3", ids.first())
        assertEquals(setOf("1", "2", "3"), ids.toSet())
    }

    @Test
    fun aWordOrderDifferentFromTheFactStillMatches() {
        insertFact(1, "Deadline for the thesis is in December")

        assertEquals(listOf("1"), recalledIds("thesis deadline"))
    }

    @Test
    fun pinnedOnlyBreaksTiesBetweenEqualMatches() {
        insertFact(1, "Likes green tea", pinned = true)
        insertFact(2, "Likes green tea", pinned = false)
        insertFact(3, "Green is a colour the user likes in tea gardens and tea cups", pinned = true)

        assertEquals(listOf("1", "2"), recalledIds("green tea").take(2))
    }

    @Test
    fun factsWaitingForReviewAndSupersededFactsAreLeftOut() {
        insertFact(1, "Thesis topic is rivers")
        insertFact(2, "Thesis topic is lakes", pendingReview = true)
        insertFact(3, "Thesis topic was oceans", supersededAtMillis = 5)

        assertEquals(listOf("1"), recalledIds("thesis"))
    }

    @Test
    fun aShortQueryFallsBackToLikeAndAlsoSearchesKeywords() {
        insertFact(1, "ঢাকা থেকে Sylhet", keywords = "dhaka")
        insertFact(2, "Old fact about ঢা", supersededAtMillis = 5)

        assertEquals(listOf("1"), recalledIds("ঢা"))
        assertEquals(listOf("1"), recalledIds("dh"))
    }
}
