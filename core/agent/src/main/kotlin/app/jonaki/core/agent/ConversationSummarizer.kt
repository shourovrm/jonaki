package app.jonaki.core.agent

import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.providerapi.ChatProvider
import app.jonaki.core.providerapi.ChatRequest
import app.jonaki.core.providerapi.FinishReason
import app.jonaki.core.providerapi.StreamEvent
import app.jonaki.core.providerapi.Usage

sealed interface SummaryOutcome {
    data class Written(val text: String, val usage: Usage?) : SummaryOutcome

    data class Failed(val message: String) : SummaryOutcome
}

/**
 * Writes the structured summary that replaces the older part of a long
 * thread (M4 step 6, D-033), on whichever model the caller picks.
 */
class ConversationSummarizer(
    private val provider: ChatProvider,
    private val model: String,
) {
    suspend fun summarize(previousSummary: String?, transcript: String): SummaryOutcome {
        val request = ChatRequest(
            model = model,
            systemPrompt = SYSTEM_PROMPT,
            messages = listOf(Message(Role.USER, requestText(previousSummary, transcript))),
            maxOutputTokens = MAX_SUMMARY_TOKENS,
        )
        val text = StringBuilder()
        var usage: Usage? = null
        var finishReason: FinishReason? = null
        var failure: String? = null
        provider.stream(request).collect { event ->
            when (event) {
                is StreamEvent.TextDelta -> text.append(event.text)
                is StreamEvent.Finished -> {
                    usage = event.usage
                    finishReason = event.reason
                }
                is StreamEvent.Failed -> failure = event.message
                is StreamEvent.ReasoningDelta, is StreamEvent.ToolCallReady -> Unit
            }
        }
        return outcomeOf(text.toString().trim(), usage, finishReason, failure)
    }

    private fun outcomeOf(text: String, usage: Usage?, finishReason: FinishReason?, failure: String?): SummaryOutcome {
        if (failure != null) {
            return SummaryOutcome.Failed(failure)
        }
        if (finishReason == FinishReason.LENGTH) {
            // Half a summary would silently drop the newest facts.
            return SummaryOutcome.Failed("The summary was cut off at $MAX_SUMMARY_TOKENS tokens")
        }
        if (text.isEmpty()) {
            return SummaryOutcome.Failed("The model returned an empty summary")
        }
        return SummaryOutcome.Written(text, usage)
    }

    private fun requestText(previousSummary: String?, transcript: String): String {
        val parts = mutableListOf<String>()
        if (previousSummary != null) {
            parts += "Summary so far:\n$previousSummary"
        }
        parts += "Conversation to add:\n$transcript"
        return parts.joinToString("\n\n")
    }

    companion object {
        val SECTIONS = listOf(
            "Goal",
            "Constraints and Preferences",
            "Progress",
            "Key Decisions",
            "Next Steps",
            "Critical Context",
        )

        private const val MAX_SUMMARY_TOKENS = 4_000

        private val SYSTEM_PROMPT = """
            You summarise a conversation between a user and an AI assistant so that the assistant can continue it without the original messages.
            Write these sections as Markdown headings, in this order: ${SECTIONS.joinToString(", ")}.
            Under each heading, write short bullet points. Keep names, numbers, file paths, URLs, dates and the user's exact wording of requirements. Leave out greetings and anything already finished that no later step depends on.
            If a summary so far is given, merge the new conversation into it and return one complete summary.
            Write in the language the user writes in. Return only the summary.
        """.trimIndent()
    }
}
