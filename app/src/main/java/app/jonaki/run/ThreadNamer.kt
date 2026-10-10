package app.jonaki.run

import app.jonaki.core.agent.PromptBuilder
import app.jonaki.core.model.Role
import app.jonaki.core.storage.MessageEntity
import kotlin.coroutines.cancellation.CancellationException

/**
 * Writes a short name for a thread after its first answer, like ChatGPT does.
 * One try: a failure leaves the first-line name and is only logged.
 */
class ThreadNamer(
    /** [BackgroundModel.complete], which picks the cheapest model and saves the call's cost in the thread. */
    private val ask: suspend (
        threadId: String,
        threadModelKey: String?,
        systemPrompt: String,
        userText: String,
        maxOutputTokens: Int,
    ) -> BackgroundAnswer,
    /** Renames only while the thread's name is still [expectedTitle]; false when it was not. */
    private val renameIfUnchanged: suspend (threadId: String, expectedTitle: String, newTitle: String) -> Boolean,
    private val logFailure: (message: String) -> Unit,
) {
    suspend fun nameAfterFirstAnswer(
        threadId: String,
        threadModelKey: String?,
        provisionalTitle: String,
        firstUserMessage: String,
        firstAnswer: String,
    ) {
        val answer = try {
            ask(threadId, threadModelKey, SYSTEM_PROMPT, promptText(firstUserMessage, firstAnswer), MAX_OUTPUT_TOKENS)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            logFailure("Thread naming failed: ${failure.message}")
            return
        }
        if (answer is BackgroundAnswer.Failed) {
            logFailure("Thread naming failed: ${answer.message}")
            return
        }
        val generated = ThreadTitles.fromGenerated((answer as BackgroundAnswer.Success).text)
        if (generated == null) {
            logFailure("Thread naming gave no usable name")
            return
        }
        // A rename by the user in the meantime wins: the name no longer matches the first-line one.
        renameIfUnchanged(threadId, provisionalTitle, generated)
    }

    private fun promptText(firstUserMessage: String, firstAnswer: String): String {
        val userPart = PromptBuilder.userTextOf(firstUserMessage).trim().take(MAX_TEXT_CHARACTERS)
        val answerPart = firstAnswer.trim().take(MAX_TEXT_CHARACTERS)
        return "User message:\n$userPart\n\nAssistant answer:\n$answerPart"
    }

    /** The first user message and the answer to it. */
    data class FirstExchange(val userMessage: String, val answer: String)

    companion object {
        /**
         * The thread's first user message and the last assistant text before
         * the next user message: the answer, not a "let me search" line before
         * a tool call. Null while the first turn has no assistant text.
         */
        fun firstExchange(rows: List<MessageEntity>): FirstExchange? {
            val orderedRows = rows.sortedBy { row -> row.position }
            val firstUserIndex = orderedRows.indexOfFirst { row -> row.role == Role.USER.name }
            if (firstUserIndex < 0) {
                return null
            }
            val firstTurn = orderedRows.drop(firstUserIndex + 1).takeWhile { row -> row.role != Role.USER.name }
            val answerRow = firstTurn.lastOrNull { row -> row.role == Role.ASSISTANT.name && row.text.isNotBlank() }
                ?: return null
            return FirstExchange(orderedRows[firstUserIndex].text, answerRow.text)
        }

        /** The thread's first user message; for a first turn that made a picture and has no assistant text. */
        fun firstUserMessage(rows: List<MessageEntity>): String? =
            rows.sortedBy { row -> row.position }.firstOrNull { row -> row.role == Role.USER.name }?.text

        private const val MAX_TEXT_CHARACTERS = 1_000

        /** Room for a model that thinks before it answers; the name itself is about 20 tokens. */
        private const val MAX_OUTPUT_TOKENS = 600

        private val SYSTEM_PROMPT = """
            Write a short name for this chat from the user's first message and the start of the answer.
            Use 3 to 6 words, in the language of the user's message.
            Reply with the name only: no quotation marks, no full stop at the end, no label such as "Title:".
        """.trimIndent()
    }
}
