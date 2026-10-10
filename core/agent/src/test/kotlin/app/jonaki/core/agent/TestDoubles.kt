package app.jonaki.core.agent

import app.jonaki.core.model.ToolCall
import app.jonaki.core.providerapi.ChatProvider
import app.jonaki.core.providerapi.ChatRequest
import app.jonaki.core.providerapi.FinishReason
import app.jonaki.core.providerapi.StreamEvent
import app.jonaki.core.providerapi.Usage
import app.jonaki.core.toolapi.Capability
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Replays one scripted flow per model turn and keeps every request it received. */
class ScriptedProvider(vararg turns: Flow<StreamEvent>) : ChatProvider {
    private val remainingTurns = ArrayDeque(turns.toList())
    val requests = mutableListOf<ChatRequest>()

    override val id: String = "scripted"

    override fun stream(request: ChatRequest): Flow<StreamEvent> {
        requests += request
        return remainingTurns.removeFirstOrNull() ?: error("The script has no turn for request ${requests.size}")
    }
}

fun textTurn(vararg chunks: String): Flow<StreamEvent> = flowOf(
    *chunks.map { chunk -> StreamEvent.TextDelta(chunk) }.toTypedArray(),
    StreamEvent.Finished(FinishReason.STOP, Usage(inputTokens = 10, outputTokens = 5)),
)

fun toolCallTurn(vararg toolCalls: ToolCall): Flow<StreamEvent> = flowOf(
    *toolCalls.map { toolCall -> StreamEvent.ToolCallReady(toolCall) }.toTypedArray(),
    StreamEvent.Finished(FinishReason.TOOL_CALLS, usage = null),
)

/** Streams some text, then never finishes, like a provider that hangs. */
fun hangingTurn(firstChunk: String): Flow<StreamEvent> = flow {
    emit(StreamEvent.TextDelta(firstChunk))
    delay(Long.MAX_VALUE)
}

class FakeTool(
    override val name: String,
    override val sideEffect: SideEffect = SideEffect.READ_ONLY,
    override val timeLimit: Duration = 5.seconds,
    /** Per-call cost, like the phone tool's; null gives every call [sideEffect]. */
    private val sideEffectOfCall: ((JsonObject) -> SideEffect)? = null,
    private val veryRisky: Boolean = false,
    /** Null keeps the default: a call sends out when it is [SideEffect.CHANGES]. */
    private val sendsOut: Boolean? = null,
    /** Null for a tool whose results are Jonaki's own; else the source label of its outside content. */
    private val outsideSource: String? = null,
    /** Null for a tool that contacts no address; else the name of the argument that holds the address it contacts. */
    private val addressArgument: String? = null,
    private val behaviour: suspend (JsonObject) -> ToolOutput = { arguments ->
        ToolOutput.success("$name got $arguments")
    },
) : Tool {
    val receivedArguments = mutableListOf<JsonObject>()

    override val promptLine: String = "$name: a fake tool for tests"
    override val guidelines: List<String> = listOf("Use $name only in tests.")
    override val parameterSchema: JsonObject = buildJsonObject { put("type", "object") }
    override val requiredCapabilities: Set<Capability> = emptySet()

    override fun sideEffectOf(arguments: JsonObject): SideEffect = sideEffectOfCall?.invoke(arguments) ?: sideEffect

    override fun isVeryRiskyOf(arguments: JsonObject): Boolean = veryRisky

    override fun sendsOutOf(arguments: JsonObject): Boolean = sendsOut ?: super.sendsOutOf(arguments)

    override fun outsideContentSourceOf(arguments: JsonObject): String? = outsideSource

    override fun contactedAddressOf(arguments: JsonObject): String? =
        addressArgument?.let { argumentName -> (arguments[argumentName] as? JsonPrimitive)?.content }

    override suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput {
        receivedArguments += arguments
        return behaviour(arguments)
    }
}

fun call(id: String, toolName: String, vararg arguments: Pair<String, String>): ToolCall {
    val json = JsonObject(arguments.associate { (key, value) -> key to JsonPrimitive(value) })
    return ToolCall(id = id, toolName = toolName, argumentsJson = json.toString())
}

/** Answers every approval request with the same decision and counts the requests. */
class FixedApprover(private val decision: ApprovalDecision) : ApprovalRequester {
    val requests = mutableListOf<ApprovalRequest>()

    override suspend fun requestApproval(request: ApprovalRequest): ApprovalDecision {
        requests += request
        return decision
    }
}
