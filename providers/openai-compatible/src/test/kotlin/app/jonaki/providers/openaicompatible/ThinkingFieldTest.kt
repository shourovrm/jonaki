package app.jonaki.providers.openaicompatible

import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.providerapi.ChatRequest
import app.jonaki.core.providerapi.ThinkingLevel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThinkingFieldTest {
    private fun body(level: ThinkingLevel?, field: ThinkingField): JsonObject =
        ChatCompletionRequestBody.build(
            ChatRequest("m", "system", listOf(Message(Role.USER, "hi")), thinkingLevel = level),
            thinkingField = field,
        )

    @Test
    fun openRouterTakesAnEffort() {
        val reasoning = body(ThinkingLevel.MEDIUM, ThinkingField.OPENROUTER)["reasoning"] as JsonObject

        assertEquals("medium", reasoning["effort"]!!.jsonPrimitive.content)
    }

    @Test
    fun openRouterOffDisablesReasoning() {
        val reasoning = body(ThinkingLevel.OFF, ThinkingField.OPENROUTER)["reasoning"] as JsonObject

        assertEquals("false", reasoning["enabled"]!!.jsonPrimitive.content)
        assertNull(reasoning["effort"])
    }

    @Test
    fun openAiTakesReasoningEffort() {
        assertEquals("high", body(ThinkingLevel.HIGH, ThinkingField.OPENAI)["reasoning_effort"]!!.jsonPrimitive.content)
        assertEquals("none", body(ThinkingLevel.OFF, ThinkingField.OPENAI)["reasoning_effort"]!!.jsonPrimitive.content)
    }

    @Test
    fun defaultOrAServiceWithoutTheFieldSendsNothing() {
        assertNull(body(null, ThinkingField.OPENROUTER)["reasoning"])
        val deepSeek = body(ThinkingLevel.HIGH, ThinkingField.NONE)
        assertNull(deepSeek["reasoning"])
        assertNull(deepSeek["reasoning_effort"])
    }

    @Test
    fun onlyOpenRouterAndOpenAiHaveTheField() {
        assertEquals(ThinkingField.OPENROUTER, ProviderPresets.openRouter.thinkingField)
        assertEquals(ThinkingField.OPENAI, ProviderPresets.openAi.thinkingField)
        assertEquals(ThinkingField.NONE, ProviderPresets.deepSeek.thinkingField)
    }
}
