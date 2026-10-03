package app.jonaki.core.agent

import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.toolapi.Tool
import kotlin.math.ceil
import kotlin.math.floor

/** The parts of a request the context sheet lists, in its order (D-081). */
enum class ContextPartKind {
    SYSTEM_PROMPT,
    TOOLS,
    SKILLS,
    MEMORY,
    SUMMARY,
    MESSAGES,
    TOOL_RESULTS,
    IMAGES,
}

/**
 * One part of the context. [count] is what the sheet names beside it: tools,
 * skills, facts, images, or for the summary the messages it covers; null
 * for parts without one.
 */
data class ContextPart(val kind: ContextPartKind, val tokens: Int, val count: Int? = null)

data class ContextUse(
    /** Parts with at least one token, in [ContextPartKind] order; they add up to [totalTokens]. */
    val parts: List<ContextPart>,
    val totalTokens: Int,
    /** True when the total is the service's own count for the latest request; the split is always estimated. */
    val totalIsReported: Boolean,
    /** Null when the model's window is unknown. */
    val freeTokens: Int?,
)

/**
 * How the next request of a thread uses the context window, like Claude
 * Code's /context (D-081). It is given the same pieces the next request
 * sends: the base prompt, the active tools, the skill and memory sections,
 * and the history after compaction with its images. The service reports
 * only one total, so each part is measured in characters and the parts are
 * scaled to add up to that total.
 *
 * @param summaryBlock the compaction summary as it sits at the start of the
 *   first kept message, or null without a compaction.
 */
class ContextBreakdown(
    private val basePrompt: String,
    private val tools: List<Tool>,
    private val skillSection: String,
    private val skillCount: Int,
    private val memorySection: String,
    private val factCount: Int,
    private val messages: List<Message>,
    private val summaryBlock: String?,
    private val summaryCoversMessages: Int,
) {
    /**
     * [reportedInputTokens] is the input count of the thread's latest
     * request, what the ring shows; null before the first request, when
     * every part is an estimate from characters.
     */
    fun measure(reportedInputTokens: Int?, contextWindowTokens: Int?): ContextUse {
        val estimated = estimatedParts()
        val estimatedTotal = estimated.sumOf { part -> part.tokens }
        val hasReport = reportedInputTokens != null && reportedInputTokens > 0 && estimatedTotal > 0
        val parts = if (hasReport) scaledTo(estimated, reportedInputTokens ?: 0) else estimated
        val total = parts.sumOf { part -> part.tokens }
        val free = contextWindowTokens?.let { window -> (window - total).coerceAtLeast(0) }
        return ContextUse(parts.filter { part -> part.tokens > 0 }, total, hasReport, free)
    }

    private fun estimatedParts(): List<ContextPart> {
        val summaryCharacters = summaryBlock?.length ?: 0
        val imageCount = messages.sumOf { message -> message.images.size }
        return listOf(
            ContextPart(ContextPartKind.SYSTEM_PROMPT, tokensFor(basePromptCharacters())),
            ContextPart(ContextPartKind.TOOLS, tokensFor(toolCharacters()), count = tools.size),
            ContextPart(ContextPartKind.SKILLS, tokensFor(skillSection.trim().length), count = skillCount),
            ContextPart(ContextPartKind.MEMORY, tokensFor(memorySection.trim().length), count = factCount),
            ContextPart(ContextPartKind.SUMMARY, tokensFor(summaryCharacters), count = summaryCoversMessages),
            ContextPart(ContextPartKind.MESSAGES, tokensFor(conversationCharacters() - summaryCharacters)),
            ContextPart(ContextPartKind.TOOL_RESULTS, tokensFor(toolResultCharacters())),
            ContextPart(ContextPartKind.IMAGES, imageCount * TOKENS_PER_IMAGE, count = imageCount),
        )
    }

    private fun basePromptCharacters(): Int = PromptBuilder(basePrompt).systemPrompt(emptyList()).length

    /** The tool list and guidelines in the system prompt, plus each tool's definition in the request. */
    private fun toolCharacters(): Int {
        val promptBuilder = PromptBuilder(basePrompt)
        val promptCharacters = promptBuilder.systemPrompt(tools).length - basePromptCharacters()
        val definitionCharacters = tools.sumOf { tool ->
            tool.name.length + tool.promptLine.length + tool.parameterSchema.toString().length
        }
        return promptCharacters + definitionCharacters
    }

    /** User and assistant text, with the assistant's tool calls (name and arguments). */
    private fun conversationCharacters(): Int = messages.filter { message -> message.role != Role.TOOL }.sumOf { message ->
        message.text.length + message.toolCalls.sumOf { toolCall -> toolCall.toolName.length + toolCall.argumentsJson.length }
    }

    private fun toolResultCharacters(): Int = messages.filter { message -> message.role == Role.TOOL }.sumOf { message -> message.text.length }

    /**
     * Shares of [total] in the proportions of [estimated]; the largest
     * remainders get the leftover tokens, so the parts add up exactly.
     */
    private fun scaledTo(estimated: List<ContextPart>, total: Int): List<ContextPart> {
        val estimatedTotal = estimated.sumOf { part -> part.tokens }.toDouble()
        val exactShares = estimated.map { part -> part.tokens * total / estimatedTotal }
        val wholeShares = exactShares.map { share -> floor(share).toInt() }.toMutableList()
        val leftover = total - wholeShares.sum()
        val byRemainder = exactShares.indices.sortedByDescending { index -> exactShares[index] - wholeShares[index] }
        for (index in byRemainder.take(leftover)) {
            wholeShares[index] += 1
        }
        return estimated.mapIndexed { index, part -> part.copy(tokens = wholeShares[index]) }
    }

    companion object {
        /** A common rule of thumb for English text and code; the sheet says the split is estimated. */
        private const val CHARACTERS_PER_TOKEN = 4.0

        /**
         * Images are shrunk to 1,568 pixels on the long side (ImageScale);
         * services count such an image as roughly 1,000 to 2,500 tokens.
         */
        const val TOKENS_PER_IMAGE = 1_500

        fun tokensFor(characters: Int): Int = ceil(characters.coerceAtLeast(0) / CHARACTERS_PER_TOKEN).toInt()
    }
}
