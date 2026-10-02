package app.jonaki.core.providerapi

import app.jonaki.core.model.Message
import app.jonaki.core.model.ToolCall
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/**
 * A model service that streams replies. Each wire format is one module under
 * providers/ (D-010); the agent loop knows only this interface.
 */
interface ChatProvider {
    /** Stable identifier, for example "openai-compatible:deepseek". */
    val id: String

    /**
     * Streams one model turn. The flow ends after exactly one [StreamEvent.Finished]
     * or one [StreamEvent.Failed]; cancelling the collector cancels the request.
     */
    fun stream(request: ChatRequest): Flow<StreamEvent>
}

data class ChatRequest(
    val model: String,
    /** Kept identical between requests so that the provider's prompt cache works (D-005). */
    val systemPrompt: String,
    val messages: List<Message>,
    val tools: List<ToolDefinition> = emptyList(),
    val maxOutputTokens: Int? = null,
    /** How hard the model should think; null leaves the model's own default (D-057). */
    val thinkingLevel: ThinkingLevel? = null,
)

/** The thinking setting the user picks per model or per thread (D-057). */
enum class ThinkingLevel {
    OFF,
    LOW,
    MEDIUM,
    HIGH,
}

/** A tool as the model sees it. */
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameterSchema: JsonObject,
)

sealed interface StreamEvent {
    data class TextDelta(val text: String) : StreamEvent

    /** Reasoning text from models that expose it; shown collapsed, never sent back. */
    data class ReasoningDelta(val text: String) : StreamEvent

    /** Emitted once a tool call's arguments have fully arrived. */
    data class ToolCallReady(val toolCall: ToolCall) : StreamEvent

    data class Finished(val reason: FinishReason, val usage: Usage?) : StreamEvent

    /**
     * The request failed. [retryable] is true for overload and rate-limit errors
     * (for example Gemini 503), false for bad keys or invalid requests.
     */
    data class Failed(val message: String, val retryable: Boolean) : StreamEvent
}

enum class FinishReason {
    STOP,
    TOOL_CALLS,
    LENGTH,
    OTHER,
}

data class Usage(
    val inputTokens: Int,
    val outputTokens: Int,
    /** Input tokens served from the provider's prompt cache, when it reports them. */
    val cachedInputTokens: Int? = null,
    /** Cost in US dollars when the service reports it (OpenRouter does); otherwise priced from tokens (D-027). */
    val costUsd: Double? = null,
)
