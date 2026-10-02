package app.jonaki.tools.mcp

import kotlinx.serialization.json.JsonObject

/**
 * One remote MCP server the user added in Settings. [headerName] and
 * [headerValue] are an optional header sent with every request, for example
 * "Authorization" and "Bearer …".
 */
data class McpServer(
    /** Stable id that names the server's tool-list cache file; survives a rename. */
    val id: String,
    /** What the model and the user call the server, for example "deepwiki". */
    val name: String,
    val url: String,
    val headerName: String? = null,
    val headerValue: String? = null,
)

/** One tool a server offers, as tools/list describes it. */
data class McpToolInfo(
    val name: String,
    val description: String,
    /** The JSON schema of the tool's arguments. */
    val inputSchema: JsonObject,
)

/** What went wrong while talking to a server; [message] is written for the model. */
class McpFailure(val kind: Kind, message: String) : Exception(message) {
    enum class Kind {
        /** No connection, a time-out or a DNS failure. */
        UNREACHABLE,

        /** HTTP 401 or 403: the header is missing or wrong. */
        REFUSED,

        /** Any other HTTP status that is not a success. */
        HTTP_STATUS,

        /** The body was not a JSON-RPC answer. */
        UNREADABLE,

        /** A JSON-RPC error object, for example -32602 for an unknown tool. */
        ERROR_ANSWER,
    }
}
