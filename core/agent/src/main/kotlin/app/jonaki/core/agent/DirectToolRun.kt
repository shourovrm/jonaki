package app.jonaki.core.agent

import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.model.ToolCall
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * Runs a tool call that the user asked for directly, with no model turn in
 * between (media mode in the message box). It records the same events in
 * the same order as [AgentLoop] does for a call the model made: an assistant
 * message that holds the one call and no text, the step starting, the step
 * finishing with the tool's result, and the end of the run. So the chat, the
 * saved rows and the history that later requests are built from look like an
 * agent-made call.
 *
 * A tool may not be done after one call (a video can outlive the tool's time
 * limit). Then [run]'s `nextCall` gets the result and may return the arguments
 * of a follow-up call to the same tool, which is recorded as its own assistant
 * message, step and result, up to `maxCalls` calls in all. The run ends once,
 * after the last call.
 *
 * It asks no [PermissionBroker]: the user's own press of Send is the
 * approval for the call. It takes no provider and no guard, so it can
 * neither call the chat model nor be reached by one. The loop never calls it;
 * only the app's send-in-media-mode path does.
 */
class DirectToolRun(private val recorder: StepRecorder) {
    /**
     * The result of the last call [run] made; null before any call finished.
     * With no model turn after the call, nobody explains a failure to the
     * user, so the caller reads it here and shows the reason itself.
     */
    var lastOutput: ToolOutput? = null
        private set

    /**
     * The run's outcome: Completed with no text, or Stopped when the calling coroutine was cancelled.
     * [callId] names the first call; [newCallId] names each follow-up call.
     */
    suspend fun run(
        tool: Tool,
        arguments: JsonObject,
        toolContext: ToolContext,
        callId: String,
        nextCall: (ToolOutput) -> JsonObject? = { null },
        maxCalls: Int = 1,
        newCallId: () -> String = { UUID.randomUUID().toString() },
    ): RunOutcome {
        var callArguments = arguments
        var currentCallId = callId
        var callsMade = 0
        while (true) {
            val output = runOneCall(tool, callArguments, toolContext, currentCallId)
            lastOutput = output
            callsMade++
            val followUp = if (callsMade < maxCalls) nextCall(output) else null
            if (followUp == null) {
                break
            }
            callArguments = followUp
            currentCallId = newCallId()
        }
        val outcome = RunOutcome.Completed(finalText = "")
        recorder.record(AgentEvent.RunFinished(outcome))
        return outcome
    }

    private suspend fun runOneCall(tool: Tool, arguments: JsonObject, toolContext: ToolContext, callId: String): ToolOutput {
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
        return output
    }
}
