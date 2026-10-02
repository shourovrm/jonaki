package app.jonaki.providers.openaicompatible

import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.providerapi.ChatRequest
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
    fun build(request: ChatRequest): JsonObject = buildJsonObject {
        put("model", request.model)
        put("stream", true)
        // Without this the stream carries no token counts.
        putJsonObject("stream_options") { put("include_usage", true) }
        request.maxOutputTokens?.let { put("max_tokens", it) }
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
        }
    }

    private fun messageJson(message: Message): JsonObject = when (message.role) {
        Role.USER -> buildJsonObject {
            put("role", "user")
            put("content", message.text)
        }
        Role.ASSISTANT -> assistantMessageJson(message)
        Role.TOOL -> buildJsonObject {
            put("role", "tool")
            put("tool_call_id", message.toolCallId)
            put("content", message.text)
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
}
