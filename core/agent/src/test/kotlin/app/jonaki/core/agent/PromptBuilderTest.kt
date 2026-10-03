package app.jonaki.core.agent

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {
    private val builder = PromptBuilder(basePrompt = "You are Jonaki, an agent on the user's phone.")
    private val tools = listOf(FakeTool("read_file"), FakeTool("web_search"))

    @Test
    fun systemPromptListsOneLinePerToolAndTheirGuidelines() {
        val prompt = builder.systemPrompt(tools)

        assertTrue(prompt.startsWith("You are Jonaki"))
        assertTrue(prompt.contains("- read_file: a fake tool for tests\n"))
        assertTrue(prompt.contains("- web_search: a fake tool for tests"))
        assertTrue(prompt.contains("Use web_search only in tests."))
    }

    @Test
    fun systemPromptIsByteIdenticalForTheSameTools() {
        val first = builder.systemPrompt(tools)
        val second = builder.systemPrompt(tools.reversed())
        assertEquals(first, second)
    }

    /**
     * With no answer style, instructions, persona or project set, the prompt
     * is exactly the bytes 0.7.0 sent, so updating breaks no prompt cache (D-118).
     */
    @Test
    fun nothingSetGivesTheSamePromptAsVersion070() {
        val emptyInstructions = InstructionsSection.build(
            answerStyle = AnswerStyle.NORMAL,
            generalInstructions = "",
            persona = null,
            threadInstructions = "",
            projectPart = ProjectSection.build("Thesis", ""),
        )

        val prompt = builder.systemPrompt(
            activeTools = tools,
            memorySection = "Memory:\n- [1] likes tea",
            skillSection = "Skills:\n- report",
            instructionsSection = emptyInstructions,
        )

        assertEquals(
            "You are Jonaki, an agent on the user's phone.\n\n" +
                "Tools:\n" +
                "- read_file: a fake tool for tests\n" +
                "- web_search: a fake tool for tests\n\n" +
                "Guidelines:\n" +
                "- Use read_file only in tests.\n" +
                "- Use web_search only in tests.\n\n" +
                "Skills:\n- report\n\n" +
                "Memory:\n- [1] likes tea",
            prompt,
        )
    }

    @Test
    fun systemPromptHoldsNoTime() {
        val prompt = builder.systemPrompt(tools)
        assertFalse(prompt.contains("2026"))
    }

    @Test
    fun timeGoesIntoTheUserMessage() {
        val now = ZonedDateTime.of(2026, 10, 2, 14, 44, 0, 0, ZoneId.of("Asia/Dhaka"))

        val message = builder.userMessageWithContext("What's on today?", now)

        assertEquals("[Friday 2 October 2026, 14:44 Asia/Dhaka]\nWhat's on today?", message)
    }
}
