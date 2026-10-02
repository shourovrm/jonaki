package app.jonaki.providers.gemini

import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.providerapi.ChatRequest
import app.jonaki.core.providerapi.ThinkingLevel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeminiThinkingTest {
    private fun thinkingConfig(model: String, level: ThinkingLevel?): JsonObject? {
        val request = ChatRequest(model, "system", listOf(Message(Role.USER, "hi")), thinkingLevel = level)
        val generationConfig = GeminiRequestBody.build(request)["generationConfig"] as? JsonObject
        return generationConfig?.get("thinkingConfig") as? JsonObject
    }

    @Test
    fun gemini3TakesALevelAndShowsItsThoughts() {
        val config = thinkingConfig("gemini-3.8-flash", ThinkingLevel.LOW)!!

        assertEquals("low", config["thinkingLevel"]!!.jsonPrimitive.content)
        assertEquals("true", config["includeThoughts"]!!.jsonPrimitive.content)
    }

    @Test
    fun offIsTheLowestLevelGemini3Allows() {
        assertEquals("minimal", thinkingConfig("gemini-3.8-flash", ThinkingLevel.OFF)!!["thinkingLevel"]!!.jsonPrimitive.content)
    }

    @Test
    fun gemini25TakesATokenBudget() {
        assertEquals("0", thinkingConfig("gemini-2.5-flash", ThinkingLevel.OFF)!!["thinkingBudget"]!!.jsonPrimitive.content)
        assertEquals("24576", thinkingConfig("gemini-2.5-flash", ThinkingLevel.HIGH)!!["thinkingBudget"]!!.jsonPrimitive.content)
        // Gemini 2.5 Pro cannot switch thinking off; 128 tokens is its smallest budget.
        assertEquals("128", thinkingConfig("gemini-2.5-pro", ThinkingLevel.OFF)!!["thinkingBudget"]!!.jsonPrimitive.content)
    }

    @Test
    fun defaultSendsNoLevelButStillAsksForThoughts() {
        val config = thinkingConfig("gemini-3.8-flash", null)!!

        assertNull(config["thinkingLevel"])
        assertEquals("true", config["includeThoughts"]!!.jsonPrimitive.content)
    }

    @Test
    fun olderModelsGetNoThinkingConfig() {
        assertNull(thinkingConfig("gemini-2.0-flash", ThinkingLevel.HIGH))
    }
}
