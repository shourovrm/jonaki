package app.jonaki.memory

import app.jonaki.core.storage.MemoryEntity
import app.jonaki.core.storage.MemoryOrigin
import app.jonaki.core.storage.MemoryScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryExtractionTest {
    private fun threadFact(id: Long, text: String, pinned: Boolean = false, origin: String = MemoryOrigin.EXTRACTED) =
        MemoryEntity(
            id = id,
            scope = MemoryScope.THREAD,
            threadId = "t1",
            text = text,
            pinned = pinned,
            origin = origin,
            createdAtMillis = 0,
            updatedAtMillis = 0,
        )

    private fun globalFact(id: Long, text: String) = threadFact(id, text).copy(scope = MemoryScope.GLOBAL, threadId = null)

    private val messages = listOf(
        ExtractionMessage(id = "msg-a", isUser = true, text = "[Friday 2 October 2026, 14:44 Asia/Dhaka]\nMy thesis is due on 15 December."),
        ExtractionMessage(id = "msg-b", isUser = false, text = "Noted. That gives you ten weeks."),
    )

    private fun plan(modelOutput: String, threadFacts: List<MemoryEntity> = emptyList(), globalFacts: List<MemoryEntity> = emptyList()) =
        MemoryExtraction.plan(MemoryExtraction.parse(modelOutput), threadFacts, globalFacts, messages)

    // Parsing

    @Test
    fun parsesAddUpdateAndDelete() {
        val parsed = MemoryExtraction.parse(
            """{"operations":[{"op":"add","text":"Thesis due 15 December","source":"m1"},""" +
                """{"op":"update","id":12,"text":"Lives in Sylhet"},{"op":"delete","id":"7"}]}""",
        )

        assertEquals(
            ParsedExtraction.Operations(
                listOf(
                    ExtractionOperation.Add("Thesis due 15 December", source = "m1"),
                    ExtractionOperation.Update(12, "Lives in Sylhet"),
                    ExtractionOperation.Delete(7),
                ),
                ignored = 0,
            ),
            parsed,
        )
    }

    @Test
    fun readsJsonInsideACodeFenceWithTextAround() {
        val parsed = MemoryExtraction.parse("Here you go:\n```json\n{\"operations\":[{\"op\":\"ADD\",\"text\":\"Likes tea\"}]}\n```")
        assertEquals(listOf(ExtractionOperation.Add("Likes tea", source = null)), (parsed as ParsedExtraction.Operations).operations)
    }

    @Test
    fun acceptsABareArray() {
        val parsed = MemoryExtraction.parse("""[{"op":"delete","id":3}]""")
        assertEquals(listOf(ExtractionOperation.Delete(3)), (parsed as ParsedExtraction.Operations).operations)
    }

    @Test
    fun emptyOperationsParseToNothing() {
        assertEquals(ParsedExtraction.Operations(emptyList(), ignored = 0), MemoryExtraction.parse("""{"operations": []}"""))
    }

    @Test
    fun malformedJsonIsAFailure() {
        assertTrue(MemoryExtraction.parse("""{"operations":[{"op":"add","text":"Likes tea"}""") is ParsedExtraction.Failed)
        assertTrue(MemoryExtraction.parse("Nothing to remember.") is ParsedExtraction.Failed)
        assertTrue(MemoryExtraction.parse("""{"facts":[]}""") is ParsedExtraction.Failed)
    }

    @Test
    fun brokenOperationsAreIgnoredAndCounted() {
        val parsed = MemoryExtraction.parse(
            """{"operations":[{"op":"add"},{"op":"update","id":"x","text":"a"},{"op":"rename","id":1},""" +
                """"just text",{"op":"delete"},{"op":"add","text":"   "},{"op":"add","text":"Kept"}]}""",
        )

        assertEquals(ParsedExtraction.Operations(listOf(ExtractionOperation.Add("Kept", null)), ignored = 6), parsed)
    }

    @Test
    fun overlongFactsAreIgnored() {
        val parsed = MemoryExtraction.parse("""{"operations":[{"op":"add","text":"${"a".repeat(501)}"}]}""")
        assertEquals(ParsedExtraction.Operations(emptyList(), ignored = 1), parsed)
    }

    // Planning

    @Test
    fun addKeepsTheSourceMessageOfItsLabel() {
        val result = plan("""{"operations":[{"op":"add","text":"Thesis due 15 December","source":"m1"}]}""")
        assertEquals(listOf(NewFact("Thesis due 15 December", sourceMessageId = "msg-a")), result.adds)
    }

    @Test
    fun unknownSourceLabelGivesNoSource() {
        val result = plan("""{"operations":[{"op":"add","text":"A fact","source":"m9"}]}""")
        assertEquals(listOf(NewFact("A fact", sourceMessageId = null)), result.adds)
    }

    @Test
    fun duplicateAddsAreDropped() {
        val result = plan(
            """{"operations":[{"op":"add","text":"Likes tea"},{"op":"add","text":"likes tea."},""" +
                """{"op":"add","text":"Thesis due 15 December"},{"op":"add","text":"User's name is Riad"}]}""",
            threadFacts = listOf(threadFact(4, "Thesis due 15 December")),
            globalFacts = listOf(globalFact(1, "User's name is Riad")),
        )

        assertEquals(listOf(NewFact("Likes tea", null)), result.adds)
        assertEquals(3, result.skipped)
    }

    @Test
    fun updateAndDeleteApplyToThisThreadsFactsOnly() {
        val result = plan(
            """{"operations":[{"op":"update","id":4,"text":"Thesis due 20 December"},{"op":"delete","id":5},""" +
                """{"op":"delete","id":1},{"op":"update","id":99,"text":"x"}]}""",
            threadFacts = listOf(threadFact(4, "Thesis due 15 December"), threadFact(5, "Uses LaTeX")),
            globalFacts = listOf(globalFact(1, "User's name is Riad")),
        )

        assertEquals(listOf(FactUpdate(4, "Thesis due 20 December")), result.updates)
        assertEquals(listOf(5L), result.deletes)
        assertEquals(2, result.skipped)
    }

    @Test
    fun pinnedAndUserWrittenFactsAreLeftAlone() {
        val result = plan(
            """{"operations":[{"op":"update","id":4,"text":"changed"},{"op":"delete","id":5}]}""",
            threadFacts = listOf(threadFact(4, "Pinned fact", pinned = true), threadFact(5, "Typed by user", origin = MemoryOrigin.USER)),
        )

        assertTrue(result.updates.isEmpty())
        assertTrue(result.deletes.isEmpty())
        assertEquals(2, result.skipped)
    }

    @Test
    fun onlyTheFirstOperationOnAFactCounts() {
        val result = plan(
            """{"operations":[{"op":"delete","id":4},{"op":"update","id":4,"text":"again"},{"op":"delete","id":4}]}""",
            threadFacts = listOf(threadFact(4, "Old")),
        )

        assertEquals(listOf(4L), result.deletes)
        assertTrue(result.updates.isEmpty())
        assertEquals(2, result.skipped)
    }

    @Test
    fun updateToTheSameTextChangesNothing() {
        val result = plan(
            """{"operations":[{"op":"update","id":4,"text":"old fact."}]}""",
            threadFacts = listOf(threadFact(4, "Old fact")),
        )
        assertTrue(result.updates.isEmpty())
        assertEquals(1, result.skipped)
    }

    @Test
    fun failedParseGivesAnEmptyPlanThatSaysSo() {
        val result = plan("not json")
        assertTrue(result.isEmpty)
        assertTrue(result.failure != null)
    }

    // Prompt

    @Test
    fun promptLabelsMessagesAndListsFactsWithIds() {
        val prompt = MemoryExtraction.userPrompt(
            messages,
            threadFacts = listOf(threadFact(4, "Uses LaTeX")),
            globalFacts = listOf(globalFact(1, "User's name is Riad")),
        )

        assertEquals(
            "Global facts (read only):\n- [1] User's name is Riad\n\n" +
                "Thread facts:\n- [4] Uses LaTeX\n\n" +
                "New messages:\n" +
                "[m1] User: [Friday 2 October 2026, 14:44 Asia/Dhaka]\nMy thesis is due on 15 December.\n\n" +
                "[m2] Assistant: Noted. That gives you ten weeks.",
            prompt,
        )
    }

    @Test
    fun promptSaysNoneWhenThereAreNoFactsAndCutsLongMessages() {
        val long = ExtractionMessage("msg-c", isUser = false, text = "z".repeat(5_000))

        val prompt = MemoryExtraction.userPrompt(listOf(long), emptyList(), emptyList())

        assertTrue(prompt.startsWith("Global facts (read only):\n(none)\n\nThread facts:\n(none)\n\n"))
        assertTrue(prompt.endsWith("z…"))
        assertTrue(prompt.length < 2_000)
    }
}
