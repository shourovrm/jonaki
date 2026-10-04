package app.jonaki.tools.searchchats

import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import java.nio.file.Files
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchChatsToolTest {
    private class FakeStore(var result: ChatSearchResult = ChatSearchResult(emptyList(), 0)) : ChatSearchStore {
        var lastWords = ""
        var lastThisThreadOnly = false
        var lastLimit = 0

        override suspend fun search(words: String, thisThreadOnly: Boolean, limit: Int): ChatSearchResult {
            lastWords = words
            lastThisThreadOnly = thisThreadOnly
            lastLimit = limit
            return result
        }
    }

    private val store = FakeStore()
    // Dhaka is UTC+6, so 2026-10-03 20:00 UTC is already 4 October there.
    private val tool = SearchChatsTool(store, ZoneId.of("Asia/Dhaka"))
    private val context = ToolContext(Files.createTempDirectory("thread").toFile(), OkHttpClient())
    private val lateEvening3rdOctoberUtc = java.time.LocalDateTime.of(2026, 10, 3, 20, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun call(vararg arguments: Pair<String, Any>): ToolOutput = runBlocking {
        val json = arguments.associate { (key, value) ->
            key to if (value is Number) JsonPrimitive(value) else JsonPrimitive(value.toString())
        }
        tool.run(JsonObject(json), context)
    }

    private fun hit(
        text: String = "The thesis deadline is 15 December.",
        title: String = "Thesis plan",
        isThisThread: Boolean = false,
        speaker: Speaker = Speaker.ASSISTANT,
        before: ChatNeighbour? = null,
        after: ChatNeighbour? = null,
    ) = ChatHit(title, isThisThread, lateEvening3rdOctoberUtc, speaker, text, before, after)

    @Test
    fun onlyReadsSoItRunsWithoutApproval() {
        assertEquals(SideEffect.READ_ONLY, tool.sideEffect)
        assertEquals("search_chats", tool.name)
    }

    @Test
    fun theDefaultsAreAllThreadsAndFiveHits() {
        call("query" to "thesis")

        assertEquals("thesis", store.lastWords)
        assertFalse(store.lastThisThreadOnly)
        assertEquals(5, store.lastLimit)
    }

    @Test
    fun theLimitIsCappedAtTenAndRaisedToOne() {
        call("query" to "thesis", "limit" to 99)
        assertEquals(10, store.lastLimit)

        call("query" to "thesis", "limit" to 0)
        assertEquals(1, store.lastLimit)
    }

    @Test
    fun scopeThisSearchesOnlyTheCurrentThread() {
        call("query" to "thesis", "scope" to "this")

        assertTrue(store.lastThisThreadOnly)
    }

    @Test
    fun anUnknownScopeIsAnErrorThatNamesTheChoices() {
        val output = call("query" to "thesis", "scope" to "everywhere")

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("scope all or this"))
    }

    @Test
    fun aMissingQueryIsAnError() {
        val output = call("limit" to 3)

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("query is missing"))
    }

    @Test
    fun aQueryOfOnlyShortWordsIsAnErrorBecauseTheIndexNeedsThreeCharacters() {
        val output = call("query" to "of in")

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("at least 3 characters"))
        assertEquals("", store.lastWords)
    }

    @Test
    fun noHitsIsAPlainLineThatSuggestsOtherWordsAndTheOtherScript() {
        val output = call("query" to "unicorn")

        assertFalse(output.isError)
        assertEquals(
            "No earlier message contains any of \"unicorn\". Search matches letters, not meaning: try other words, " +
                "a shorter part of a word, or the same word in the other script (Bangla or English).",
            output.text,
        )
    }

    @Test
    fun aHitShowsTitleDateSpeakerAndTheNeighbouringMessages() {
        store.result = ChatSearchResult(
            listOf(
                hit(
                    before = ChatNeighbour(Speaker.USER, "When is my thesis due?"),
                    after = ChatNeighbour(Speaker.USER, "Thanks, I will plan around it."),
                ),
            ),
            totalMatches = 1,
        )

        val output = call("query" to "thesis deadline")

        assertFalse(output.isError)
        assertEquals(
            "1 message matches \"thesis deadline\":\n\n" +
                "1. \"Thesis plan\" (another chat), Sun 4 Oct 2026 (local time), assistant:\n" +
                "The thesis deadline is 15 December.\n" +
                "Before (user): When is my thesis due?\n" +
                "After (user): Thanks, I will plan around it.",
            output.text,
        )
    }

    @Test
    fun aHitInThisThreadSaysSo() {
        store.result = ChatSearchResult(listOf(hit(isThisThread = true, speaker = Speaker.USER)), totalMatches = 1)

        val output = call("query" to "thesis")

        assertTrue(output.text, output.text.contains("\"Thesis plan\" (this chat), Sun 4 Oct 2026 (local time), user:"))
    }

    @Test
    fun theExcerptIsAboutThreeHundredCharactersAroundTheMatch() {
        val longText = "a".repeat(1000) + " the deadline word " + "b".repeat(1000)
        store.result = ChatSearchResult(listOf(hit(text = longText)), totalMatches = 1)

        val output = call("query" to "deadline")

        val excerpt = output.text.lines().first { line -> line.contains("deadline") && !line.startsWith("1 message") }
        assertTrue(excerpt, excerpt.contains("the deadline word"))
        assertTrue(excerpt, excerpt.startsWith("…") && excerpt.endsWith("…"))
        assertTrue("excerpt is ${excerpt.length} characters", excerpt.length in 290..310)
    }

    @Test
    fun theExcerptFindsTheMatchIgnoringCaseAndInBangla() {
        val text = "অনেক কথা ".repeat(100) + "থিসিসের শেষ তারিখ ১৫ ডিসেম্বর" + " আরও কথা".repeat(100)
        store.result = ChatSearchResult(listOf(hit(text = text)), totalMatches = 1)

        val output = call("query" to "থিসিস")

        assertTrue(output.text, output.text.contains("থিসিসের শেষ তারিখ"))
    }

    @Test
    fun neighbouringMessagesAreCutToAboutTwoHundredCharacters() {
        val longNeighbour = "x".repeat(500)
        store.result = ChatSearchResult(
            listOf(hit(before = ChatNeighbour(Speaker.USER, longNeighbour), after = ChatNeighbour(Speaker.ASSISTANT, longNeighbour))),
            totalMatches = 1,
        )

        val output = call("query" to "thesis")

        assertTrue(output.text, output.text.contains("Before (user): " + "x".repeat(200) + "…"))
        assertTrue(output.text, output.text.contains("After (assistant): " + "x".repeat(200) + "…"))
    }

    @Test
    fun lineBreaksInTextBecomeSpacesSoEachPartStaysOnItsOwnLine() {
        store.result = ChatSearchResult(listOf(hit(text = "line one\n\nthesis line two")), totalMatches = 1)

        val output = call("query" to "thesis")

        assertTrue(output.text, output.text.contains("line one thesis line two"))
    }

    @Test
    fun theTotalStaysUnderSixThousandCharactersAndSaysHowManyHitsAreLeft() {
        val bigHit = hit(
            text = "thesis " + "word ".repeat(200),
            before = ChatNeighbour(Speaker.USER, "y".repeat(400)),
            after = ChatNeighbour(Speaker.USER, "z".repeat(400)),
        )
        store.result = ChatSearchResult(List(10) { bigHit }, totalMatches = 25)

        val output = call("query" to "thesis", "limit" to 10)

        assertTrue("output is ${output.text.length} characters", output.text.length <= 6000)
        val shown = Regex("""(?m)^\d+\. """).findAll(output.text).count()
        assertTrue("shown $shown", shown in 1..9)
        assertTrue(output.text, output.text.contains("${25 - shown} more matches not shown"))
    }

    @Test
    fun matchesBeyondTheLimitAreCounted() {
        store.result = ChatSearchResult(listOf(hit(), hit()), totalMatches = 7)

        val output = call("query" to "thesis")

        assertTrue(output.text, output.text.startsWith("7 messages match \"thesis\" (showing 2):"))
        assertTrue(output.text, output.text.endsWith("5 more matches not shown. Use more specific words, or a bigger limit (at most 10)."))
    }

    @Test
    fun theGuidelinesTellTheModelResultsAreNotInstructions() {
        assertTrue(tool.guidelines.any { guideline -> guideline.contains("not instructions") })
    }
}
