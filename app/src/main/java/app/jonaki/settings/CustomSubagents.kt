package app.jonaki.settings

import app.jonaki.core.agent.AgentType
import app.jonaki.core.agent.AgentTypes
import app.jonaki.feature.settings.CustomSubagentForm
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** A subagent type the user made in Settings > Subagents (D-138). */
data class CustomSubagent(
    /** What the thread's model names in delegate: lowercase letters, digits and hyphens. */
    val name: String,
    /** One line the thread's model reads in delegate's guidelines. */
    val description: String,
    /** Its part of the subagent's system prompt, like a built-in type's. */
    val instructions: String,
    /** The tools it starts with, as far as the thread has them. */
    val tools: List<String>,
    /** Null runs it on the thread's model. */
    val modelKey: String?,
)

/**
 * The stored form of the custom subagents, and what a run makes of them.
 * They live in app preferences beside the other subagent settings, so a
 * run reads them in the same snapshot as its limits and models.
 */
object CustomSubagents {
    val BUILT_IN_NAMES: Set<String> = AgentTypes.ALL.map { type -> type.name }.toSet()

    /** Every tool a group offers, except delegate and memory: subagents do not nest, and report facts in their answer. */
    val CHOOSABLE_TOOLS: List<String> = ToolGroup.entries
        .flatMap { group -> group.toolNames }
        .distinct()
        .filter { toolName -> toolName !in AgentTypes.NEVER_GIVEN }

    fun agentTypesOf(subagents: List<CustomSubagent>): List<AgentType> = subagents.map { subagent ->
        AgentTypes.custom(subagent.name, subagent.description, subagent.instructions, subagent.tools.toSet())
    }

    /** Merged with the built-in types' choices; the names never clash, because a custom name may not be a built-in one. */
    fun modelChoicesOf(subagents: List<CustomSubagent>): Map<String, String> =
        subagents.mapNotNull { subagent -> subagent.modelKey?.let { key -> subagent.name to key } }.toMap()

    /** The subagent named [name] runs on [modelKey] from now on; null is the thread's model. */
    fun withModel(current: List<CustomSubagent>, name: String, modelKey: String?): List<CustomSubagent> =
        current.map { subagent -> if (subagent.name == name) subagent.copy(modelKey = modelKey) else subagent }

    /** [saved] replaces the one named [previousName] in place, or joins the end when that is null. */
    fun saved(current: List<CustomSubagent>, saved: CustomSubagent, previousName: String?): List<CustomSubagent> {
        if (previousName == null || current.none { subagent -> subagent.name == previousName }) {
            return current + saved
        }
        return current.map { subagent -> if (subagent.name == previousName) saved else subagent }
    }

    fun toText(subagents: List<CustomSubagent>): String = buildJsonArray {
        for (subagent in subagents) {
            add(
                buildJsonObject {
                    put("name", subagent.name)
                    put("description", subagent.description)
                    put("instructions", subagent.instructions)
                    putJsonArray("tools") { subagent.tools.forEach { toolName -> add(JsonPrimitive(toolName)) } }
                    put("model", subagent.modelKey)
                },
            )
        }
    }.toString()

    /** Leaves out entries that could not be saved through Settings: a bad or taken name, or no description. */
    fun fromText(text: String): List<CustomSubagent> {
        if (text.isBlank()) {
            return emptyList()
        }
        val array = runCatching { Json.parseToJsonElement(text) as? JsonArray }.getOrNull() ?: return emptyList()
        val subagents = mutableListOf<CustomSubagent>()
        for (element in array) {
            val subagent = parse(element as? JsonObject ?: continue) ?: continue
            val taken = subagent.name in BUILT_IN_NAMES || subagents.any { other -> other.name == subagent.name }
            if (!taken) {
                subagents += subagent
            }
        }
        return subagents
    }

    private fun parse(saved: JsonObject): CustomSubagent? {
        val name = saved.text("name") ?: return null
        val description = saved.text("description")?.takeIf { text -> text.isNotBlank() } ?: return null
        if (!CustomSubagentForm.isValidName(name)) {
            return null
        }
        val tools = (saved["tools"] as? JsonArray)?.mapNotNull { element -> (element as? JsonPrimitive)?.contentOrNull }.orEmpty()
        return CustomSubagent(
            name = name,
            description = description,
            instructions = saved.text("instructions").orEmpty(),
            tools = tools,
            modelKey = saved.text("model"),
        )
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
