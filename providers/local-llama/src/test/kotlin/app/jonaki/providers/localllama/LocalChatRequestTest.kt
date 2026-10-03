package app.jonaki.providers.localllama

import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.model.ToolCall
import app.jonaki.core.providerapi.ChatRequest
import app.jonaki.core.providerapi.ThinkingLevel
import app.jonaki.core.providerapi.ToolDefinition
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalChatRequestTest {
    private val searchTool = ToolDefinition(
        name = "web_search",
        description = "Searches the web",
        parameterSchema = buildJsonObject { put("type", "object") },
    )

    private val conversation = listOf(
        Message(Role.USER, "Weather in Dhaka?"),
        Message(Role.ASSISTANT, "", toolCalls = listOf(ToolCall("call_1", "web_search", """{"query":"Dhaka weather"}"""))),
        Message(Role.TOOL, "31 °C, humid", toolCallId = "call_1"),
        Message(Role.ASSISTANT, "It is 31 °C."),
    )

    private fun build(
        thinkingLevel: ThinkingLevel? = null,
        toolsCallable: Boolean = true,
        tools: List<ToolDefinition> = listOf(searchTool),
        maxOutputTokens: Int? = null,
    ): JsonObject = LocalChatRequest.build(
        ChatRequest(
            model = "Qwen3.5-0.8B-Q4_0.gguf",
            systemPrompt = "You are Jonaki.",
            messages = conversation,
            tools = tools,
            thinkingLevel = thinkingLevel,
            toolsCallable = toolsCallable,
            maxOutputTokens = maxOutputTokens,
        ),
    )

    @Test
    fun systemPromptComesFirstThenEveryMessageInOrder() {
        val messages = build()["messages"]!!.jsonArray
        val roles = messages.map { message -> message.jsonObject["role"]!!.jsonPrimitive.content }
        assertEquals(listOf("system", "user", "assistant", "tool", "assistant"), roles)
        assertEquals("You are Jonaki.", messages[0].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("Weather in Dhaka?", messages[1].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun assistantToolCallKeepsArgumentsAsTextAndHasNullContent() {
        val assistant = build()["messages"]!!.jsonArray[2].jsonObject
        assertEquals(JsonNull, assistant["content"])
        val call = assistant["tool_calls"]!!.jsonArray.single().jsonObject
        assertEquals("call_1", call["id"]!!.jsonPrimitive.content)
        assertEquals("function", call["type"]!!.jsonPrimitive.content)
        val function = call["function"]!!.jsonObject
        assertEquals("web_search", function["name"]!!.jsonPrimitive.content)
        assertEquals("""{"query":"Dhaka weather"}""", function["arguments"]!!.jsonPrimitive.content)
    }

    @Test
    fun toolResultCarriesItsCallIdAndTheToolName() {
        val result = build()["messages"]!!.jsonArray[3].jsonObject
        assertEquals("call_1", result["tool_call_id"]!!.jsonPrimitive.content)
        assertEquals("web_search", result["name"]!!.jsonPrimitive.content)
        assertEquals("31 °C, humid", result["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun toolsAreFunctionsWithTheirSchema() {
        val request = build()
        val function = request["tools"]!!.jsonArray.single().jsonObject["function"]!!.jsonObject
        assertEquals("web_search", function["name"]!!.jsonPrimitive.content)
        assertEquals("Searches the web", function["description"]!!.jsonPrimitive.content)
        assertEquals("object", function["parameters"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("auto", request["tool_choice"]!!.jsonPrimitive.content)
    }

    @Test
    fun toolsThatMayNotBeCalledAreListedWithChoiceNone() {
        val request = build(toolsCallable = false)
        assertEquals(1, request["tools"]!!.jsonArray.size)
        assertEquals("none", request["tool_choice"]!!.jsonPrimitive.content)
    }

    @Test
    fun noToolsLeavesToolsAndChoiceOut() {
        val request = build(tools = emptyList())
        assertFalse("tools" in request)
        assertFalse("tool_choice" in request)
    }

    @Test
    fun thinkingOffDisablesThinkingAndOtherLevelsEnableIt() {
        assertFalse(build(thinkingLevel = ThinkingLevel.OFF)["enable_thinking"]!!.jsonPrimitive.boolean)
        assertTrue(build(thinkingLevel = ThinkingLevel.HIGH)["enable_thinking"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun noThinkingLevelLeavesTheTemplateDefault() {
        assertNull(build(thinkingLevel = null)["enable_thinking"])
    }

    @Test
    fun outputLimitIsPassedOnlyWhenSet() {
        assertEquals(400, build(maxOutputTokens = 400)["max_tokens"]!!.jsonPrimitive.int)
        assertNull(build()["max_tokens"])
    }
}
