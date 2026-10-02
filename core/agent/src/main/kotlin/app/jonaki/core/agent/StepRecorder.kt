package app.jonaki.core.agent

import app.jonaki.core.model.Message
import app.jonaki.core.model.ToolCall
import app.jonaki.core.providerapi.Usage
import app.jonaki.core.toolapi.ToolOutput

/** One thing that happened during a run, in the order it happened. */
sealed interface AgentEvent {
    /** A streamed piece of the answer; saved as it arrives so nothing is lost. */
    data class TextDelta(val text: String) : AgentEvent

    data class ReasoningDelta(val text: String) : AgentEvent

    /** A finished (or, after Stop, partial) assistant message, with any tool calls it made. */
    data class AssistantMessage(val message: Message, val usage: Usage?) : AgentEvent

    data class ToolStarted(val toolCall: ToolCall) : AgentEvent

    /** [message] is the TOOL message sent back to the model. */
    data class ToolFinished(val toolCall: ToolCall, val output: ToolOutput, val message: Message) : AgentEvent

    data class RunFinished(val outcome: RunOutcome) : AgentEvent
}

sealed interface RunOutcome {
    data class Completed(val finalText: String) : RunOutcome

    /** The step budget ran out; [finalText] is the answer the model gave without tools. */
    data class BudgetReached(val finalText: String) : RunOutcome

    data class ProviderFailed(val message: String, val retryable: Boolean) : RunOutcome

    /** The user pressed Stop; [partialText] is what had streamed so far. */
    data class Stopped(val partialText: String) : RunOutcome
}

/**
 * Receives every step of a run as it happens. The database (to survive the
 * app being stopped) and the chat screen (to show steps live) implement it.
 */
fun interface StepRecorder {
    suspend fun record(event: AgentEvent)
}

/** Keeps events in a list; for tests and for previews. */
class InMemoryStepRecorder : StepRecorder {
    private val recordedEvents = mutableListOf<AgentEvent>()

    val events: List<AgentEvent>
        get() = synchronized(recordedEvents) { recordedEvents.toList() }

    override suspend fun record(event: AgentEvent) {
        synchronized(recordedEvents) { recordedEvents += event }
    }
}
