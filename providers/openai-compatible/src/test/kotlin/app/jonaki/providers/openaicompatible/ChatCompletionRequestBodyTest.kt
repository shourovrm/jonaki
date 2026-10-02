package app.jonaki.providers.openaicompatible

import app.jonaki.core.model.ImagePart
import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.providerapi.ChatRequest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCompletionRequestBodyTest {
    private val photo = ImagePart("image/jpeg", "/9j/AAAA")

    private fun userMessageOf(message: Message): JsonObject =
        ChatCompletionRequestBody.build(ChatRequest("m", "system", listOf(message)))["messages"]!!.jsonArray[1].jsonObject

    @Test
    fun textOnlyUserMessageKeepsStringContent() {
        val user = userMessageOf(Message(Role.USER, "hello"))
        assertEquals(JsonPrimitive("hello"), user["content"])
    }

    @Test
    fun imagesBecomeImageUrlPartsWithDataUrls() {
        val user = userMessageOf(Message(Role.USER, "What is this?", images = listOf(photo)))
        val parts = user["content"]!!.jsonArray
        assertEquals(2, parts.size)
        assertEquals("text", parts[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("What is this?", parts[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("image_url", parts[1].jsonObject["type"]!!.jsonPrimitive.content)
        val url = parts[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content
        assertEquals("data:image/jpeg;base64,/9j/AAAA", url)
    }

    @Test
    fun sameMessageGivesSameBytes() {
        val message = Message(Role.USER, "a", images = listOf(photo, photo))
        val first = ChatCompletionRequestBody.build(ChatRequest("m", "s", listOf(message))).toString()
        val second = ChatCompletionRequestBody.build(ChatRequest("m", "s", listOf(message.copy()))).toString()
        assertEquals(first, second)
        assertTrue(first.contains("data:image/jpeg;base64,/9j/AAAA"))
    }

    @Test
    fun toolsCanBeSentButNotCalled() {
        val tool = app.jonaki.core.providerapi.ToolDefinition("web_search", "search", JsonObject(emptyMap()))
        val offered = ChatCompletionRequestBody.build(ChatRequest("m", "system", emptyList(), tools = listOf(tool)))
        val shownOnly = ChatCompletionRequestBody.build(
            ChatRequest("m", "system", emptyList(), tools = listOf(tool), toolsCallable = false),
        )

        assertEquals(null, offered["tool_choice"])
        assertEquals(JsonPrimitive("none"), shownOnly["tool_choice"])
        // The same tool list, so the prompt cache prefix matches the thread's requests.
        assertEquals(offered["tools"], shownOnly["tools"])
    }
}
