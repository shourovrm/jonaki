package app.jonaki.memory

import app.jonaki.core.storage.MemoryEntity
import app.jonaki.core.storage.MemoryOrigin
import app.jonaki.core.storage.MemoryScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Keywords and global facts in background extraction, and how a plan becomes rows. */
class ExtractionKeywordsAndGlobalTest {
    private val messages = listOf(ExtractionMessage(id = "msg-a", isUser = true, text = "আমার থিসিস ১২ ডিসেম্বর"))

    private fun threadFact(id: Long, text: String, keywords: String = "") = MemoryEntity(
        id = id,
        scope = MemoryScope.THREAD,
        threadId = "t1",
        text = text,
        keywords = keywords,
        origin = MemoryOrigin.EXTRACTED,
        createdAtMillis = 10,
        updatedAtMillis = 20,
    )

    private fun plan(modelOutput: String, threadFacts: List<MemoryEntity> = emptyList(), allowGlobal: Boolean = true) =
        MemoryExtraction.plan(MemoryExtraction.parse(modelOutput), threadFacts, emptyList(), messages, allowGlobalFacts = allowGlobal)

    // Parsing

    @Test
    fun addAndUpdateReadTheirKeywordsAndCapThem() {
        val longKeywords = "k".repeat(300)
        val parsed = MemoryExtraction.parse(
            """{"operations":[{"op":"add","text":"থিসিস","keywords":"thesis, dissertation"},""" +
                """{"op":"update","id":3,"text":"নতুন","keywords":"$longKeywords"}]}""",
        ) as ParsedExtraction.Operations

        val add = parsed.operations[0] as ExtractionOperation.Add
        val update = parsed.operations[1] as ExtractionOperation.Update
        assertEquals("thesis dissertation", add.keywords)
        assertEquals(FactKeywords.MAX_LENGTH, update.keywords.length)
    }

    @Test
    fun aGlobalMarkIsRead() {
        val parsed = MemoryExtraction.parse("""{"operations":[{"op":"add","text":"Lives in Sylhet","scope":"global"}]}""")

        assertEquals(
            listOf(ExtractionOperation.Add("Lives in Sylhet", source = null, forGlobal = true)),
            (parsed as ParsedExtraction.Operations).operations,
        )
    }

    // Plans

    @Test
    fun aGlobalFactIsPlannedAsGlobalWhenAllowed() {
        val result = plan("""{"operations":[{"op":"add","text":"Lives in Sylhet","scope":"global","keywords":"sylhet"}]}""")

        assertEquals(listOf(NewFact("Lives in Sylhet", null, forGlobal = true, keywords = "sylhet")), result.adds)
    }

    @Test
    fun aGlobalMarkIsAThreadFactWhenGlobalFactsAreOff() {
        val result = plan("""{"operations":[{"op":"add","text":"Lives in Sylhet","scope":"global"}]}""", allowGlobal = false)

        assertEquals(listOf(NewFact("Lives in Sylhet", null)), result.adds)
    }

    @Test
    fun anUpdateCarriesItsKeywords() {
        val result = plan(
            """{"operations":[{"op":"update","id":4,"text":"Thesis due 20 December","keywords":"থিসিস"}]}""",
            threadFacts = listOf(threadFact(4, "Thesis due 15 December")),
        )

        assertEquals(listOf(FactUpdate(4, "Thesis due 20 December", keywords = "থিসিস")), result.updates)
    }

    // Prompts

    @Test
    fun theKeywordRuleIsAlwaysInThePrompt() {
        val prompt = MemoryExtraction.systemPrompt(inProject = false, allowGlobalFacts = false)

        assertTrue(prompt.contains("\"keywords\""))
        assertTrue(prompt.contains("up to 6"))
        assertTrue(prompt.contains("other script"))
    }

    @Test
    fun theGlobalRuleIsInThePromptOnlyWhenAllowed() {
        assertTrue(MemoryExtraction.systemPrompt(inProject = false, allowGlobalFacts = true).contains("\"scope\":\"global\""))
        assertFalse(MemoryExtraction.systemPrompt(inProject = false, allowGlobalFacts = false).contains("\"scope\":\"global\""))
        assertTrue(MemoryExtraction.systemPrompt(inProject = true, allowGlobalFacts = true).contains("\"scope\":\"project\""))
    }

    // Rows written from a plan

    @Test
    fun aProposedGlobalFactAlwaysWaitsForReview() {
        val global = NewFact("Lives in Sylhet", null, forGlobal = true)

        assertTrue(ExtractionWrites.waitsForReview(global, reviewMode = false, holdAfterOutsideContent = false))
    }

    @Test
    fun aThreadFactWaitsOnlyInReviewModeOrAfterOutsideContent() {
        val thread = NewFact("Thesis due", null)

        assertFalse(ExtractionWrites.waitsForReview(thread, reviewMode = false, holdAfterOutsideContent = false))
        assertTrue(ExtractionWrites.waitsForReview(thread, reviewMode = true, holdAfterOutsideContent = false))
        assertTrue(ExtractionWrites.waitsForReview(thread, reviewMode = false, holdAfterOutsideContent = true))
    }

    @Test
    fun newFactRowsHaveTheRightScopeAndKeywords() {
        val global = ExtractionWrites.newMemory(NewFact("Lives in Sylhet", "m1", forGlobal = true, keywords = "sylhet"), "t1", "p1", 99, true)
        val project = ExtractionWrites.newMemory(NewFact("Uses APA", null, forProject = true), "t1", "p1", 99, false)
        val thread = ExtractionWrites.newMemory(NewFact("Due Friday", null), "t1", "p1", 99, false)

        assertEquals(Triple(MemoryScope.GLOBAL, null, null), Triple(global.scope, global.threadId, global.projectId))
        assertEquals("sylhet", global.keywords)
        assertTrue(global.pendingReview)
        assertEquals(Triple(MemoryScope.PROJECT, null, "p1"), Triple(project.scope, project.threadId, project.projectId))
        assertEquals(Triple(MemoryScope.THREAD, "t1", null), Triple(thread.scope, thread.threadId, thread.projectId))
        assertEquals(MemoryOrigin.EXTRACTED, thread.origin)
    }

    @Test
    fun anUpdatedFactKeepsItsIdAndTheOldTextGoesToASupersededCopy() {
        val current = threadFact(4, "Thesis due 15 December", keywords = "thesis")

        val updated = ExtractionWrites.updated(current, FactUpdate(4, "Thesis due 20 December", keywords = "dissertation"), now = 500)
        val copy = ExtractionWrites.supersededCopy(current, now = 500)

        assertEquals(4L, updated.id)
        assertEquals("Thesis due 20 December", updated.text)
        assertEquals("dissertation", updated.keywords)
        assertEquals(500L, updated.updatedAtMillis)
        assertNull(updated.supersededAtMillis)
        assertEquals(0L, copy.id)
        assertEquals("Thesis due 15 December", copy.text)
        assertEquals("thesis", copy.keywords)
        assertEquals(500L, copy.supersededAtMillis)
        assertEquals(
            listOf(current.scope, current.threadId, current.projectId, current.origin, current.createdAtMillis),
            listOf(copy.scope, copy.threadId, copy.projectId, copy.origin, copy.createdAtMillis),
        )
        assertFalse(copy.pinned)
    }

    @Test
    fun anUpdateWithoutKeywordsClearsTheOldOnesBecauseTheTextChanged() {
        val current = threadFact(4, "Thesis due 15 December", keywords = "thesis")

        assertEquals("", ExtractionWrites.updated(current, FactUpdate(4, "Exam on 3 January"), now = 1).keywords)
    }

    @Test
    fun superseded30DaysAgoIsTheCutoffForDeletion() {
        val now = 100L * 24 * 60 * 60 * 1000

        assertEquals(70L * 24 * 60 * 60 * 1000, ExtractionWrites.supersededDeleteCutoff(now))
    }
}
