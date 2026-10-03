package app.jonaki.core.agent

import app.jonaki.core.model.Message
import app.jonaki.core.model.Role

/**
 * The conversation for a subagent's ask_parent (D-015, D-063): the thread's
 * history up to the turn that called delegate, then the question. That turn
 * is left out because its calls have no results yet, which providers refuse.
 * The history before it is what the request that produced the delegate
 * call sent; the app sends it with the same system prompt, tool list (not
 * callable) and thinking level, so the provider's prompt cache can serve
 * that prefix. Whether it does is up to the provider.
 */
object ParentQuestion {
    fun conversation(history: List<Message>, delegateToolCallId: String, agentLabel: String, question: String): List<Message> {
        val delegateTurn = history.indexOfLast { message -> message.toolCalls.any { call -> call.id == delegateToolCallId } }
        // Without the turn, the last assistant turn with calls is the open one.
        val openTurn = if (delegateTurn >= 0) delegateTurn else lastTurnWithoutResults(history)
        val before = if (openTurn >= 0) history.subList(0, openTurn) else history
        val ask = "[A subagent you delegated to ($agentLabel) asks: $question\n" +
            "Answer it in a few sentences; you cannot call tools now.]"
        return before + Message(Role.USER, ask)
    }

    private fun lastTurnWithoutResults(history: List<Message>): Int {
        val last = history.lastOrNull() ?: return -1
        if (last.role == Role.ASSISTANT && last.toolCalls.isNotEmpty()) {
            return history.lastIndex
        }
        return -1
    }
}
