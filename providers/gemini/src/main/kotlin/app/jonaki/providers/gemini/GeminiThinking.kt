package app.jonaki.providers.gemini

import app.jonaki.core.providerapi.ThinkingLevel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Gemini's thinkingConfig (D-057): Gemini 3 takes a level, Gemini 2.5 a
 * token budget, older models nothing. Thought summaries are always asked for,
 * so the chat can show the reasoning (D-054).
 */
internal object GeminiThinking {
    fun configFor(model: String, level: ThinkingLevel?): JsonObject? {
        val isGemini3 = model.startsWith("gemini-3")
        val isGemini25 = model.startsWith("gemini-2.5")
        if (!isGemini3 && !isGemini25) {
            return null
        }
        return buildJsonObject {
            put("includeThoughts", true)
            if (level != null && isGemini3) {
                put("thinkingLevel", levelOf(level))
            }
            if (level != null && isGemini25) {
                put("thinkingBudget", budgetOf(level, isPro = model.contains("pro")))
            }
        }
    }

    // Gemini 3 cannot switch thinking off; "minimal" is its lowest level.
    private fun levelOf(level: ThinkingLevel): String = when (level) {
        ThinkingLevel.OFF -> "minimal"
        ThinkingLevel.LOW -> "low"
        ThinkingLevel.MEDIUM -> "medium"
        ThinkingLevel.HIGH -> "high"
    }

    private fun budgetOf(level: ThinkingLevel, isPro: Boolean): Int = when (level) {
        // 2.5 Pro has no "off"; 128 tokens is the smallest budget it accepts.
        ThinkingLevel.OFF -> if (isPro) 128 else 0
        ThinkingLevel.LOW -> 1_024
        ThinkingLevel.MEDIUM -> 8_192
        ThinkingLevel.HIGH -> 24_576
    }
}
