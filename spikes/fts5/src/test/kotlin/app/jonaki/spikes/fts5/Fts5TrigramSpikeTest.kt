package app.jonaki.spikes.fts5

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Fts5TrigramSpikeTest {
    private lateinit var connection: SQLiteConnection

    private val facts = listOf(
        "আমার থিসিস সুপারভাইজার ড. রহমান",
        "User prefers DeepSeek for coding questions",
        "ঢাকা থেকে Sylhet ট্রেন সকাল ৭টায়",
        "thesis deadline ১৫ ডিসেম্বর",
    )

    @Before
    fun openDatabaseWithFacts() {
        connection = BundledSQLiteDriver().open(":memory:")
        connection.execSQL("CREATE VIRTUAL TABLE memory_fts USING fts5(text, tokenize = 'trigram')")
        for (fact in facts) {
            connection.prepare("INSERT INTO memory_fts(text) VALUES (?)").use { statement ->
                statement.bindText(1, fact)
                statement.step()
            }
        }
    }

    @After
    fun closeDatabase() {
        connection.close()
    }

    private fun search(fragment: String): List<String> {
        val matches = mutableListOf<String>()
        connection.prepare("SELECT text FROM memory_fts WHERE memory_fts MATCH ? ORDER BY rowid").use { statement ->
            statement.bindText(1, "\"$fragment\"")
            while (statement.step()) {
                matches.add(statement.getText(0))
            }
        }
        return matches
    }

    @Test
    fun reportsSqliteVersion() {
        val version = connection.prepare("SELECT sqlite_version()").use { statement ->
            statement.step()
            statement.getText(0)
        }
        println("S-1 bundled SQLite version: $version")
        val (major, minor) = version.split(".").map { part -> part.toInt() }
        // The trigram tokenizer arrived in SQLite 3.34.0.
        assertTrue(major > 3 || minor >= 34)
    }

    @Test
    fun banglaFragmentOfThreeCodePointsFindsTheBanglaFact() {
        // "থিস" is three code points: থ, ি, স.
        assertEquals(listOf(facts[0]), search("থিস"))
    }

    @Test
    fun englishFragmentIsCaseInsensitive() {
        assertEquals(listOf(facts[1]), search("dee"))
        assertEquals(listOf(facts[2]), search("SYL"))
    }

    @Test
    fun fragmentInsideAMixedFactMatches() {
        assertEquals(listOf(facts[2]), search("ট্রে"))
        assertEquals(listOf(facts[3]), search("ডিসে"))
    }

    @Test
    fun englishAndBanglaWordsForTheSameThingDoNotMatchEachOther() {
        // Trigram search is literal: "thesis" does not find "থিসিস".
        assertEquals(listOf(facts[3]), search("thesis"))
    }

    @Test
    fun twoCharacterFragmentFallsBackToLikeScan() {
        // MATCH needs at least three characters; LIKE still works but scans.
        val matches = mutableListOf<String>()
        connection.prepare("SELECT text FROM memory_fts WHERE text LIKE ? ORDER BY rowid").use { statement ->
            statement.bindText(1, "%ঢা%")
            while (statement.step()) {
                matches.add(statement.getText(0))
            }
        }
        assertEquals(listOf(facts[2]), matches)
    }
}
