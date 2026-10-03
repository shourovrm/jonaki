package app.jonaki.core.agent

import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.model.ToolCall
import app.jonaki.core.providerapi.ChatProvider
import app.jonaki.core.providerapi.ChatRequest
import app.jonaki.core.providerapi.ThinkingLevel
import app.jonaki.core.providerapi.ToolDefinition
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class AgentSettings(
    val model: String,
    val systemPrompt: String,
    /** Model turns that may call tools for one user message (D-005). */
    val stepBudget: Int = 15,
    val maxOutputTokens: Int? = null,
    /** Null leaves the model's own default (D-057). */
    val thinkingLevel: ThinkingLevel? = null,
)

/**
 * Runs one user message to its answer: ask the model, run the tools it
 * calls, send the results back, and repeat until it answers in text. Every
 * step goes to the [recorder] as it happens. Cancelling the calling coroutine
 * is the Stop button: the partial answer is recorded, then the cancellation
 * continues to the caller. Read-only calls of one turn run side by side
 * through a [ToolCallScheduler] (D-080).
 */
class AgentLoop(
    private val provider: ChatProvider,
    tools: List<Tool>,
    private val toolContext: ToolContext,
    private val permissionBroker: PermissionBroker,
    private val recorder: StepRecorder,
    private val settings: AgentSettings,
    /** Adds images from the thread folder before each request; null sends text only (D-049). */
    private val imageMessages: ImageMessages? = null,
    /** Spaces web searches that run side by side; tests pass a virtual clock. */
    waitTimer: WaitTimer = WaitTimer.REAL,
) {
    private val scheduler = ToolCallScheduler(waitTimer)

    // Calls running side by side record at the same time; the recorder saves one event at a time.
    private val recorderLock = Mutex()

    private val toolsByName: Map<String, Tool> = tools.associateBy { tool -> tool.name }

    private val toolDefinitions: List<ToolDefinition> = ToolDefinitions.of(tools)

    suspend fun run(history: List<Message>): RunOutcome {
        val conversation = history.toMutableList()
        var toolTurnsUsed = 0
        while (true) {
            val budgetUsedUp = toolDefinitions.isNotEmpty() && toolTurnsUsed >= settings.stepBudget
            if (budgetUsedUp) {
                conversation += Message(Role.USER, BUDGET_NOTICE)
            }
            val toolsForThisTurn = if (budgetUsedUp) emptyList() else toolDefinitions

            val turn = streamOneTurn(conversation, toolsForThisTurn)
            if (turn is TurnResult.Failed) {
                return finish(RunOutcome.ProviderFailed(turn.message, turn.retryable))
            }
            val answered = turn as TurnResult.Answered
            conversation += answered.message
            record(AgentEvent.AssistantMessage(answered.message, answered.usage))

            if (budgetUsedUp) {
                // Tool calls are ignored here: the model was offered no tools for this turn.
                return finish(RunOutcome.BudgetReached(answered.message.text))
            }
            if (answered.message.toolCalls.isEmpty()) {
                return finish(RunOutcome.Completed(answered.message.text))
            }

            toolTurnsUsed += 1
            conversation += runToolCalls(answered.message.toolCalls)
        }
    }

    private suspend fun finish(outcome: RunOutcome): RunOutcome {
        record(AgentEvent.RunFinished(outcome))
        return outcome
    }

    private suspend fun streamOneTurn(conversation: List<Message>, tools: List<ToolDefinition>): TurnResult {
        val request = ChatRequest(
            model = settings.model,
            systemPrompt = settings.systemPrompt,
            messages = imageMessages?.prepare(conversation) ?: conversation.toList(),
            tools = tools,
            maxOutputTokens = settings.maxOutputTokens,
            thinkingLevel = settings.thinkingLevel,
        )
        val streamedText = StringBuilder()
        try {
            return streamTurn(
                provider = provider,
                request = request,
                onTextDelta = { delta ->
                    streamedText.append(delta)
                    record(AgentEvent.TextDelta(delta))
                },
                onReasoningDelta = { delta -> record(AgentEvent.ReasoningDelta(delta)) },
            )
        } catch (cancellation: CancellationException) {
            recordStop(streamedText.toString())
            throw cancellation
        }
    }

    private suspend fun recordStop(partialText: String) {
        // The coroutine is already cancelled; NonCancellable lets the last saves finish.
        withContext(NonCancellable) {
            if (partialText.isNotEmpty()) {
                record(AgentEvent.AssistantMessage(Message(Role.ASSISTANT, partialText), usage = null))
            }
            record(AgentEvent.RunFinished(RunOutcome.Stopped(partialText)))
        }
    }

    private suspend fun record(event: AgentEvent) {
        recorderLock.withLock { recorder.record(event) }
    }

    /** Stop cancels every running call of the turn; the run's stop is recorded once. */
    private suspend fun runToolCalls(toolCalls: List<ToolCall>): List<Message> {
        val finishedCalls = try {
            scheduler.runAll(
                toolCalls = toolCalls,
                runsAlongsideOthers = { toolCall -> ToolCallScheduler.readsOnly(toolsByName[toolCall.toolName], toolCall) },
                run = ::runToolCall,
                inCallOrder = { toolCall, finished -> record(AgentEvent.ToolFinished(toolCall, finished.output, finished.message)) },
            )
        } catch (cancellation: CancellationException) {
            recordStop(partialText = "")
            throw cancellation
        }
        return finishedCalls.map { finished -> finished.message }
    }

    private class FinishedCall(val output: ToolOutput, val message: Message)

    private suspend fun runToolCall(toolCall: ToolCall): FinishedCall {
        record(AgentEvent.ToolStarted(toolCall))
        val output = outputFor(toolCall)
        val message = Message(role = Role.TOOL, text = output.text, toolCallId = toolCall.id)
        return FinishedCall(output, message)
    }

    private suspend fun outputFor(toolCall: ToolCall): ToolOutput {
        val tool = toolsByName[toolCall.toolName]
            ?: return ToolOutput.error(
                "there is no tool named ${toolCall.toolName}",
                "Available tools: ${toolsByName.keys.sorted().joinToString(", ")}.",
            )
        val arguments = parseToolArguments(toolCall.argumentsJson)
            ?: return ToolOutput.error(
                "the arguments for ${tool.name} are not a JSON object",
                "Call ${tool.name} again with arguments that match its schema.",
            )
        if (!permissionBroker.mayRun(tool, toolCall)) {
            return ToolOutput.error(
                "the user denied ${tool.name}",
                "Do not retry it; continue without it or ask the user what to do instead.",
            )
        }
        return runToolWithTimeLimit(tool, arguments, toolContext.forCall(toolCall.id))
    }

    private companion object {
        const val BUDGET_NOTICE =
            "[The step budget for this message is used up. Answer now with what you have found, " +
                "without calling tools, and say what is still missing.]"
    }
}
