package app.jonaki.settings

import app.jonaki.core.agent.ApprovalRule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * The stored form of the "always allow" rules of Settings > Approvals. They
 * live in app preferences, so a run reads them in the same snapshot as the
 * default approval mode; no thread and no card writes them.
 */
object ApprovalRules {
    fun toText(rules: List<ApprovalRule>): String = buildJsonArray {
        for (rule in rules) {
            add(
                buildJsonObject {
                    put("tool", rule.toolName)
                    put("action", rule.action)
                    put("detail", rule.detail)
                },
            )
        }
    }.toString()

    /** Leaves out an entry without a tool name, and a repeated rule. */
    fun fromText(text: String): List<ApprovalRule> {
        if (text.isBlank()) {
            return emptyList()
        }
        val array = runCatching { Json.parseToJsonElement(text) as? JsonArray }.getOrNull() ?: return emptyList()
        val rules = mutableListOf<ApprovalRule>()
        for (element in array) {
            val rule = parse(element as? JsonObject ?: continue) ?: continue
            if (rule !in rules) {
                rules += rule
            }
        }
        return rules
    }

    /** [rule] added at the end unless it is already there. */
    fun added(current: List<ApprovalRule>, rule: ApprovalRule): List<ApprovalRule> =
        if (rule in current) current else current + rule

    private fun parse(saved: JsonObject): ApprovalRule? {
        val toolName = saved.text("tool")?.takeIf { name -> name.isNotBlank() } ?: return null
        return ApprovalRule(toolName, saved.text("action"), saved.text("detail"))
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
