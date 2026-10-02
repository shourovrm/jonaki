package app.jonaki.tools.mcp

import app.jonaki.core.toolapi.await
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * One session with one server over MCP Streamable HTTP, written on OkHttp
 * because the official Kotlin SDK needs a newer Kotlin (D-MCP-1). A tool call
 * opens a connection, uses it and closes it, so nothing outlives the call.
 *
 * Every message is a POST of one JSON-RPC 2.0 object. The server answers
 * either with one JSON object or with an event stream that carries the
 * answer, possibly after notifications of its own.
 */
internal class McpConnection(
    private val httpClient: OkHttpClient,
    private val server: McpServer,
) {
    private var sessionId: String? = null
    private var protocolVersion: String? = null
    private var nextRequestId = 1

    /** initialize, then the initialized notification. */
    suspend fun open() {
        sessionId = null
        protocolVersion = null
        val initializeParams = buildJsonObject {
            put("protocolVersion", REQUESTED_PROTOCOL_VERSION)
            putJsonObject("capabilities") {}
            putJsonObject("clientInfo") {
                put("name", CLIENT_NAME)
                put("version", CLIENT_VERSION)
            }
        }
        val result = request("initialize", initializeParams, mayReopen = false)
        protocolVersion = result.text("protocolVersion") ?: REQUESTED_PROTOCOL_VERSION
        notify("notifications/initialized")
    }

    /** Every tool the server offers, following nextCursor page by page. */
    suspend fun listTools(): List<McpToolInfo> {
        val tools = mutableListOf<McpToolInfo>()
        var cursor: String? = null
        repeat(MAX_PAGES) {
            val params = buildJsonObject {
                if (cursor != null) put("cursor", cursor)
            }
            val result = request("tools/list", params)
            val page = result["tools"] as? JsonArray ?: JsonArray(emptyList())
            tools += page.mapNotNull { element -> toolInfoOf(element as? JsonObject) }
            cursor = result.text("nextCursor")
            if (cursor == null) return tools
        }
        return tools
    }

    /** The tools/call result object: content, maybe structuredContent and isError. */
    suspend fun callTool(toolName: String, arguments: JsonObject): JsonObject {
        val params = buildJsonObject {
            put("name", toolName)
            put("arguments", arguments)
        }
        return request("tools/call", params)
    }

    /** Ends the session when the server gave one; a server that refuses DELETE is fine. */
    suspend fun close() {
        val currentSession = sessionId ?: return
        val request = baseRequest()
            .header(SESSION_HEADER, currentSession)
            .delete()
            .build()
        try {
            httpClient.newCall(request).await().close()
        } catch (networkError: IOException) {
            // The session ends on the server's own time-out anyway.
        }
        sessionId = null
    }

    private suspend fun request(method: String, params: JsonObject, mayReopen: Boolean = true): JsonObject {
        val id = nextRequestId++
        val message = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        }
        val answer = try {
            send(message, expectedId = id)
        } catch (expired: SessionExpired) {
            // A server may forget a session (restart, time-out); the spec says to start a new one.
            if (!mayReopen) {
                throw McpFailure(McpFailure.Kind.HTTP_STATUS, "${server.name} answered HTTP 404")
            }
            open()
            return request(method, params, mayReopen = false)
        }
        val error = answer!!["error"] as? JsonObject
        if (error != null) {
            val code = error["code"]?.let { (it as? JsonPrimitive)?.contentOrNull }
            val text = error.text("message") ?: "no message"
            throw McpFailure(McpFailure.Kind.ERROR_ANSWER, "${server.name} answered error $code: $text")
        }
        return answer["result"] as? JsonObject
            ?: throw McpFailure(McpFailure.Kind.UNREADABLE, "${server.name} sent an answer without a result")
    }

    private suspend fun notify(method: String) {
        val message = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", method)
        }
        send(message, expectedId = null)
    }

    /**
     * Posts [message] and returns the JSON-RPC answer whose id is
     * [expectedId]; a notification ([expectedId] null) returns null.
     */
    private suspend fun send(message: JsonObject, expectedId: Int?): JsonObject? {
        val builder = baseRequest()
            .header("Accept", "application/json, text/event-stream")
            .post(message.toString().toRequestBody(JSON_TYPE))
        sessionId?.let { builder.header(SESSION_HEADER, it) }
        protocolVersion?.let { builder.header(VERSION_HEADER, it) }
        val call = httpClient.newCall(builder.build())
        val response = try {
            call.await()
        } catch (networkError: IOException) {
            throw McpFailure(McpFailure.Kind.UNREACHABLE, "could not reach ${server.name} (${networkError.message})")
        }
        return readCancellably(call, response) { readAnswer(response, expectedId) }
    }

    private fun readAnswer(response: Response, expectedId: Int?): JsonObject? {
        checkStatus(response)
        // The initialize answer carries the session id; later answers repeat or omit it.
        response.header(SESSION_HEADER)?.let { sessionId = it }
        if (expectedId == null) {
            return null
        }
        val contentType = response.body?.contentType()
        val subtype = "${contentType?.type}/${contentType?.subtype}"
        val body = response.body ?: throw unreadable("an empty answer")
        return when (subtype) {
            "text/event-stream" -> answerFromEvents(EventStream(body.charStream().buffered()), expectedId)
            "application/json" -> answerFromJson(parse(body.string()), expectedId)
            else -> throw unreadable("$subtype instead of JSON")
        }
    }

    private fun checkStatus(response: Response) {
        if (response.isSuccessful) {
            return
        }
        val code = response.code
        if (code == 404 && sessionId != null) {
            throw SessionExpired()
        }
        if (code == 401 || code == 403) {
            throw McpFailure(McpFailure.Kind.REFUSED, "${server.name} refused access (HTTP $code)")
        }
        throw McpFailure(McpFailure.Kind.HTTP_STATUS, "${server.name} answered HTTP $code")
    }

    /** Skips the server's own notifications and requests until the answer to [expectedId] arrives. */
    private fun answerFromEvents(events: EventStream, expectedId: Int): JsonObject {
        while (true) {
            val data = events.nextData() ?: throw unreadable("an event stream that ended without an answer")
            val answer = answerFromJson(parse(data), expectedId)
            if (answer != null) return answer
        }
    }

    /** A single object or a batch array; null when no element answers [expectedId]. */
    private fun answerFromJson(element: JsonElement, expectedId: Int): JsonObject? {
        val candidates = when (element) {
            is JsonObject -> listOf(element)
            is JsonArray -> element.filterIsInstance<JsonObject>()
            else -> emptyList()
        }
        return candidates.firstOrNull { candidate -> isAnswerTo(candidate, expectedId) }
    }

    private fun isAnswerTo(message: JsonObject, expectedId: Int): Boolean {
        val id = (message["id"] as? JsonPrimitive)?.contentOrNull ?: return false
        val isAnswer = message.containsKey("result") || message.containsKey("error")
        return isAnswer && id == expectedId.toString()
    }

    private fun parse(text: String): JsonElement = try {
        Json.parseToJsonElement(text)
    } catch (badJson: SerializationException) {
        throw unreadable("text that is not JSON")
    } catch (badJson: IllegalArgumentException) {
        throw unreadable("text that is not JSON")
    }

    private fun unreadable(what: String) = McpFailure(McpFailure.Kind.UNREADABLE, "${server.name} sent $what")

    private fun baseRequest(): Request.Builder {
        val builder = Request.Builder().url(server.url)
        val headerName = server.headerName?.trim().orEmpty()
        val headerValue = server.headerValue?.trim().orEmpty()
        if (headerName.isNotEmpty() && headerValue.isNotEmpty()) {
            builder.header(headerName, headerValue)
        }
        return builder
    }

    private fun toolInfoOf(tool: JsonObject?): McpToolInfo? {
        if (tool == null) return null
        val name = tool.text("name") ?: return null
        return McpToolInfo(
            name = name,
            description = tool.text("description").orEmpty(),
            inputSchema = tool["inputSchema"] as? JsonObject ?: JsonObject(emptyMap()),
        )
    }

    /**
     * Reads the body on an IO thread; cancelling the coroutine (Stop, the
     * tool's time limit) cancels the call, which ends a blocked read at once.
     */
    private suspend fun <T> readCancellably(call: Call, response: Response, read: () -> T): T = coroutineScope {
        val reading = async(Dispatchers.IO) { response.use { read() } }
        try {
            reading.await()
        } catch (cancelled: CancellationException) {
            call.cancel()
            throw cancelled
        } catch (networkError: IOException) {
            throw McpFailure(McpFailure.Kind.UNREACHABLE, "lost the connection to ${server.name} (${networkError.message})")
        }
    }

    private class SessionExpired : Exception()

    private companion object {
        /** The newest version the client was written against; servers answer with the one they use. */
        const val REQUESTED_PROTOCOL_VERSION = "2025-06-18"
        const val CLIENT_NAME = "Jonaki"
        const val CLIENT_VERSION = "1"
        const val SESSION_HEADER = "Mcp-Session-Id"
        const val VERSION_HEADER = "MCP-Protocol-Version"

        /** Guards against a server that hands out cursors forever. */
        const val MAX_PAGES = 20
        val JSON_TYPE = "application/json".toMediaType()
    }
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
