package app.jonaki.core.agent

import app.jonaki.core.providerapi.ToolDefinition
import app.jonaki.core.toolapi.Tool

/**
 * Tools as a request lists them, sorted like the system prompt, so the
 * request bytes stay the same for the provider's prompt cache (D-005).
 * Every request that wants to share that cache builds its list here.
 */
object ToolDefinitions {
    fun of(tools: Collection<Tool>): List<ToolDefinition> = tools.sortedBy { tool -> tool.name }.map { tool ->
        ToolDefinition(name = tool.name, description = tool.promptLine, parameterSchema = tool.parameterSchema)
    }
}
