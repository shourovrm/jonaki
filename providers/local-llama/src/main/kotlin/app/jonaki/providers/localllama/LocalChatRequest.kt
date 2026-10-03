package app.jonaki.providers.localllama

import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.providerapi.ChatRequest
import app.jonaki.core.providerapi.ThinkingLevel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The request the JNI layer reads: OpenAI-style messages and tools, which
 * llama.cpp's common_chat_msgs_parse_oaicompat and
 * common_chat_tools_parse_oaicompat take, plus the switches of one turn.
 */
object LocalChatRequest {
    fun build(request: ChatRequest): JsonObject = buildJsonObject {
        put("messages", messagesJson(request))
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
            put("tool_choice", if (request.toolsCallable) "auto" else "none")
        }
        // Without a level the chat template's own default applies, which thinks for Qwen3.5.
        request.thinkingLevel?.let { level -> put("enable_thinking", level != ThinkingLevel.OFF) }
        request.maxOutputTokens?.let { limit -> put("max_tokens", limit) }
    }

    private fun messagesJson(request: ChatRequest): JsonArray = buildJsonArray {
        addJsonObject {
            put("role", "system")
            put("content", request.systemPrompt)
        }
        val toolNamesById = request.messages
            .flatMap { message -> message.toolCalls }
            .associate { toolCall -> toolCall.id to toolCall.toolName }
        for (message in request.messages) {
            add(messageJson(message, toolNamesById))
        }
    }

    /** Images are left out: no local model is marked as taking them, so none should arrive. */
    private fun messageJson(message: Message, toolNamesById: Map<String, String>): JsonObject = when (message.role) {
        Role.USER -> buildJsonObject {
            put("role", "user")
            put("content", message.text)
        }
        Role.ASSISTANT -> assistantJson(message)
        Role.TOOL -> buildJsonObject {
            put("role", "tool")
            put("tool_call_id", message.toolCallId)
            // Some chat templates (Gemma's) name the function a result belongs to.
            toolNamesById[message.toolCallId]?.let { name -> put("name", name) }
            put("content", message.text)
        }
    }

    private fun assistantJson(message: Message): JsonObject = buildJsonObject {
        put("role", "assistant")
        if (message.text.isEmpty() && message.toolCalls.isNotEmpty()) {
            put("content", JsonNull)
        } else {
            put("content", message.text)
        }
        if (message.toolCalls.isEmpty()) {
            return@buildJsonObject
        }
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
