package app.jonaki.providers.openaicompatible

import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.providerapi.ChatRequest
import app.jonaki.core.providerapi.ThinkingLevel
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Builds the JSON body of a streaming chat completions request. */
object ChatCompletionRequestBody {
    fun build(
        request: ChatRequest,
        askForCost: Boolean = false,
        routing: OpenRouterRouting = OpenRouterRouting.AUTOMATIC,
        thinkingField: ThinkingField = ThinkingField.NONE,
    ): JsonObject = buildJsonObject {
        routing.providerBlock()?.let { block -> put("provider", block) }
        put("model", request.model)
        put("stream", true)
        // Without this the stream carries no token counts.
        putJsonObject("stream_options") { put("include_usage", true) }
        if (askForCost) {
            // OpenRouter's own field; it adds usage.cost in USD to the last chunk.
            putJsonObject("usage") { put("include", true) }
        }
        request.maxOutputTokens?.let { put("max_tokens", it) }
        request.thinkingLevel?.let { level -> putThinking(level, thinkingField) }
        putJsonArray("messages") {
            addJsonObject {
                put("role", "system")
                put("content", request.systemPrompt)
            }
            for (message in request.messages) {
                add(messageJson(message))
            }
        }
        if (request.tools.isNotEmpty()) {
            putJsonArray("tools") {
                for (tool in request.tools) {
                    addJsonObject {
                        put("type", "function")
                        putJsonObject("function") {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("parameters", tool.parameterSchema)
                        }
                    }
                }
            }
            if (!request.toolsCallable) {
                put("tool_choice", "none")
            }
        }
    }

    private fun messageJson(message: Message): JsonObject = when (message.role) {
        Role.USER -> userMessageJson(message)
        Role.ASSISTANT -> assistantMessageJson(message)
        Role.TOOL -> buildJsonObject {
            put("role", "tool")
            put("tool_call_id", message.toolCallId)
            put("content", message.text)
        }
    }

    /**
     * Plain text stays a string, as before images existed, so the bytes of
     * older requests do not change and the prompt cache holds (D-049).
     */
    private fun userMessageJson(message: Message): JsonObject = buildJsonObject {
        put("role", "user")
        if (message.images.isEmpty()) {
            put("content", message.text)
            return@buildJsonObject
        }
        putJsonArray("content") {
            if (message.text.isNotEmpty()) {
                addJsonObject {
                    put("type", "text")
                    put("text", message.text)
                }
            }
            for (image in message.images) {
                addJsonObject {
                    put("type", "image_url")
                    putJsonObject("image_url") { put("url", "data:${image.mimeType};base64,${image.base64Data}") }
                }
            }
        }
    }

    private fun assistantMessageJson(message: Message): JsonObject = buildJsonObject {
        put("role", "assistant")
        if (message.text.isEmpty() && message.toolCalls.isNotEmpty()) {
            put("content", JsonNull)
        } else {
            put("content", message.text)
        }
        if (message.toolCalls.isNotEmpty()) {
            putJsonArray("tool_calls") {
                for (toolCall in message.toolCalls) {
                    addJsonObject {
                        put("id", toolCall.id)
                        put("type", "function")
                        putJsonObject("function") {
                            put("name", toolCall.toolName)
                            put("arguments", toolCall.argumentsJson)
                        }
                    }
                }
            }
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putThinking(level: ThinkingLevel, field: ThinkingField) {
        when (field) {
            ThinkingField.NONE -> Unit
            ThinkingField.OPENROUTER -> putJsonObject("reasoning") {
                if (level == ThinkingLevel.OFF) {
                    put("enabled", false)
                } else {
                    put("effort", effortOf(level))
                }
            }
            ThinkingField.OPENAI -> put("reasoning_effort", if (level == ThinkingLevel.OFF) "none" else effortOf(level))
        }
    }

    private fun effortOf(level: ThinkingLevel): String = level.name.lowercase()
}
