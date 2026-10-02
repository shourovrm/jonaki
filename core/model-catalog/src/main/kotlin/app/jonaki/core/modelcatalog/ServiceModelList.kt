package app.jonaki.core.modelcatalog

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Reads a service's own model list in the OpenAI format (GET /models:
 * `{"data": [{"id": "gpt-6-luna"}, …]}`), which OpenAI, MiniMax, Qwen and
 * Ollama answer (D-MCP-5). The list carries ids only, no prices.
 */
object ServiceModelList {
    /**
     * Words in ids of models that cannot chat: embeddings, speech, images,
     * moderation. OpenAI's list has about a hundred ids, most of them these.
     */
    private val notChatWords = listOf(
        "embed", "tts", "whisper", "transcribe", "dall-e", "image", "moderation", "realtime", "audio", "search",
        "davinci", "babbage",
    )

    fun parseIds(json: String): List<String> {
        val root = runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return emptyList()
        val data = root["data"] as? JsonArray ?: return emptyList()
        return data
            .mapNotNull { element -> ((element as? JsonObject)?.get("id") as? JsonPrimitive)?.contentOrNull }
            .filter { id -> isChatModel(id) }
    }

    private fun isChatModel(id: String): Boolean {
        val lowercase = id.lowercase()
        return notChatWords.none { word -> lowercase.contains(word) }
    }
}
