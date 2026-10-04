package app.jonaki.core.agent

import app.jonaki.core.toolapi.Tool
import kotlinx.serialization.json.JsonObject

/**
 * An "always allow" rule from Settings: one action of one tool, never a
 * whole tool. It makes that action run without a card in every thread. Only
 * the Settings page creates rules; no card and no thread can.
 *
 * @param action the action as [Tool.actionOf] names it; null for a tool with one action.
 * @param detail what [Tool.ruleDetailName] asks for (for the mcp tool "server/tool"); null without one.
 */
data class ApprovalRule(
    val toolName: String,
    val action: String? = null,
    val detail: String? = null,
) {
    /** True when this call of [tool] is the action the rule names. */
    fun matches(tool: Tool, arguments: JsonObject): Boolean {
        if (tool.name != toolName) {
            return false
        }
        return sameText(tool.actionOf(arguments), action) && sameText(tool.ruleDetailOf(arguments), detail)
    }

    /** A missing text and an empty one are the same, so a rule saved with "" still matches. */
    private fun sameText(first: String?, second: String?): Boolean =
        first.orEmpty().trim() == second.orEmpty().trim()
}
