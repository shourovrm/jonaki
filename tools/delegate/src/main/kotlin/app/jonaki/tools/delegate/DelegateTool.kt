package app.jonaki.tools.delegate

import app.jonaki.core.toolapi.Capability
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.SubagentLauncher
import app.jonaki.core.toolapi.SubagentModelInfo
import app.jonaki.core.toolapi.SubagentTask
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import app.jonaki.core.toolapi.stringArgument
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * delegate: hands one task, or up to four in parallel, to subagents (M7,
 * D-015). A subagent sees only its task, never this conversation, and its
 * answer comes back as this tool's result.
 *
 * The tool itself changes nothing; each subagent's own calls go through the
 * permission broker, so it is declared read-only.
 */
class DelegateTool(private val launcher: SubagentLauncher) : Tool {
    override val name: String = "delegate"

    override val promptLine: String =
        "delegate: hand a task to a subagent (${typeNames()}), or up to $MAX_TASKS in parallel; returns their answers"

    override val guidelines: List<String> = listOf(
        "Use delegate for reasoning-heavy work such as research and writing, not to run a single search or fetch in parallel.",
        "Subagents have NO context from this conversation: put everything they need in the task (facts, file paths, " +
            "the user's wishes, the form of the answer).",
        "Subagent types: " + launcher.agentTypes.joinToString("; ") { type -> "${type.name}: ${type.description}" },
        "Name a model in delegate only when the user asks for one; otherwise each type uses the model set for it.",
        "A subagent's answer is its own work; check it before you rely on it.",
    )

    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            taskProperties()
            putJsonObject("tasks") {
                put("type", "array")
                put("maxItems", MAX_TASKS)
                put("description", "Up to $MAX_TASKS tasks that run in parallel, instead of agent and task.")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") { taskProperties() }
                    putJsonArray("required") {
                        add("agent")
                        add("task")
                    }
                }
            }
        }
    }

    private fun JsonObjectBuilder.taskProperties() {
        putJsonObject("agent") {
            put("type", "string")
            putJsonArray("enum") { launcher.agentTypes.forEach { type -> add(type.name) } }
        }
        putJsonObject("task") {
            put("type", "string")
            put("description", "The whole task, with every fact and path the subagent needs.")
        }
        putJsonObject("extra_tools") {
            put("type", "array")
            putJsonObject("items") { put("type", "string") }
            put("description", "Tools of this thread to add to the type's own.")
        }
        putJsonObject("model") {
            put("type", "string")
            put("description", "Only when the user asks for a model: one of the user's models, by name or id.")
        }
    }

    override val sideEffect: SideEffect = SideEffect.READ_ONLY
    override val requiredCapabilities: Set<Capability> = emptySet()

    // Longer than a subagent's own 10 minutes, so that a subagent at its limit still returns what it has.
    override val timeLimit: Duration = 11.minutes

    override suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput {
        val taskObjects = taskObjectsOf(arguments)
        if (taskObjects.isEmpty()) {
            return ToolOutput.error("delegate needs agent and task, or tasks", "Call it again with a task.")
        }
        if (taskObjects.size > MAX_TASKS) {
            return ToolOutput.error(
                "delegate runs at most $MAX_TASKS tasks at once, not ${taskObjects.size}",
                "Merge tasks, or delegate the rest after these finish.",
            )
        }
        val tasks = mutableListOf<SubagentTask>()
        for (taskObject in taskObjects) {
            when (val parsed = parseTask(taskObject)) {
                is ParsedTask.Valid -> tasks += parsed.task
                is ParsedTask.Invalid -> return parsed.error
            }
        }
        val reports = launcher.launch(tasks, context)
        if (reports.size == 1) {
            return ToolOutput.success(reports.single().text)
        }
        return ToolOutput.success(reports.joinToString("\n\n") { report -> "## ${report.label}\n${report.text}" })
    }

    /** The tasks array, else the single agent and task, as one list. */
    private fun taskObjectsOf(arguments: JsonObject): List<JsonObject> {
        val listed = (arguments["tasks"] as? JsonArray)?.mapNotNull { element -> element as? JsonObject }.orEmpty()
        if (listed.isNotEmpty()) {
            return listed
        }
        if (arguments["agent"] == null && arguments["task"] == null) {
            return emptyList()
        }
        return listOf(arguments)
    }

    private sealed interface ParsedTask {
        data class Valid(val task: SubagentTask) : ParsedTask

        data class Invalid(val error: ToolOutput) : ParsedTask
    }

    private fun parseTask(taskObject: JsonObject): ParsedTask {
        val agent = taskObject.stringArgument("agent")?.trim()?.lowercase().orEmpty()
        if (launcher.agentTypes.none { type -> type.name == agent }) {
            return ParsedTask.Invalid(
                ToolOutput.error("there is no agent type \"$agent\"", "Use one of: ${typeNames()}."),
            )
        }
        val task = taskObject.stringArgument("task")?.trim().orEmpty()
        if (task.isEmpty()) {
            return ParsedTask.Invalid(ToolOutput.error("the task for $agent is empty", "Write the whole task."))
        }
        val extraTools = (taskObject["extra_tools"] as? JsonArray)
            ?.mapNotNull { element -> (element as? JsonPrimitive)?.content?.trim() }
            .orEmpty()
        val unknownTools = extraTools.filter { toolName -> toolName !in launcher.extraToolNames }
        if (unknownTools.isNotEmpty()) {
            return ParsedTask.Invalid(
                ToolOutput.error(
                    "this thread has no tool ${unknownTools.joinToString(", ")} to give",
                    "extra_tools may name: ${launcher.extraToolNames.joinToString(", ")}.",
                ),
            )
        }
        val requestedModel = taskObject.stringArgument("model")?.trim().orEmpty()
        if (requestedModel.isEmpty()) {
            return ParsedTask.Valid(SubagentTask(agent, task, extraTools))
        }
        val model = findModel(requestedModel)
            ?: return ParsedTask.Invalid(
                ToolOutput.error(
                    "there is no model \"$requestedModel\" among the user's models",
                    "Use one of: ${launcher.models.joinToString(", ") { info -> "${info.displayName} (${info.key})" }}.",
                ),
            )
        return ParsedTask.Valid(SubagentTask(agent, task, extraTools, model.key))
    }

    /** By key, by model id, or by display name, ignoring case. */
    private fun findModel(requested: String): SubagentModelInfo? = launcher.models.firstOrNull { info ->
        info.key.equals(requested, ignoreCase = true) ||
            info.key.substringAfter(':').equals(requested, ignoreCase = true) ||
            info.displayName.equals(requested, ignoreCase = true)
    }

    private fun typeNames(): String = launcher.agentTypes.joinToString(", ") { type -> type.name }

    companion object {
        const val MAX_TASKS = SubagentLauncher.MAX_PARALLEL
    }
}
