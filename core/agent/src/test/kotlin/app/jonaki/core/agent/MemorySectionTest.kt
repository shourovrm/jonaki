package app.jonaki.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemorySectionTest {
    private fun fact(id: Long, text: String, global: Boolean = false, pinned: Boolean = false, lastUsed: Long? = null) =
        PromptFact(id = id, text = text, isGlobal = global, pinned = pinned, lastUsedAtMillis = lastUsed)

    @Test
    fun noFactsMeansNoSection() {
        assertEquals("", MemorySection.build(emptyList()).text)
    }

    @Test
    fun globalFactsComeBeforeThreadFactsAndEachShowsItsId() {
        val section = MemorySection.build(
            listOf(fact(12, "Thesis due 15 December"), fact(3, "User's name is Riad", global = true)),
        )

        assertEquals(
            "Memory (facts saved earlier; [id] is for the memory tool):\n" +
                "All threads:\n" +
                "- [3] User's name is Riad\n" +
                "This thread:\n" +
                "- [12] Thesis due 15 December",
            section.text,
        )
        assertEquals(setOf(3L, 12L), section.includedIds.toSet())
    }

    @Test
    fun textIsByteIdenticalWhatEverOrderAndUseTimesTheFactsArriveIn() {
        val facts = listOf(
            fact(1, "A", lastUsed = 10),
            fact(2, "B", lastUsed = 30),
            fact(3, "C", global = true, lastUsed = 20),
        )
        val sameFactsUsedAgain = facts.reversed().map { it.copy(lastUsedAtMillis = 99) }

        assertEquals(MemorySection.build(facts).text, MemorySection.build(sameFactsUsedAgain).text)
    }

    @Test
    fun linesAreOrderedByIdNotByLastUse() {
        val text = MemorySection.build(listOf(fact(7, "seven", lastUsed = 1), fact(2, "two", lastUsed = 50))).text
        assertTrue(text.indexOf("[2] two") < text.indexOf("[7] seven"))
    }

    @Test
    fun budgetKeepsPinnedFirstThenMostRecentlyUsed() {
        // Each line "- [n] " plus 40 characters is about 47; a 100-character budget fits two.
        val long = "x".repeat(40)
        val facts = listOf(
            fact(1, "old $long", lastUsed = 1),
            fact(2, "pinned $long", pinned = true, lastUsed = null),
            fact(3, "recent $long", lastUsed = 50),
            fact(4, "middle $long", lastUsed = 20),
        )

        val section = MemorySection.build(facts, budgetCharactersPerScope = 110)

        assertEquals(listOf(2L, 3L), section.includedIds.sorted())
        assertTrue(section.text.endsWith("More facts are saved; find them with memory recall."))
    }

    @Test
    fun aFactTooLongForWhatIsLeftIsSkippedAndShorterOnesStillFit() {
        val facts = listOf(
            fact(1, "y".repeat(80), lastUsed = 50),
            fact(2, "z".repeat(200), lastUsed = 40),
            fact(3, "short", lastUsed = 30),
        )

        val section = MemorySection.build(facts, budgetCharactersPerScope = 110)

        assertEquals(listOf(1L, 3L), section.includedIds.sorted())
    }

    @Test
    fun eachScopeHasItsOwnBudget() {
        val long = "w".repeat(90)
        val section = MemorySection.build(
            listOf(fact(1, long, global = true), fact(2, long)),
            budgetCharactersPerScope = 110,
        )
        assertEquals(listOf(1L, 2L), section.includedIds.sorted())
        assertFalse(section.text.contains("More facts"))
    }

    @Test
    fun defaultBudgetIsAbout1500TokensPerScope() {
        assertEquals(6_000, MemorySection.DEFAULT_BUDGET_CHARACTERS)
    }

    @Test
    fun systemPromptEndsWithTheMemorySection() {
        val builder = PromptBuilder(basePrompt = "Base.")
        val section = MemorySection.build(listOf(fact(1, "Likes tea", global = true)))

        val prompt = builder.systemPrompt(listOf(FakeTool("read_file")), section.text)

        assertTrue(prompt.endsWith("All threads:\n- [1] Likes tea"))
        assertEquals(builder.systemPrompt(listOf(FakeTool("read_file"))), builder.systemPrompt(listOf(FakeTool("read_file")), ""))
    }
}
