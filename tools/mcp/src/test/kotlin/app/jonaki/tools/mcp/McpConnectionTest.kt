package app.jonaki.tools.mcp

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class McpConnectionTest {
    private lateinit var server: MockWebServer

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        server.shutdown()
    }

    private fun serverConfig(headerName: String? = null, headerValue: String? = null) = McpServer(
        id = "s1",
        name = "test",
        url = server.url("/mcp").toString(),
        headerName = headerName,
        headerValue = headerValue,
    )

    private fun connection(config: McpServer = serverConfig()) = McpConnection(OkHttpClient(), config)

    private fun json(body: String, sessionId: String? = null): MockResponse {
        val response = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
        if (sessionId != null) response.setHeader("Mcp-Session-Id", sessionId)
        return response
    }

    private fun events(vararg payloads: String): MockResponse {
        val body = payloads.joinToString("") { payload -> "event: message\ndata: $payload\n\n" }
        return MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body)
    }

    private fun accepted() = MockResponse().setResponseCode(202)

    private fun initializeAnswer(id: Int = 1) =
        """{"jsonrpc":"2.0","id":$id,"result":{"protocolVersion":"2025-03-26","capabilities":{"tools":{}},"serverInfo":{"name":"t","version":"1"}}}"""

    private fun RecordedRequest.bodyJson(): JsonObject = Json.parseToJsonElement(body.readUtf8()).jsonObject

    @Test
    fun opensASessionAndSendsItsIdAndVersionAfterwards() = runBlocking {
        server.enqueue(json(initializeAnswer(), sessionId = "abc"))
        server.enqueue(accepted())
        server.enqueue(json("""{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"echo","description":"Echoes","inputSchema":{"type":"object"}}]}}"""))

        val mcp = connection()
        mcp.open()
        val tools = mcp.listTools()

        assertEquals(listOf("echo"), tools.map { tool -> tool.name })
        assertEquals("Echoes", tools.single().description)
        val initialize = server.takeRequest()
        val initializeBody = initialize.bodyJson()
        assertEquals("initialize", initializeBody["method"]!!.jsonPrimitive.content)
        assertEquals("2.0", initializeBody["jsonrpc"]!!.jsonPrimitive.content)
        assertTrue(initialize.getHeader("Accept")!!.contains("application/json"))
        assertTrue(initialize.getHeader("Accept")!!.contains("text/event-stream"))
        assertNull(initialize.getHeader("Mcp-Session-Id"))
        assertNull(initialize.getHeader("MCP-Protocol-Version"))
        val initialized = server.takeRequest()
        val initializedBody = initialized.bodyJson()
        assertEquals("notifications/initialized", initializedBody["method"]!!.jsonPrimitive.content)
        assertNull("a notification has no id", initializedBody["id"])
        assertEquals("abc", initialized.getHeader("Mcp-Session-Id"))
        val list = server.takeRequest()
        assertEquals("tools/list", list.bodyJson()["method"]!!.jsonPrimitive.content)
        assertEquals("abc", list.getHeader("Mcp-Session-Id"))
        // The version the server answered with, not the one asked for.
        assertEquals("2025-03-26", list.getHeader("MCP-Protocol-Version"))
    }

    @Test
    fun readsAnswersSentAsAnEventStreamAndSkipsServerNotifications() = runBlocking {
        server.enqueue(events(initializeAnswer()))
        server.enqueue(accepted())
        server.enqueue(
            events(
                """{"jsonrpc":"2.0","method":"notifications/progress","params":{"progress":1}}""",
                """{"jsonrpc":"2.0","id":2,"result":{"content":[{"type":"text","text":"hi"}]}}""",
            ),
        )

        val mcp = connection()
        mcp.open()
        val result = mcp.callTool("echo", buildJsonObject { put("text", "hi") })

        assertEquals("hi", result["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        server.takeRequest()
        server.takeRequest()
        val call = server.takeRequest().bodyJson()
        assertEquals("tools/call", call["method"]!!.jsonPrimitive.content)
        val params = call["params"]!!.jsonObject
        assertEquals("echo", params["name"]!!.jsonPrimitive.content)
        assertEquals(JsonPrimitive("hi"), params["arguments"]!!.jsonObject["text"])
    }

    @Test
    fun followsPaginationCursors() = runBlocking {
        server.enqueue(json(initializeAnswer()))
        server.enqueue(accepted())
        server.enqueue(json("""{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"a","inputSchema":{"type":"object"}}],"nextCursor":"page2"}}"""))
        server.enqueue(json("""{"jsonrpc":"2.0","id":3,"result":{"tools":[{"name":"b","inputSchema":{"type":"object"}}]}}"""))

        val mcp = connection()
        mcp.open()
        val tools = mcp.listTools()

        assertEquals(listOf("a", "b"), tools.map { tool -> tool.name })
        server.takeRequest()
        server.takeRequest()
        assertNull(server.takeRequest().bodyJson()["params"]!!.jsonObject["cursor"])
        assertEquals("page2", server.takeRequest().bodyJson()["params"]!!.jsonObject["cursor"]!!.jsonPrimitive.content)
    }

    @Test
    fun aJsonRpcErrorBecomesAFailureWithItsCodeAndMessage() = runBlocking {
        server.enqueue(json(initializeAnswer()))
        server.enqueue(accepted())
        server.enqueue(json("""{"jsonrpc":"2.0","id":2,"error":{"code":-32602,"message":"Unknown tool: nope"}}"""))

        val mcp = connection()
        mcp.open()
        try {
            mcp.callTool("nope", JsonObject(emptyMap()))
            fail("expected a failure")
        } catch (failure: McpFailure) {
            assertEquals(McpFailure.Kind.ERROR_ANSWER, failure.kind)
            assertTrue(failure.message!!, failure.message!!.contains("-32602"))
            assertTrue(failure.message!!, failure.message!!.contains("Unknown tool: nope"))
        }
    }

    @Test
    fun aRefusedKeyIsReportedAsRefused() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        try {
            connection().open()
            fail("expected a failure")
        } catch (failure: McpFailure) {
            assertEquals(McpFailure.Kind.REFUSED, failure.kind)
        }
    }

    @Test
    fun sendsTheConfiguredHeaderOnEveryRequest() = runBlocking {
        server.enqueue(json(initializeAnswer()))
        server.enqueue(accepted())
        server.enqueue(json("""{"jsonrpc":"2.0","id":2,"result":{"tools":[]}}"""))

        val mcp = connection(serverConfig(headerName = "Authorization", headerValue = "Bearer secret"))
        mcp.open()
        mcp.listTools()

        repeat(3) { assertEquals("Bearer secret", server.takeRequest().getHeader("Authorization")) }
    }

    @Test
    fun anExpiredSessionIsOpenedAgainOnceAndTheRequestRepeated() = runBlocking {
        server.enqueue(json(initializeAnswer(), sessionId = "old"))
        server.enqueue(accepted())
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(json(initializeAnswer(id = 3), sessionId = "new"))
        server.enqueue(accepted())
        server.enqueue(json("""{"jsonrpc":"2.0","id":4,"result":{"tools":[{"name":"a","inputSchema":{"type":"object"}}]}}"""))

        val mcp = connection()
        mcp.open()
        val tools = mcp.listTools()

        assertEquals(listOf("a"), tools.map { tool -> tool.name })
        repeat(5) { server.takeRequest() }
        val repeated = server.takeRequest()
        assertEquals("tools/list", repeated.bodyJson()["method"]!!.jsonPrimitive.content)
        assertEquals("new", repeated.getHeader("Mcp-Session-Id"))
    }

    @Test
    fun closeEndsTheSessionWithDeleteAndIgnoresARefusal() = runBlocking {
        server.enqueue(json(initializeAnswer(), sessionId = "abc"))
        server.enqueue(accepted())
        server.enqueue(MockResponse().setResponseCode(405))

        val mcp = connection()
        mcp.open()
        mcp.close()

        server.takeRequest()
        server.takeRequest()
        val delete = server.takeRequest()
        assertEquals("DELETE", delete.method)
        assertEquals("abc", delete.getHeader("Mcp-Session-Id"))
    }

    @Test
    fun closeWithoutASessionSendsNothing() = runBlocking {
        server.enqueue(json(initializeAnswer()))
        server.enqueue(accepted())

        val mcp = connection()
        mcp.open()
        mcp.close()

        assertEquals(2, server.requestCount)
    }

    @Test
    fun anUnreadableAnswerIsAProtocolFailure() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html>login</html>"))
        try {
            connection().open()
            fail("expected a failure")
        } catch (failure: McpFailure) {
            assertEquals(McpFailure.Kind.UNREADABLE, failure.kind)
        }
    }

    @Test
    fun anUnreachableServerIsReportedAsUnreachable() = runBlocking {
        val config = serverConfig()
        server.shutdown()
        try {
            connection(config).open()
            fail("expected a failure")
        } catch (failure: McpFailure) {
            assertEquals(McpFailure.Kind.UNREACHABLE, failure.kind)
        }
    }
}
