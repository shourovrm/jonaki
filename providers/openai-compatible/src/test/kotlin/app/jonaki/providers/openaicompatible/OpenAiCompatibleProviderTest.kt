package app.jonaki.providers.openaicompatible

import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.model.ToolCall
import app.jonaki.core.providerapi.ChatRequest
import app.jonaki.core.providerapi.FinishReason
import app.jonaki.core.providerapi.StreamEvent
import app.jonaki.core.providerapi.ToolDefinition
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenAiCompatibleProviderTest {
    private lateinit var server: MockWebServer
    private lateinit var provider: OpenAiCompatibleProvider

    private val request = ChatRequest(
        model = "test-model",
        systemPrompt = "You are Jonaki.",
        messages = listOf(
            Message(Role.USER, "Weather in Dhaka?"),
            Message(Role.ASSISTANT, "", toolCalls = listOf(ToolCall("call_1", "web_search", """{"query":"Dhaka weather"}"""))),
            Message(Role.TOOL, "Rain, 29 °C", toolCallId = "call_1"),
        ),
        tools = listOf(ToolDefinition("web_search", "Search the web.", Json.parseToJsonElement("""{"type":"object"}""").jsonObject)),
    )

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
        provider = OpenAiCompatibleProvider(
            preset = ProviderPresets.openRouter,
            apiKey = "test-key",
            httpClient = OkHttpClient(),
            baseUrl = server.url("/api/v1").toString(),
        )
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    private fun sse(vararg payloads: String): String =
        payloads.joinToString(separator = "") { "data: $it\n\n" }

    @Test
    fun streamsTextAndSendsTheExpectedRequest() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                ": OPENROUTER PROCESSING\n\n" + sse(
                    """{"choices":[{"delta":{"content":"It is "}}]}""",
                    """{"choices":[{"delta":{"content":"raining."},"finish_reason":"stop"}]}""",
                    """{"choices":[],"usage":{"prompt_tokens":40,"completion_tokens":4}}""",
                    "[DONE]",
                ),
            ),
        )

        val events = provider.stream(request).toList()

        assertEquals(StreamEvent.TextDelta("It is "), events[0])
        assertEquals(StreamEvent.TextDelta("raining."), events[1])
        val finished = events[2] as StreamEvent.Finished
        assertEquals(FinishReason.STOP, finished.reason)
        assertEquals(40, finished.usage?.inputTokens)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/chat/completions", recorded.path)
        assertEquals("Bearer test-key", recorded.getHeader("Authorization"))
        assertEquals("Jonaki", recorded.getHeader("X-Title"))
        val body = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals("test-model", body["model"]!!.jsonPrimitive.content)
        val messages = body["messages"]!!.jsonArray
        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("call_1", messages[3].jsonObject["tool_call_id"]!!.jsonPrimitive.content)
        val assistantToolCall = messages[2].jsonObject["tool_calls"]!!.jsonArray[0].jsonObject
        assertEquals("web_search", (assistantToolCall["function"] as JsonObject)["name"]!!.jsonPrimitive.content)
        assertEquals("web_search", body["tools"]!!.jsonArray[0].jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun badKeyFailsWithoutRetry() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"No auth credentials found","code":401}}"""))

        val events = provider.stream(request).toList()

        val failure = events.single() as StreamEvent.Failed
        assertFalse(failure.retryable)
        assertTrue(failure.message, failure.message.contains("HTTP 401: No auth credentials found"))
    }

    @Test
    fun overloadFailsAsRetryable() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("busy"))

        val failure = provider.stream(request).toList().single() as StreamEvent.Failed

        assertTrue(failure.retryable)
        assertTrue(failure.message.contains("busy"))
    }

    @Test
    fun unreachableServerFailsAsRetryable() = runBlocking {
        server.shutdown()

        val failure = provider.stream(request).toList().single() as StreamEvent.Failed

        assertTrue(failure.retryable)
        assertTrue(failure.message.startsWith("Could not reach OpenRouter"))
    }

    @Test
    fun openRouterIsAskedToReportCost() = runBlocking {
        server.enqueue(MockResponse().setBody(sse("""{"choices":[{"delta":{},"finish_reason":"stop"}]}""", "[DONE]")))

        provider.stream(request).toList()

        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("true", body.getValue("usage").jsonObject.getValue("include").jsonPrimitive.content)
    }

    @Test
    fun otherServicesAreNotSentOpenRoutersUsageField() = runBlocking {
        val deepSeek = OpenAiCompatibleProvider(ProviderPresets.deepSeek, "k", OkHttpClient(), server.url("/").toString())
        server.enqueue(MockResponse().setBody(sse("""{"choices":[{"delta":{},"finish_reason":"stop"}]}""", "[DONE]")))

        deepSeek.stream(request).toList()

        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertFalse(body.containsKey("usage"))
    }
}
