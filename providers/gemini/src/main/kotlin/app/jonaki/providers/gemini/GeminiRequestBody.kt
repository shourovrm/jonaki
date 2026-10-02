package app.jonaki.providers.gemini

import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.model.ToolCall
import app.jonaki.core.providerapi.ChatRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Builds the JSON body of a Gemini `generateContent` request from a [ChatRequest]. */
object GeminiRequestBody {
    private const val GENERATED_ID_PREFIX = "gemini_"

    fun build(request: ChatRequest): JsonObject = buildJsonObject {
        putJsonObject("systemInstruction") {
            putJsonArray("parts") { addJsonObject { put("text", request.systemPrompt) } }
        }
        put("contents", contentsFrom(request.messages))
        if (request.tools.isNotEmpty()) {
            putJsonArray("tools") {
                addJsonObject {
                    putJsonArray("functionDeclarations") {
                        for (tool in request.tools) {
                            addJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                // Full JSON Schema; the older "parameters" field accepts only an OpenAPI subset.
                                put("parametersJsonSchema", tool.parameterSchema)
                            }
                        }
                    }
                }
            }
        }
        val thinkingConfig = GeminiThinking.configFor(request.model, request.thinkingLevel)
        if (request.maxOutputTokens != null || thinkingConfig != null) {
            putJsonObject("generationConfig") {
                request.maxOutputTokens?.let { maxTokens -> put("maxOutputTokens", maxTokens) }
                thinkingConfig?.let { config -> put("thinkingConfig", config) }
            }
        }
    }

    private fun contentsFrom(messages: List<Message>): JsonArray {
        val toolCallsById = messages.flatMap { it.toolCalls }.associateBy { it.id }
        val turns = mutableListOf<JsonObject>()
        var pendingFunctionResponses = mutableListOf<JsonObject>()

        fun flushFunctionResponses() {
            if (pendingFunctionResponses.isEmpty()) return
            // Gemini expects all answers to one turn's calls together in one user turn.
            turns += turn("user", pendingFunctionResponses)
            pendingFunctionResponses = mutableListOf()
        }

        for (message in messages) {
            when (message.role) {
                Role.TOOL -> pendingFunctionResponses += functionResponsePart(message, toolCallsById)
                Role.USER -> {
                    flushFunctionResponses()
                    turns += turn("user", listOf(textPart(message.text)))
                }
                Role.ASSISTANT -> {
                    flushFunctionResponses()
                    turns += turn("model", modelParts(message))
                }
            }
        }
        flushFunctionResponses()
        return JsonArray(turns)
    }

    private fun turn(role: String, parts: List<JsonObject>): JsonObject = buildJsonObject {
        put("role", role)
        put("parts", JsonArray(parts))
    }

    private fun textPart(text: String): JsonObject = buildJsonObject { put("text", text) }

    private fun modelParts(message: Message): List<JsonObject> {
        val parts = mutableListOf<JsonObject>()
        if (message.text.isNotEmpty()) parts += textPart(message.text)
        for (toolCall in message.toolCalls) {
            parts += functionCallPart(toolCall)
        }
        return parts
    }

    private fun functionCallPart(toolCall: ToolCall): JsonObject = buildJsonObject {
        val callId = GeminiToolCallId.callId(toolCall.id)
        putJsonObject("functionCall") {
            if (!callId.startsWith(GENERATED_ID_PREFIX)) put("id", callId)
            put("name", toolCall.toolName)
            put("args", argumentsObject(toolCall.argumentsJson))
        }
        GeminiToolCallId.thoughtSignature(toolCall.id)?.let { put("thoughtSignature", it) }
    }

    private fun functionResponsePart(message: Message, toolCallsById: Map<String, ToolCall>): JsonObject {
        val encodedId = message.toolCallId.orEmpty()
        val callId = GeminiToolCallId.callId(encodedId)
        val toolName = toolCallsById[encodedId]?.toolName.orEmpty()
        return buildJsonObject {
            putJsonObject("functionResponse") {
                if (!callId.startsWith(GENERATED_ID_PREFIX)) put("id", callId)
                put("name", toolName)
                putJsonObject("response") { put("result", message.text) }
            }
        }
    }

    private fun argumentsObject(argumentsJson: String): JsonObject =
        runCatching { Json.parseToJsonElement(argumentsJson) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())
}
