package app.jonaki.settings

import android.content.Context
import app.jonaki.tools.mcp.McpServer
import app.jonaki.tools.mcp.ToolListCache
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** One MCP server as Settings stores it; the header's value lives in [SecretStore]. */
data class SavedMcpServer(
    val id: String,
    val name: String,
    val url: String,
    val headerName: String?,
)

/**
 * The user's MCP servers (D-104): names, addresses and header names in app
 * preferences, header values encrypted in [SecretStore], tool lists cached
 * in [toolListFolder] by the mcp tool.
 */
class McpServerStore(
    context: Context,
    private val secrets: SecretStore,
    /** Where the mcp tool caches each server's tool list (D-102). */
    val toolListFolder: File,
) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val state = MutableStateFlow(McpServerList.fromText(preferences.getString(SERVERS, "").orEmpty()))

    val servers: StateFlow<List<SavedMcpServer>> = state.asStateFlow()

    fun hasHeaderValue(id: String): Boolean = secrets.hasRuntimeSecret(secretNameOf(id))

    /**
     * Adds a server ([id] null) or replaces one. [newHeaderValue] null keeps
     * the saved value; a header name left blank with no new value removes
     * the header.
     */
    fun save(id: String?, name: String, url: String, typedHeaderName: String, newHeaderValue: String?) {
        val serverId = id ?: UUID.randomUUID().toString()
        val headerName = McpServerList.headerNameFor(typedHeaderName, newHeaderValue)
        if (headerName == null) {
            secrets.removeRuntimeSecret(secretNameOf(serverId))
        } else if (newHeaderValue != null) {
            secrets.saveRuntimeSecret(secretNameOf(serverId), newHeaderValue)
        }
        // Another header can unlock other tools, so an edited server lists its tools again.
        ToolListCache.forget(toolListFolder, serverId)
        val saved = SavedMcpServer(serverId, name, url, headerName)
        val current = state.value
        val updated = if (current.any { server -> server.id == serverId }) {
            current.map { server -> if (server.id == serverId) saved else server }
        } else {
            current + saved
        }
        write(updated)
    }

    fun remove(id: String) {
        secrets.removeRuntimeSecret(secretNameOf(id))
        ToolListCache.forget(toolListFolder, id)
        write(state.value.filterNot { server -> server.id == id })
    }

    /** The servers with their header values, for one run of the mcp tool. */
    fun forTools(): List<McpServer> = state.value.map { server ->
        McpServer(
            id = server.id,
            name = server.name,
            url = server.url,
            headerName = server.headerName,
            headerValue = server.headerName?.let { secrets.readRuntimeSecret(secretNameOf(server.id)) },
        )
    }

    private fun write(servers: List<SavedMcpServer>) {
        preferences.edit().putString(SERVERS, McpServerList.toText(servers)).apply()
        state.value = servers
    }

    private fun secretNameOf(id: String): String = "mcp-header-$id"

    private companion object {
        const val SERVERS = "mcp_servers"
    }
}

/** The stored form of the server list: a JSON array, so names may hold commas and quotes. */
object McpServerList {
    private const val DEFAULT_HEADER = "Authorization"

    fun toText(servers: List<SavedMcpServer>): String = buildJsonArray {
        for (server in servers) {
            add(
                buildJsonObject {
                    put("id", server.id)
                    put("name", server.name)
                    put("url", server.url)
                    put("headerName", server.headerName)
                },
            )
        }
    }.toString()

    fun fromText(text: String): List<SavedMcpServer> {
        if (text.isBlank()) return emptyList()
        val array = runCatching { Json.parseToJsonElement(text) as? JsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { element ->
            val server = element as? JsonObject ?: return@mapNotNull null
            SavedMcpServer(
                id = server.text("id") ?: return@mapNotNull null,
                name = server.text("name") ?: return@mapNotNull null,
                url = server.text("url") ?: return@mapNotNull null,
                headerName = server.text("headerName"),
            )
        }
    }

    /** A value typed without a header name is almost always a bearer token. */
    fun headerNameFor(typedName: String, newValue: String?): String? {
        val trimmed = typedName.trim()
        if (trimmed.isNotEmpty()) return trimmed
        return if (newValue != null) DEFAULT_HEADER else null
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
