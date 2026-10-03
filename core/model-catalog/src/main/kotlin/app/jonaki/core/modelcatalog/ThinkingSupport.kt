package app.jonaki.core.modelcatalog

/** Whether a model takes the thinking level the user can pick (D-057). */
object ThinkingSupport {
    private val openAiReasoningModels = listOf("gpt-5", "gpt-6", "o3", "o4")

    fun isSupported(modelKey: String, info: ModelInfo?): Boolean {
        val modelId = ModelKey.modelOf(modelKey)
        return when (ModelKey.serviceOf(modelKey)) {
            "openrouter" -> info?.supportsThinkingLevel == true
            "gemini" -> modelId.startsWith("gemini-3") || modelId.startsWith("gemini-2.5")
            "openai" -> openAiReasoningModels.any { prefix -> modelId.startsWith(prefix) }
            // llama.cpp passes Off to the chat template as enable_thinking=false; a model without thinking ignores it (D-133).
            "local" -> true
            // DeepSeek, GLM, MiMo and Ollama take no level through their chat API.
            else -> false
        }
    }
}
