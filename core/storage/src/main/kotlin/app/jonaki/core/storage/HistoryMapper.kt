package app.jonaki.core.storage

import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.model.ToolCall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Turns saved message rows back into the history the agent loop sends to the model. */
object HistoryMapper {
    /** Role of rows that show a failure to the user but are never sent to the model. */
    const val ERROR_ROLE = "ERROR"

    /**
     * Role of rows that only record the usage of a background call (memory
     * extraction, compaction); never shown, never sent to the model.
     */
    const val BACKGROUND_ROLE = "BACKGROUND"

    const val STOPPED_TOOL_RESULT = "Error: this tool call was stopped before it finished."

    fun toHistory(rows: List<MessageEntity>): List<Message> {
        val history = mutableListOf<Message>()
        // Providers reject an assistant tool call that has no result, which is
        // what a Stop or a killed app leaves behind; such calls get a stopped result.
        var callsWaitingForResult = mutableListOf<ToolCall>()
        for (row in rows) {
            // A background call can finish while a run waits for tool results;
            // its row must not close the open tool calls.
            if (row.role == BACKGROUND_ROLE) {
                continue
            }
            if (row.role == Role.TOOL.name) {
                callsWaitingForResult.removeAll { call -> call.id == row.toolCallId }
                history += Message(Role.TOOL, row.text, toolCallId = row.toolCallId)
                continue
            }
            history += stoppedResults(callsWaitingForResult)
            callsWaitingForResult = mutableListOf()
            val message = messageFor(row) ?: continue
            history += message
            callsWaitingForResult.addAll(message.toolCalls)
        }
        history += stoppedResults(callsWaitingForResult)
        return history
    }

    private fun messageFor(row: MessageEntity): Message? {
        return when (row.role) {
            Role.USER.name -> Message(Role.USER, row.text)
            Role.ASSISTANT.name -> {
                val toolCalls = toolCallsFromJson(row.toolCallsJson)
                if (row.text.isEmpty() && toolCalls.isEmpty()) null else Message(Role.ASSISTANT, row.text, toolCalls)
            }
            else -> null
        }
    }

    private fun stoppedResults(calls: List<ToolCall>): List<Message> =
        calls.map { call -> Message(Role.TOOL, STOPPED_TOOL_RESULT, toolCallId = call.id) }

    fun toolCallsToJson(toolCalls: List<ToolCall>): String =
        buildJsonArray {
            for (call in toolCalls) {
                add(
                    buildJsonObject {
                        put("id", JsonPrimitive(call.id))
                        put("toolName", JsonPrimitive(call.toolName))
                        put("argumentsJson", JsonPrimitive(call.argumentsJson))
                    },
                )
            }
        }.toString()

    fun toolCallsFromJson(json: String): List<ToolCall> {
        if (json.isBlank()) {
            return emptyList()
        }
        val array = Json.parseToJsonElement(json) as? JsonArray ?: return emptyList()
        return array.map { element ->
            val fields = element.jsonObject
            ToolCall(
                id = fields.getValue("id").jsonPrimitive.content,
                toolName = fields.getValue("toolName").jsonPrimitive.content,
                argumentsJson = fields.getValue("argumentsJson").jsonPrimitive.content,
            )
        }
    }
}
