package app.jonaki.run

import app.jonaki.core.agent.AttachmentLine
import app.jonaki.core.agent.ImageMessages
import app.jonaki.core.model.ImagePart
import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.model.ToolCall
import app.jonaki.core.providerapi.ChatRequest
import app.jonaki.core.toolapi.ImageSource
import app.jonaki.core.toolapi.ViewedImages
import app.jonaki.providers.gemini.GeminiRequestBody
import app.jonaki.providers.openaicompatible.ChatCompletionRequestBody
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A scripted thread with an attached photo and a view_image call, through
 * ImageMessages into both wire formats (D-049, D-050).
 */
class ImageRequestsTest {
    private val history = listOf(
        Message(Role.USER, AttachmentLine.appendTo("What is in the photo?", listOf("inbox/photo.jpg"))),
        Message(Role.ASSISTANT, "", toolCalls = listOf(ToolCall("call_1", ViewedImages.TOOL_NAME, """{"path":"work/chart.png"}"""))),
        Message(Role.TOOL, ViewedImages.resultText(ImageSource("work/chart.png")), toolCallId = "call_1"),
    )

    private fun prepared(modelAcceptsImages: Boolean): ChatRequest {
        val imageMessages = ImageMessages({ source -> ImagePart("image/jpeg", "jpeg-of-" + source.path) }, modelAcceptsImages)
        return ChatRequest("model", "system", imageMessages.prepare(history))
    }

    @Test
    fun openAiFormatCarriesBothImagesAsDataUrls() {
        val messages = ChatCompletionRequestBody.build(prepared(modelAcceptsImages = true))["messages"]!!.jsonArray

        // system, user, assistant, tool, user with the viewed image
        assertEquals(listOf("system", "user", "assistant", "tool", "user"), messages.map { it.jsonObject.role() })
        val attached = messages[1].jsonObject["content"]!!.jsonArray
        assertEquals("data:image/jpeg;base64,jpeg-of-inbox/photo.jpg", attached[1].imageUrl())
        assertTrue(messages[3].jsonObject["content"]!!.jsonPrimitive.content.startsWith("Image shown: work/chart.png"))
        val viewed = messages[4].jsonObject["content"]!!.jsonArray
        assertEquals("[view_image: work/chart.png]", viewed[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("data:image/jpeg;base64,jpeg-of-work/chart.png", viewed[1].imageUrl())
    }

    @Test
    fun geminiFormatPutsTheViewedImageInAUserTurnAfterTheFunctionResponse() {
        val contents = GeminiRequestBody.build(prepared(modelAcceptsImages = true))["contents"]!!.jsonArray

        assertEquals(listOf("user", "model", "user", "user"), contents.map { it.jsonObject.role() })
        assertEquals("jpeg-of-inbox/photo.jpg", contents[0].parts()[1].jsonObject["inlineData"]!!.jsonObject["data"]!!.jsonPrimitive.content)
        assertTrue(contents[2].parts()[0].jsonObject.containsKey("functionResponse"))
        assertEquals("jpeg-of-work/chart.png", contents[3].parts()[1].jsonObject["inlineData"]!!.jsonObject["data"]!!.jsonPrimitive.content)
    }

    @Test
    fun aTextOnlyModelGetsPlainTextNotes() {
        val body = ChatCompletionRequestBody.build(prepared(modelAcceptsImages = false)).toString()

        assertTrue(body.contains("[inbox/photo.jpg is an image; this model cannot see images.]"))
        assertTrue(body.contains("[work/chart.png: this model cannot see images.]"))
        assertTrue(!body.contains("image_url"))
    }

    private fun JsonObject.role(): String = this["role"]!!.jsonPrimitive.content

    private fun kotlinx.serialization.json.JsonElement.imageUrl(): String =
        jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content

    private fun kotlinx.serialization.json.JsonElement.parts() = jsonObject["parts"]!!.jsonArray
}
