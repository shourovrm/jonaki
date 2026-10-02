package app.jonaki.core.agent

import app.jonaki.core.model.ToolCall
import app.jonaki.core.toolapi.Tool
import java.util.Collections

/** What the user answered on an approval card. */
enum class ApprovalDecision {
    ALLOW_ONCE,
    ALLOW_FOR_THREAD,

    /** A subagent's card: the tool runs without asking until that subagent ends (D-015). */
    ALLOW_FOR_TASK,
    DENY,
}

data class ApprovalRequest(
    val toolName: String,
    val toolCall: ToolCall,
    /** Set when a subagent asks: the card names it and offers "Allow for this task" (D-015). */
    val subagent: SubagentAsk? = null,
)

data class SubagentAsk(
    /** "researcher", or "researcher 2" when one delegate call started several. */
    val agentLabel: String,
    /** The subagent's reason, from request_tool; null for a call it makes. */
    val reason: String?,
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
 * need the user's approval, as far as the thread's [ApprovalMode] asks for
 * it. One broker serves one thread.
 *
 * @param toolsAllowedForThread allowances saved earlier for this thread.
 * @param approvalMode read before every call, so a mode changed during a run
 *   applies from the next tool call.
 */
class PermissionBroker(
    private val approvalRequester: ApprovalRequester,
    toolsAllowedForThread: Set<String> = emptySet(),
    private val approvalMode: () -> ApprovalMode = { ApprovalMode.ASK },
) {
    // Parallel subagents ask through the same broker, so the set is shared between coroutines.
    private val allowedTools: MutableSet<String> = Collections.synchronizedSet(toolsAllowedForThread.toMutableSet())

    /** Tool names the user allowed for the whole thread; the caller persists them. */
    val toolsAllowedForThread: Set<String>
        get() = synchronized(allowedTools) { allowedTools.toSet() }

    /** True when [tool] may run without an approval card: by its cost, the mode or an allowance. */
    fun runsWithoutAsking(tool: Tool): Boolean {
        if (!ApprovalMode.needsApproval(tool.sideEffect, approvalMode())) {
            return true
        }
        return tool.name in allowedTools
    }

    /** Shows a subagent's card; [SubagentGate] decides what the answer means and how long to wait. */
    suspend fun askForSubagent(request: ApprovalRequest): ApprovalDecision =
        approvalRequester.requestApproval(request)

    suspend fun mayRun(tool: Tool, toolCall: ToolCall): Boolean {
        if (runsWithoutAsking(tool)) {
            return true
        }
        val decision = approvalRequester.requestApproval(ApprovalRequest(tool.name, toolCall))
        return when (decision) {
            ApprovalDecision.ALLOW_ONCE -> true
            ApprovalDecision.ALLOW_FOR_THREAD -> {
                allowedTools += tool.name
                true
            }
            // Only a subagent's card offers this; for the main agent it means once.
            ApprovalDecision.ALLOW_FOR_TASK -> true
            ApprovalDecision.DENY -> false
        }
    }
}
