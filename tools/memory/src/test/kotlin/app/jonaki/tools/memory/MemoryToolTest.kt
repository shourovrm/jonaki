package app.jonaki.tools.memory

import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryToolTest {
    /** Keeps facts in a list; recall is a plain "contains" like the real index. */
    private class FakeStore : MemoryStore {
        val facts = mutableListOf<Fact>()
        var lastRecallLimit = 0
        private var nextId = 1L

        override suspend fun remember(scope: FactScope, text: String): RememberResult {
            val existing = facts.firstOrNull { it.text.equals(text, ignoreCase = true) }
            if (existing != null) {
                return RememberResult.AlreadyKnown(existing)
            }
            val fact = Fact(nextId++, scope, text, pinned = false)
            facts += fact
            return RememberResult.Saved(fact)
        }

        override suspend fun forget(factId: Long): ForgetResult {
            val fact = facts.firstOrNull { it.id == factId } ?: return ForgetResult.NotFound
            if (fact.pinned) {
                return ForgetResult.Pinned(fact)
            }
            facts -= fact
            return ForgetResult.Forgotten(fact)
        }

        override suspend fun recall(query: String, limit: Int): List<Fact> {
            lastRecallLimit = limit
            return facts.filter { it.text.contains(query, ignoreCase = true) }.take(limit)
        }
    }

    private val store = FakeStore()
    private val tool = MemoryTool(store)
    private val context = ToolContext(Files.createTempDirectory("thread").toFile(), OkHttpClient())

    private fun call(vararg arguments: Pair<String, Any>): ToolOutput = runBlocking {
        val json = arguments.associate { (key, value) ->
            key to if (value is Number) JsonPrimitive(value) else JsonPrimitive(value.toString())
        }
        tool.run(JsonObject(json), context)
    }

    @Test
    fun changesOnlyAppDataSoItRunsWithoutApproval() {
        assertEquals(SideEffect.CHANGES_APP_DATA, tool.sideEffect)
        assertEquals("memory", tool.name)
    }

    @Test
    fun rememberSavesAThreadFactByDefault() {
        val output = call("action" to "remember", "text" to "  Thesis is due on 15 December  ")

        assertFalse(output.isError)
        assertEquals("Remembered fact 1 for this thread: Thesis is due on 15 December", output.text)
        assertEquals(Fact(1, FactScope.THREAD, "Thesis is due on 15 December", false), store.facts.single())
    }

    @Test
    fun rememberWithGlobalScopeSavesForAllThreads() {
        val output = call("action" to "remember", "text" to "User's name is Riad", "scope" to "global")

        assertEquals("Remembered fact 1 for all threads: User's name is Riad", output.text)
        assertEquals(FactScope.GLOBAL, store.facts.single().scope)
    }

    @Test
    fun rememberingTheSameTextAgainSaysItIsKnown() {
        call("action" to "remember", "text" to "Likes tea")

        val output = call("action" to "remember", "text" to "likes tea")

        assertFalse(output.isError)
        assertEquals("Already remembered as fact 1: Likes tea", output.text)
        assertEquals(1, store.facts.size)
    }

    @Test
    fun rememberNeedsText() {
        val output = call("action" to "remember", "text" to "   ")
        assertTrue(output.isError)
        assertTrue(output.text.contains("text"))
    }

    @Test
    fun rememberRefusesALongText() {
        val output = call("action" to "remember", "text" to "a".repeat(501))
        assertTrue(output.isError)
        assertTrue(output.text.contains("501 characters"))
        assertTrue(store.facts.isEmpty())
    }

    @Test
    fun unknownScopeIsAnError() {
        val output = call("action" to "remember", "text" to "x y z", "scope" to "team")
        assertTrue(output.isError)
        assertTrue(output.text.contains("thread or global"))
    }

    @Test
    fun projectScopeOutsideAProjectIsAnError() {
        val output = call("action" to "remember", "text" to "x y z", "scope" to "project")
        assertTrue(output.isError)
        assertTrue(output.text.contains("in no project"))
        assertTrue(store.facts.isEmpty())
    }

    @Test
    fun inAProjectTheProjectScopeIsOfferedAndSaved() = runBlocking {
        val projectTool = MemoryTool(store, projectName = "Thesis")
        val schemaText = projectTool.parameterSchema.toString()
        assertTrue(schemaText.contains("\"project\""))
        assertTrue(projectTool.guidelines.any { guideline -> guideline.contains("\"Thesis\"") })
        assertFalse(tool.parameterSchema.toString().contains("\"project\""))

        val arguments = JsonObject(mapOf("action" to JsonPrimitive("remember"), "text" to JsonPrimitive("Uses APA"), "scope" to JsonPrimitive("project")))
        val output = projectTool.run(arguments, context)

        assertFalse(output.isError)
        assertEquals(FactScope.PROJECT, store.facts.single().scope)
        assertTrue(output.text.contains("the project's threads"))
    }

    @Test
    fun forgetDeletesByIdAndQuotesTheText() {
        call("action" to "remember", "text" to "Old phone number is 0171")

        val output = call("action" to "forget", "id" to 1)

        assertEquals("Forgot fact 1: Old phone number is 0171", output.text)
        assertTrue(store.facts.isEmpty())
    }

    @Test
    fun forgetAcceptsTheIdAsText() {
        call("action" to "remember", "text" to "Fact")
        assertFalse(call("action" to "forget", "id" to "1").isError)
    }

    @Test
    fun forgetUnknownIdIsAnErrorThatNamesRecall() {
        val output = call("action" to "forget", "id" to 9)
        assertTrue(output.isError)
        assertTrue(output.text.contains("no fact 9"))
        assertTrue(output.text.contains("recall"))
    }

    @Test
    fun forgetRefusesAPinnedFact() {
        store.facts += Fact(5, FactScope.GLOBAL, "Pinned by user", pinned = true)

        val output = call("action" to "forget", "id" to 5)

        assertTrue(output.isError)
        assertTrue(output.text.contains("pinned"))
        assertEquals(1, store.facts.size)
    }

    @Test
    fun recallListsMatchesWithIdsAndScopes() {
        call("action" to "remember", "text" to "Thesis supervisor is Dr. Rahman", "scope" to "global")
        call("action" to "remember", "text" to "Thesis draft is in work/draft.md")
        store.facts[0] = store.facts[0].copy(pinned = true)

        val output = call("action" to "recall", "query" to "thesis")

        assertEquals(
            "2 facts contain \"thesis\":\n" +
                "[1] (all threads, pinned) Thesis supervisor is Dr. Rahman\n" +
                "[2] (this thread) Thesis draft is in work/draft.md",
            output.text,
        )
    }

    @Test
    fun recallWithNoMatchSaysWhatToTry() {
        val output = call("action" to "recall", "query" to "rivers")
        assertFalse(output.isError)
        assertTrue(output.text.startsWith("No fact contains \"rivers\"."))
    }

    @Test
    fun recallLimitDefaultsToTwentyAndIsCapped() {
        call("action" to "recall", "query" to "a")
        assertEquals(20, store.lastRecallLimit)
        call("action" to "recall", "query" to "a", "limit" to 500)
        assertEquals(50, store.lastRecallLimit)
    }

    @Test
    fun recallNeedsAQuery() {
        assertTrue(call("action" to "recall").isError)
    }

    @Test
    fun unknownActionIsAnErrorThatListsTheActions() {
        val output = call("action" to "list")
        assertTrue(output.isError)
        assertTrue(output.text.contains("remember, forget or recall"))
    }
}
