package app.jonaki.tools.mcp

import app.jonaki.core.toolapi.Capability
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import app.jonaki.core.toolapi.intArgument
import app.jonaki.core.toolapi.stringArgument
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient

/**
 * One proxy tool for every MCP server the user added (D-014): search finds
 * tools by keyword, describe shows one tool's arguments, call runs it. Only
 * call can change something outside the app, so only call asks for approval
 * (D-MCP-3). Tool lists are cached in files (D-MCP-2); each run of this tool
 * opens its own session and closes it again.
 */
class McpTool(
    private val servers: List<McpServer>,
    cacheFolder: File,
    private val clock: () -> Long = System::currentTimeMillis,
) : Tool {
    private val cache = ToolListCache(cacheFolder, clock)

    override val name: String = "mcp"

    override val promptLine: String =
        "mcp: find and call tools on the user's MCP servers (${servers.joinToString(", ") { server -> server.name }})"

    override val guidelines: List<String> = listOf(
        "Find an MCP tool with mcp action=search and a few keywords, then read its arguments with action=describe before its first call.",
        "mcp action=call runs the tool on the server and asks the user first; pass the tool's arguments as a JSON object in arguments.",
    )

    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") {
                    add(ACTION_SEARCH)
                    add(ACTION_DESCRIBE)
                    add(ACTION_CALL)
                }
            }
            putJsonObject("query") {
                put("type", "string")
                put("description", "search: keywords; empty lists every tool")
            }
            putJsonObject("server") {
                put("type", "string")
                put("description", "describe, call: the server's name as search shows it")
            }
            putJsonObject("tool") {
                put("type", "string")
                put("description", "describe, call: the tool's name")
            }
            putJsonObject("arguments") {
                put("type", "object")
                put("description", "call: the tool's arguments, matching the schema describe shows")
            }
            putJsonObject("max_results") {
                put("type", "integer")
                put("description", "search: most tools to list, default $DEFAULT_RESULTS")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override val sideEffect: SideEffect = SideEffect.CHANGES
    override val requiredCapabilities: Set<Capability> = emptySet()

    // A call waits on someone else's server, which may itself ask a model.
    override val timeLimit: Duration = 120.seconds

    // search and describe read the cached tool lists; only call reaches into another service (D-113).
    override fun sideEffectOf(arguments: JsonObject): SideEffect {
        val action = arguments.stringArgument("action")
        if (action == ACTION_SEARCH || action == ACTION_DESCRIBE) {
            return SideEffect.READ_ONLY
        }
        return SideEffect.CHANGES
    }

    override suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput =
        when (val action = arguments.stringArgument("action")) {
            ACTION_SEARCH -> search(arguments, context)
            ACTION_DESCRIBE -> describe(arguments, context)
            ACTION_CALL -> call(arguments, context)
            else -> ToolOutput.error(
                "unknown action \"$action\"",
                "Use action $ACTION_SEARCH, $ACTION_DESCRIBE or $ACTION_CALL.",
            )
        }

    private suspend fun search(arguments: JsonObject, context: ToolContext): ToolOutput {
        val query = arguments.stringArgument("query").orEmpty()
        val limit = (arguments.intArgument("max_results") ?: DEFAULT_RESULTS).coerceIn(1, MAX_RESULTS)
        val toolsByServer = linkedMapOf<String, List<McpToolInfo>>()
        val problems = mutableListOf<String>()
        for (server in servers) {
            try {
                toolsByServer[server.name] = toolsOf(server, context.httpClient).tools
            } catch (failure: McpFailure) {
                problems += failure.message.orEmpty()
            }
        }
        if (toolsByServer.isEmpty()) {
            return ToolOutput.error(
                "no MCP server answered: ${problems.joinToString("; ")}",
                "Tell the user which server failed; they can check its address and header in Settings.",
            )
        }
        val matches = ToolSearch.rank(query, toolsByServer, limit)
        val problemLines = problems.map { problem -> "Skipped: $problem." }
        if (matches.isEmpty()) {
            val problemNote = if (problems.isEmpty()) "" else " (${problems.joinToString("; ")})"
            return ToolOutput.error(
                "no MCP tool matches \"$query\"$problemNote",
                "Try other words, or search with an empty query to list every tool.",
            )
        }
        val lines = matches.map { match -> "- ${match.tool.name} (server ${match.serverName}): ${shortDescription(match.tool)}" }
        val text = buildString {
            appendLine(if (query.isBlank()) "MCP tools:" else "MCP tools matching \"$query\":")
            lines.forEach { line -> appendLine(line) }
            problemLines.forEach { line -> appendLine(line) }
            append("Use mcp action=describe server=<server> tool=<name> to see a tool's arguments.")
        }
        return ToolOutput.success(context.outputLimiter.limit(text, MAX_OUTPUT_CHARACTERS, name))
    }

    private suspend fun describe(arguments: JsonObject, context: ToolContext): ToolOutput {
        val server = serverFrom(arguments) ?: return unknownServer(arguments)
        val toolName = arguments.stringArgument("tool")?.trim()
        if (toolName.isNullOrEmpty()) {
            return ToolOutput.error("argument tool is missing", "Pass the tool's name as search lists it.")
        }
        val tool = try {
            findTool(server, toolName, context.httpClient)
        } catch (failure: McpFailure) {
            return failureOutput(server, failure)
        }
        if (tool == null) {
            return ToolOutput.error(
                "${server.name} has no tool named $toolName",
                "Find the right name with mcp action=search.",
            )
        }
        val text = buildString {
            appendLine("${tool.name} on ${server.name}")
            if (tool.description.isNotBlank()) appendLine(tool.description.trim())
            appendLine()
            appendLine("Arguments (JSON schema):")
            appendLine(prettyJson.encodeToString(JsonObject.serializer(), tool.inputSchema))
            appendLine()
            append("Call it with mcp action=call server=${server.name} tool=${tool.name} arguments={...}.")
        }
        return ToolOutput.success(context.outputLimiter.limit(text, MAX_OUTPUT_CHARACTERS, name))
    }

    private suspend fun call(arguments: JsonObject, context: ToolContext): ToolOutput {
        val server = serverFrom(arguments) ?: return unknownServer(arguments)
        val toolName = arguments.stringArgument("tool")?.trim()
        if (toolName.isNullOrEmpty()) {
            return ToolOutput.error("argument tool is missing", "Pass the tool's name as search lists it.")
        }
        val toolArguments = toolArgumentsFrom(arguments)
            ?: return ToolOutput.error("arguments is not a JSON object", "Pass the tool's arguments as an object, for example {\"query\": \"…\"}.")
        val connection = McpConnection(context.httpClient, server)
        try {
            connection.open()
            val result = try {
                connection.callTool(toolName, toolArguments)
            } catch (refused: McpFailure) {
                if (refused.kind != McpFailure.Kind.ERROR_ANSWER) throw refused
                return refusedCallOutput(server, toolName, refused, connection)
            }
            return resultOutput(server, toolName, result, context)
        } catch (failure: McpFailure) {
            return failureOutput(server, failure)
        } finally {
            runCatching { connection.close() }
        }
    }

    /**
     * The server refused the call itself (an unknown tool, wrong arguments):
     * its list may have changed, so it is fetched again on the same session.
     */
    private suspend fun refusedCallOutput(
        server: McpServer,
        toolName: String,
        refused: McpFailure,
        connection: McpConnection,
    ): ToolOutput {
        val refreshed = runCatching { connection.listTools() }.getOrNull()
        if (refreshed != null) {
            cache.save(server, refreshed)
        }
        val toolExists = refreshed == null || refreshed.any { tool -> tool.name == toolName }
        val nextStep = if (toolExists) {
            "Check the arguments with mcp action=describe server=${server.name} tool=$toolName, then call again."
        } else {
            "${server.name} has no tool named $toolName; find the right one with mcp action=search."
        }
        return ToolOutput.error(refused.message.orEmpty(), nextStep)
    }

    private fun resultOutput(server: McpServer, toolName: String, result: JsonObject, context: ToolContext): ToolOutput {
        val text = CallResultText.of(result)
        val isError = (result["isError"] as? JsonPrimitive)?.contentOrNull == "true"
        if (isError) {
            return ToolOutput.error(
                "$toolName on ${server.name} reported an error: ${text.take(MAX_ERROR_CHARACTERS)}",
                "Fix the arguments, or tell the user what the server said.",
            )
        }
        if (text.isBlank()) {
            return ToolOutput.success("$toolName on ${server.name} finished and returned no content.")
        }
        return ToolOutput.success(context.outputLimiter.limit(text, MAX_OUTPUT_CHARACTERS, name))
    }

    private fun failureOutput(server: McpServer, failure: McpFailure): ToolOutput {
        val nextStep = when (failure.kind) {
            McpFailure.Kind.REFUSED ->
                "Ask the user to check the header for ${server.name} in Settings > MCP servers."
            McpFailure.Kind.UNREACHABLE ->
                "Check the connection, or tell the user that ${server.name} cannot be reached."
            McpFailure.Kind.HTTP_STATUS, McpFailure.Kind.UNREADABLE ->
                "Ask the user to check the address of ${server.name} in Settings > MCP servers."
            McpFailure.Kind.ERROR_ANSWER ->
                "Check the tool name and arguments with mcp action=search and action=describe."
        }
        return ToolOutput.error(failure.message.orEmpty(), nextStep)
    }

    /** The named server; with one server only, the name may be left out. */
    private fun serverFrom(arguments: JsonObject): McpServer? {
        val wanted = arguments.stringArgument("server")?.trim()
        if (wanted.isNullOrEmpty()) {
            return servers.singleOrNull()
        }
        return servers.firstOrNull { server -> server.name.equals(wanted, ignoreCase = true) }
    }

    private fun unknownServer(arguments: JsonObject): ToolOutput {
        val wanted = arguments.stringArgument("server")
        val names = servers.joinToString(", ") { server -> server.name }
        val whatFailed = if (wanted.isNullOrBlank()) "argument server is missing" else "there is no MCP server named $wanted"
        return ToolOutput.error(whatFailed, "The servers are: $names.")
    }

    /** Some models send the arguments object as JSON text; both forms are accepted. */
    private fun toolArgumentsFrom(arguments: JsonObject): JsonObject? {
        val value = arguments["arguments"] ?: return JsonObject(emptyMap())
        if (value is JsonObject) return value
        val text = (value as? JsonPrimitive)?.contentOrNull ?: return null
        if (text.isBlank()) return JsonObject(emptyMap())
        return runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
    }

    /** The cached tool, or after one fresh fetch when a cached list lacks it. */
    private suspend fun findTool(server: McpServer, toolName: String, httpClient: OkHttpClient): McpToolInfo? {
        val listed = toolsOf(server, httpClient)
        val found = listed.tools.firstOrNull { tool -> tool.name == toolName }
        if (found != null || !listed.fromCache) {
            return found
        }
        return fetchAndCache(server, httpClient).firstOrNull { tool -> tool.name == toolName }
    }

    private suspend fun toolsOf(server: McpServer, httpClient: OkHttpClient): ToolList {
        val cached = cache.freshList(server)
        if (cached != null) {
            return ToolList(cached, fromCache = true)
        }
        return ToolList(fetchAndCache(server, httpClient), fromCache = false)
    }

    private suspend fun fetchAndCache(server: McpServer, httpClient: OkHttpClient): List<McpToolInfo> {
        val connection = McpConnection(httpClient, server)
        try {
            connection.open()
            val tools = connection.listTools()
            cache.save(server, tools)
            return tools
        } finally {
            runCatching { connection.close() }
        }
    }

    private fun shortDescription(tool: McpToolInfo): String {
        val firstLine = tool.description.trim().lineSequence().firstOrNull().orEmpty()
        if (firstLine.length <= MAX_SHORT_DESCRIPTION) return firstLine
        return firstLine.take(MAX_SHORT_DESCRIPTION).trimEnd() + "…"
    }

    private class ToolList(val tools: List<McpToolInfo>, val fromCache: Boolean)

    private companion object {
        const val ACTION_SEARCH = "search"
        const val ACTION_DESCRIBE = "describe"
        const val ACTION_CALL = "call"
        const val DEFAULT_RESULTS = 10
        const val MAX_RESULTS = 50
        const val MAX_SHORT_DESCRIPTION = 160
        const val MAX_OUTPUT_CHARACTERS = 30_000
        const val MAX_ERROR_CHARACTERS = 2_000
        val prettyJson = Json { prettyPrint = true }
    }
}

/** Turns a tools/call result into text for the model. */
internal object CallResultText {
    fun of(result: JsonObject): String {
        val content = result["content"] as? JsonArray ?: JsonArray(emptyList())
        val parts = content.mapNotNull { item -> partOf(item as? JsonObject) }
        if (parts.isNotEmpty()) {
            return parts.joinToString("\n\n")
        }
        // A server may answer only with structured output.
        val structured = result["structuredContent"] ?: return ""
        return structured.toString()
    }

    private fun partOf(item: JsonObject?): String? {
        if (item == null) return null
        return when (item.text("type")) {
            "text" -> item.text("text")
            "image", "audio" -> "[${item.text("mimeType") ?: item.text("type")} ${item.text("type")}, not shown]"
            "resource" -> resourceText(item["resource"] as? JsonObject)
            "resource_link" -> "[link: ${item.text("name") ?: ""} ${item.text("uri") ?: ""}]".replace("  ", " ")
            else -> null
        }
    }

    private fun resourceText(resource: JsonObject?): String? {
        if (resource == null) return null
        return resource.text("text") ?: "[resource ${resource.text("uri").orEmpty()}, not shown]"
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
