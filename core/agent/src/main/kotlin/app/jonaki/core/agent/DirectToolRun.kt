package app.jonaki.core.agent

import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.model.ToolCall
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * Runs one tool call that the user asked for directly, with no model turn in
 * between (picture mode in the message box). It records the same events in
 * the same order as [AgentLoop] does for a call the model made: an assistant
 * message that holds the one call and no text, the step starting, the step
 * finishing with the tool's result, and the end of the run. So the chat, the
 * saved rows and the history that later requests are built from look like an
 * agent-made call.
 *
 * It asks no [PermissionBroker]: the user's own press of Send is the
 * approval for this one call. It takes no provider and no guard, so it can
 * neither call the chat model nor be reached by one. The loop never calls it;
 * only the app's send-in-picture-mode path does.
 */
class DirectToolRun(private val recorder: StepRecorder) {
    /** The run's outcome: Completed with no text, or Stopped when the calling coroutine was cancelled. */
    suspend fun run(tool: Tool, arguments: JsonObject, toolContext: ToolContext, callId: String): RunOutcome {
        val toolCall = ToolCall(id = callId, toolName = tool.name, argumentsJson = arguments.toString())
        recorder.record(
            AgentEvent.AssistantMessage(Message(Role.ASSISTANT, text = "", toolCalls = listOf(toolCall)), usage = null),
        )
        recorder.record(AgentEvent.ToolStarted(toolCall))
        val output = try {
            runToolWithTimeLimit(tool, arguments, toolContext.forCall(callId))
        } catch (cancellation: CancellationException) {
            // The coroutine is already cancelled; NonCancellable lets the step be saved as stopped.
            withContext(NonCancellable) { recorder.record(AgentEvent.RunFinished(RunOutcome.Stopped(partialText = ""))) }
            throw cancellation
        }
        val resultMessage = Message(role = Role.TOOL, text = output.text, toolCallId = callId)
        recorder.record(AgentEvent.ToolFinished(toolCall, output, resultMessage))
        val outcome = RunOutcome.Completed(finalText = "")
        recorder.record(AgentEvent.RunFinished(outcome))
        return outcome
    }
}
