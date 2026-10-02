package app.jonaki.core.model

/** Who wrote a message in a thread. */
enum class Role {
    USER,
    ASSISTANT,
    TOOL,
}

/** A tool call as the model requested it; arguments stay raw JSON text. */
data class ToolCall(
    val id: String,
    val toolName: String,
    val argumentsJson: String,
)

/** One message in a thread. A TOOL message answers the call named by toolCallId. */
data class Message(
    val role: Role,
    val text: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
) {
    init {
        // A tool result without its call id cannot be sent back to the model.
        require(role != Role.TOOL || toolCallId != null) { "A TOOL message needs a toolCallId" }
    }
}
