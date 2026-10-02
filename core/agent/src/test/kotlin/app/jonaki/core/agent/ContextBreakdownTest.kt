package app.jonaki.core.agent

import app.jonaki.core.model.ImagePart
import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.model.ToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBreakdownTest {
    private val base = "b".repeat(400)
    private val lookup = FakeTool("lookup")

    private fun breakdown(
        messages: List<Message> = listOf(Message(Role.USER, "u".repeat(400))),
        tools: List<app.jonaki.core.toolapi.Tool> = emptyList(),
        skillSection: String = "",
        memorySection: String = "",
        summaryBlock: String? = null,
    ) = ContextBreakdown(
        basePrompt = base,
        tools = tools,
        skillSection = skillSection,
        skillCount = 0,
        memorySection = memorySection,
        factCount = 0,
        messages = messages,
        summaryBlock = summaryBlock,
        summaryCoversMessages = 0,
    )

    private fun ContextUse.tokensOf(kind: ContextPartKind): Int = parts.single { part -> part.kind == kind }.tokens

    @Test
    fun withoutARequestEveryPartIsEstimatedFromItsCharacters() {
        val use = breakdown().measure(reportedInputTokens = null, contextWindowTokens = 1_000)

        assertFalse(use.totalIsReported)
        assertEquals(100, use.tokensOf(ContextPartKind.SYSTEM_PROMPT))
        assertEquals(100, use.tokensOf(ContextPartKind.MESSAGES))
        assertEquals(200, use.totalTokens)
        assertEquals(800, use.freeTokens)
    }

    @Test
    fun theReportedTotalIsSplitInTheEstimatesProportionsAndAddsUpExactly() {
        val messages = listOf(Message(Role.USER, "u".repeat(800)))

        val use = breakdown(messages = messages).measure(reportedInputTokens = 1_001, contextWindowTokens = 10_000)

        assertTrue(use.totalIsReported)
        assertEquals(1_001, use.totalTokens)
        assertEquals(1_001, use.parts.sumOf { part -> part.tokens })
        // 100 against 200 estimated tokens: one third and two thirds of 1,001.
        assertEquals(334, use.tokensOf(ContextPartKind.SYSTEM_PROMPT))
        assertEquals(667, use.tokensOf(ContextPartKind.MESSAGES))
        assertEquals(8_999, use.freeTokens)
    }

    @Test
    fun toolDefinitionsCountPromptLinesGuidelinesAndSchemas() {
        val use = breakdown(tools = listOf(lookup)).measure(reportedInputTokens = null, contextWindowTokens = null)

        val withTools = PromptBuilder(base).systemPrompt(listOf(lookup)).length - PromptBuilder(base).systemPrompt(emptyList()).length
        val definition = lookup.name.length + lookup.promptLine.length + lookup.parameterSchema.toString().length
        assertEquals(ContextBreakdown.tokensFor(withTools + definition), use.tokensOf(ContextPartKind.TOOLS))
        assertEquals(1, use.parts.single { part -> part.kind == ContextPartKind.TOOLS }.count)
        assertNull(use.freeTokens)
    }

    @Test
    fun theSummaryIsItsOwnPartAndNotCountedAsMessages() {
        val summary = "s".repeat(4_000)
        val messages = listOf(Message(Role.USER, summary + "\n\n" + "u".repeat(400)))

        val use = breakdown(messages = messages, summaryBlock = summary).measure(null, null)

        assertEquals(1_000, use.tokensOf(ContextPartKind.SUMMARY))
        assertEquals(ContextBreakdown.tokensFor(402), use.tokensOf(ContextPartKind.MESSAGES))
    }

    @Test
    fun toolResultsImagesSkillsAndMemoryAreSeparateParts() {
        val messages = listOf(
            Message(Role.USER, "u".repeat(400), images = listOf(ImagePart("image/jpeg", ""), ImagePart("image/jpeg", ""))),
            Message(Role.ASSISTANT, "", toolCalls = listOf(ToolCall("c1", "lookup", "{}"))),
            Message(Role.TOOL, "r".repeat(800), toolCallId = "c1"),
        )

        val use = breakdown(messages = messages, skillSection = "k".repeat(40), memorySection = "m".repeat(80)).measure(null, null)

        assertEquals(200, use.tokensOf(ContextPartKind.TOOL_RESULTS))
        assertEquals(2 * ContextBreakdown.TOKENS_PER_IMAGE, use.tokensOf(ContextPartKind.IMAGES))
        assertEquals(2, use.parts.single { part -> part.kind == ContextPartKind.IMAGES }.count)
        assertEquals(10, use.tokensOf(ContextPartKind.SKILLS))
        assertEquals(20, use.tokensOf(ContextPartKind.MEMORY))
        // The assistant's call counts as message text: its tool name and arguments.
        assertEquals(ContextBreakdown.tokensFor(400 + "lookup".length + "{}".length), use.tokensOf(ContextPartKind.MESSAGES))
    }

    @Test
    fun emptyPartsAreLeftOutAndFreeSpaceIsNeverNegative() {
        val use = breakdown().measure(reportedInputTokens = 5_000, contextWindowTokens = 4_000)

        assertEquals(listOf(ContextPartKind.SYSTEM_PROMPT, ContextPartKind.MESSAGES), use.parts.map { part -> part.kind })
        assertEquals(0, use.freeTokens)
    }
}
