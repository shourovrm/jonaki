package app.jonaki.tools.mcp

import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Each server's tool list in `<folder>/<server id>.json`, so search and
 * describe need no connection while the list is fresh (D-MCP-2). The file
 * records the address it came from; a server whose address was edited
 * gets a new list.
 */
class ToolListCache(
    private val folder: File,
    private val clock: () -> Long,
) {
    /** The cached list while it is younger than a day and from the same address; else null. */
    suspend fun freshList(server: McpServer): List<McpToolInfo>? = withContext(Dispatchers.IO) {
        val file = fileFor(server.id)
        if (!file.exists()) return@withContext null
        val root = runCatching { Json.parseToJsonElement(file.readText()) as? JsonObject }.getOrNull()
            ?: return@withContext null
        val url = (root["url"] as? JsonPrimitive)?.contentOrNull
        val fetchedAt = (root["fetchedAtMillis"] as? JsonPrimitive)?.longOrNull ?: return@withContext null
        val age = clock() - fetchedAt
        if (url != server.url || age !in 0 until MAX_AGE_MILLIS) return@withContext null
        val tools = root["tools"] as? JsonArray ?: return@withContext null
        tools.mapNotNull { element -> toolOf(element as? JsonObject) }
    }

    suspend fun save(server: McpServer, tools: List<McpToolInfo>) = withContext(Dispatchers.IO) {
        val root = buildJsonObject {
            put("url", server.url)
            put("fetchedAtMillis", clock())
            putJsonArray("tools") {
                for (tool in tools) {
                    add(
                        buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("inputSchema", tool.inputSchema)
                        },
                    )
                }
            }
        }
        try {
            folder.mkdirs()
            fileFor(server.id).writeText(root.toString())
        } catch (writeError: IOException) {
            // A list that cannot be cached is fetched again next time; the answer still stands.
        }
    }

    private fun fileFor(serverId: String): File = File(folder, "$serverId.json")

    private fun toolOf(tool: JsonObject?): McpToolInfo? {
        if (tool == null) return null
        val name = (tool["name"] as? JsonPrimitive)?.contentOrNull ?: return null
        return McpToolInfo(
            name = name,
            description = (tool["description"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            inputSchema = tool["inputSchema"] as? JsonObject ?: JsonObject(emptyMap()),
        )
    }

    companion object {
        const val MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L

        /** Removes a server's list when the user deletes the server. */
        fun forget(folder: File, serverId: String) {
            File(folder, "$serverId.json").delete()
        }
    }
}
