package app.jonaki.core.agent

import app.jonaki.core.model.ToolCall
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.Tool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** What the user answered on an approval card. */
enum class ApprovalDecision {
    ALLOW_ONCE,
    ALLOW_FOR_THREAD,
    DENY,
}

data class ApprovalRequest(
    val toolName: String,
    val toolCall: ToolCall,
)

/**
 * Shows an approval card and suspends until the user answers. The chat
 * screen implements this; tests use a fixed answer.
 */
fun interface ApprovalRequester {
    suspend fun requestApproval(request: ApprovalRequest): ApprovalDecision
}

/**
 * Decides whether a tool call may run. Read-only tools and tools that change
 * only the app's own records always run; tools that change something else
 * need the user's approval. One broker serves one thread.
 *
 * @param toolsAllowedForThread allowances saved earlier for this thread.
 */
class PermissionBroker(
    private val approvalRequester: ApprovalRequester,
    toolsAllowedForThread: Set<String> = emptySet(),
) {
    private val allowedTools = toolsAllowedForThread.toMutableSet()

    /** Tool names the user allowed for the whole thread; the caller persists them. */
    val toolsAllowedForThread: Set<String>
        get() = allowedTools.toSet()

    suspend fun mayRun(tool: Tool, toolCall: ToolCall): Boolean {
        val needsApproval = tool.sideEffect == SideEffect.CHANGES
        if (!needsApproval) {
            return true
        }
        if (tool.isReadOnlyCall(argumentsOf(toolCall))) {
            return true
        }
        if (tool.name in allowedTools) {
            return true
        }
        val decision = approvalRequester.requestApproval(ApprovalRequest(tool.name, toolCall))
        return when (decision) {
            ApprovalDecision.ALLOW_ONCE -> true
            ApprovalDecision.ALLOW_FOR_THREAD -> {
                allowedTools += tool.name
                true
            }
            ApprovalDecision.DENY -> false
        }
    }

    /** Unreadable arguments count as no arguments, so the call is not taken for read-only. */
    private fun argumentsOf(toolCall: ToolCall): JsonObject =
        runCatching { Json.parseToJsonElement(toolCall.argumentsJson) as? JsonObject }.getOrNull()
            ?: JsonObject(emptyMap())
}
