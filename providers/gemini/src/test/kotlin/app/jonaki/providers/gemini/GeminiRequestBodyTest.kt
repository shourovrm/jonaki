package app.jonaki.providers.gemini

import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.model.ToolCall
import app.jonaki.core.providerapi.ChatRequest
import app.jonaki.core.providerapi.ToolDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class GeminiRequestBodyTest {
    private val signedId = GeminiToolCallId.encode("gemini_1", "c2ln")

    private val request = ChatRequest(
        model = "gemini-3.8-flash",
        systemPrompt = "You are Jonaki.",
        messages = listOf(
            Message(Role.USER, "Search two things"),
            Message(
                Role.ASSISTANT,
                "Searching.",
                toolCalls = listOf(
                    ToolCall(signedId, "web_search", """{"query":"a"}"""),
                    ToolCall("gemini_2", "web_fetch", """{"url":"https://b"}"""),
                ),
            ),
            Message(Role.TOOL, "result a", toolCallId = signedId),
            Message(Role.TOOL, "result b", toolCallId = "gemini_2"),
            Message(Role.USER, "thanks"),
        ),
        tools = listOf(ToolDefinition("web_search", "Search the web.", Json.parseToJsonElement("""{"type":"object"}""").jsonObject)),
        maxOutputTokens = 1000,
    )

    private val body = GeminiRequestBody.build(request)
    private val contents = body["contents"]!!.jsonArray

    @Test
    fun systemPromptGoesIntoSystemInstruction() {
        val text = body["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertEquals("You are Jonaki.", text)
    }

    @Test
    fun consecutiveToolResultsBecomeOneUserTurn() {
        assertEquals(4, contents.size)
        val responses = contents[2].jsonObject
        assertEquals("user", responses["role"]!!.jsonPrimitive.content)
        val parts = responses["parts"]!!.jsonArray
        assertEquals(2, parts.size)
        val first = parts[0].jsonObject["functionResponse"]!!.jsonObject
        assertEquals("web_search", first["name"]!!.jsonPrimitive.content)
        assertEquals("result a", first["response"]!!.jsonObject["result"]!!.jsonPrimitive.content)
        assertEquals("web_fetch", parts[1].jsonObject["functionResponse"]!!.jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun assistantCallsCarryArgumentsAndTheirThoughtSignature() {
        val modelTurn = contents[1].jsonObject
        assertEquals("model", modelTurn["role"]!!.jsonPrimitive.content)
        val parts = modelTurn["parts"]!!.jsonArray
        assertEquals("Searching.", parts[0].jsonObject["text"]!!.jsonPrimitive.content)
        val signedCall = parts[1].jsonObject
        assertEquals("c2ln", signedCall["thoughtSignature"]!!.jsonPrimitive.content)
        assertEquals("a", (signedCall["functionCall"]!!.jsonObject["args"] as JsonObject)["query"]!!.jsonPrimitive.content)
        assertNull(parts[2].jsonObject["thoughtSignature"])
    }

    @Test
    fun generatedCallIdsAreNotSentBack() {
        // Ids Jonaki invented are unknown to Gemini; only service ids go back.
        val call = contents[1].jsonObject["parts"]!!.jsonArray[1].jsonObject["functionCall"]!!.jsonObject
        assertFalse(call.containsKey("id"))
    }

    @Test
    fun toolsUseJsonSchemaDeclarations() {
        val declaration = body["tools"]!!.jsonArray[0].jsonObject["functionDeclarations"]!!.jsonArray[0].jsonObject
        assertEquals("web_search", declaration["name"]!!.jsonPrimitive.content)
        assertEquals("object", declaration["parametersJsonSchema"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(1000, body["generationConfig"]!!.jsonObject["maxOutputTokens"]!!.jsonPrimitive.content.toInt())
    }
}
