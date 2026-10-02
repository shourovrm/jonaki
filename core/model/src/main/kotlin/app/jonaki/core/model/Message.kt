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

/**
 * An image sent to the model with a user message, already shrunk and
 * encoded. The same file must give the same [base64Data] on every request,
 * so that the provider's prompt cache holds (D-049).
 */
data class ImagePart(
    /** For example "image/jpeg". */
    val mimeType: String,
    val base64Data: String,
)

/**
 * One message in a thread. A TOOL message answers the call named by
 * toolCallId. [images] are only ever set on USER messages, and only for a
 * request: the database keeps text, the images are added from the thread
 * folder before each request (D-049).
 */
data class Message(
    val role: Role,
    val text: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
    val images: List<ImagePart> = emptyList(),
) {
    init {
        // A tool result without its call id cannot be sent back to the model.
        require(role != Role.TOOL || toolCallId != null) { "A TOOL message needs a toolCallId" }
        require(images.isEmpty() || role == Role.USER) { "Only a USER message carries images" }
    }
}
