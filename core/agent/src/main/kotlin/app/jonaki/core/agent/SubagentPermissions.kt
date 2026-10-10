package app.jonaki.core.agent

import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolOutput
import kotlinx.serialization.json.JsonObject

/**
 * What a subagent may do. The user's approval of the delegate call is the
 * consent for its work, so a subagent never shows a card: it reads, searches
 * and writes in the thread folder in every approval mode. It never performs
 * an action that leaves the app (sharing a file, changing the phone,
 * scheduling, calling an MCP tool), because nobody could check such an
 * action before it happens. The thread's agent asks the user once instead,
 * from the subagent's answer.
 */
internal object SubagentPermissions {
    /** True when this call of [tool] may run inside a subagent. */
    fun mayRun(tool: Tool, arguments: JsonObject): Boolean = when (tool.sideEffectOf(arguments)) {
        SideEffect.READ_ONLY, SideEffect.CHANGES_THREAD_FOLDER, SideEffect.CHANGES_APP_DATA -> true
        // Listed one by one, so that a new side effect forces a decision here.
        SideEffect.CHANGES, SideEffect.CHANGES_REVERSIBLE, SideEffect.NEEDS_USER -> false
    }

    /** What the subagent reads when it tried an action that leaves the app. */
    fun notAvailable(toolName: String): ToolOutput = ToolOutput.error(
        "this action of $toolName is not available to subagents",
        "Do not retry it. Describe the action you needed, with its arguments, in your answer under Blockers, " +
            "so that the agent that gave you the task can ask the user once.",
    )

    /**
     * What the subagent reads when it tried to contact an address that
     * appeared nowhere in its task or results, in a thread that read outside
     * content. The model may have put data into such an address, and a
     * subagent cannot ask the user.
     */
    fun addressNotKnown(toolName: String, address: String): ToolOutput = ToolOutput.error(
        "$toolName was not run: $address appeared nowhere in your task or in your earlier results",
        "Use an address from a search result or a page you read. If you need this one, give it in your answer " +
            "under Blockers, so that the agent that gave you the task can ask the user once.",
    )
}
