package app.jonaki.core.modelcatalog

import org.junit.Assert.assertEquals
import org.junit.Test

class ThinkingSupportTest {
    private fun info(service: String, id: String, supports: Boolean = false) =
        ModelInfo(service, id, id, null, null, null, supportsThinkingLevel = supports)

    @Test
    fun openRouterFollowsTheModelList() {
        assertEquals(true, ThinkingSupport.isSupported("openrouter:z-ai/glm-5.3-flash", info("openrouter", "z-ai/glm-5.3-flash", supports = true)))
        assertEquals(false, ThinkingSupport.isSupported("openrouter:x/old", info("openrouter", "x/old")))
        assertEquals(false, ThinkingSupport.isSupported("openrouter:x/unknown", null))
    }

    @Test
    fun geminiFromVersionTwoPointFive() {
        assertEquals(true, ThinkingSupport.isSupported("gemini:gemini-3.8-flash", null))
        assertEquals(true, ThinkingSupport.isSupported("gemini:gemini-2.5-pro", null))
        assertEquals(false, ThinkingSupport.isSupported("gemini:gemini-2.0-flash", null))
    }

    @Test
    fun openAiReasoningModels() {
        assertEquals(true, ThinkingSupport.isSupported("openai:gpt-5-mini", null))
        assertEquals(false, ThinkingSupport.isSupported("openai:gpt-4o", null))
    }

    @Test
    fun localModelsTakeTheSettingThroughTheirChatTemplate() {
        assertEquals(true, ThinkingSupport.isSupported("local:Qwen3.5-2B-Q4_0.gguf", null))
    }

    @Test
    fun servicesWithoutTheSettingNeverOfferIt() {
        assertEquals(false, ThinkingSupport.isSupported("deepseek:deepseek-flash", null))
        assertEquals(false, ThinkingSupport.isSupported("glm:glm-5.3-flash", null))
    }

    @Test
    fun theModelListMarksReasoningModels() {
        val json = """{"data":[
            {"id":"a/think","name":"Think","supported_parameters":["tools","reasoning"]},
            {"id":"b/plain","name":"Plain","supported_parameters":["tools"]}
        ]}"""

        val models = OpenRouterModels.parse(json)

        assertEquals(listOf(true, false), models.map { model -> model.supportsThinkingLevel })
    }
}
