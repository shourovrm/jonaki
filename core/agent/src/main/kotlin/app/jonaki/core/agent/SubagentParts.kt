package app.jonaki.core.agent

import app.jonaki.core.model.ToolCall
import app.jonaki.core.providerapi.ChatProvider
import app.jonaki.core.providerapi.ThinkingLevel
import app.jonaki.core.providerapi.Usage
import app.jonaki.core.toolapi.SubagentModelInfo
import app.jonaki.core.toolapi.ToolOutput
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The model one subagent runs on, resolved by the app from Settings and the delegate call. */
class SubagentModel(
    /** "service:modelId". */
    val key: String,
    /** The id the service expects. */
    val modelId: String,
    val provider: ChatProvider,
    val thinkingLevel: ThinkingLevel?,
    /** From the model catalog; view_image is offered only to models that take images (D-050). */
    val acceptsImages: Boolean,
    /**
     * True when the catalog has a price or the service reports each call's
     * cost; the prompt then names the cost limit. The limit itself applies
     * to whatever costs the calls actually have.
     */
    val hasKnownPrice: Boolean,
    /** Cost in USD of one call; null when unknown. */
    val priceOf: (Usage) -> Double?,
    /** Adds viewed images to requests; null sends text only. */
    val imageMessages: ImageMessages?,
)

/** Picks the model for each subagent. The app implements it with the user's settings. */
interface SubagentModels {
    /** The user's scoped models, which the delegate call may name. */
    val scoped: List<SubagentModelInfo>

    /** Null when the model has no saved key. */
    fun modelFor(agentType: AgentType, requestedKey: String?): SubagentModel?
}

/** Budgets of one subagent (M7). */
data class SubagentLimits(
    /** Tool calls, counted one by one. */
    val maxToolSteps: Int = 10,
    /** Applies only while the calls' cost is known. */
    val costCapUsd: Double = 0.10,
    /** Long enough for three unanswered 3-minute approvals. */
    val timeLimit: Duration = 10.minutes,
    /** Wait before the one retry of an overloaded model, as for the thread's agent (D-026). */
    val retryDelay: Duration = 2.seconds,
)

enum class SubagentStop {
    COMPLETED,
    STEP_LIMIT,
    COST_LIMIT,
    TIME_LIMIT,
    FAILED,
    STOPPED,
}

data class SubagentOutcome(
    val stop: SubagentStop,
    /** Its final answer, or for a stop without one, what it wrote and found so far. */
    val answer: String,
    /** Parts skipped because an approval was not answered within 3 minutes (D-015). */
    val skipped: List<String>,
    val toolSteps: Int,
    /** Null when no call had a known cost. */
    val costUsd: Double?,
    /** Why the model call failed, for [SubagentStop.FAILED]. */
    val failure: String? = null,
)

data class SubagentStart(
    val id: String,
    /** The delegate call that started it. */
    val parentToolCallId: String,
    val orderInCall: Int,
    val agentType: String,
    val task: String,
    val modelKey: String,
)

enum class SubagentStepStatus {
    DONE,
    FAILED,
    DENIED,
    SKIPPED,
}

/**
 * Receives every step of every subagent as it happens (D-005). The app saves
 * them in the thread; calls of parallel subagents may arrive at the same time.
 */
interface SubagentRecorder {
    suspend fun subagentStarted(start: SubagentStart)

    /** [stepCall] carries the id the step is saved under, unique within the thread. */
    suspend fun stepStarted(subagentId: String, stepCall: ToolCall)

    suspend fun stepFinished(subagentId: String, stepCall: ToolCall, output: ToolOutput, status: SubagentStepStatus)

    suspend fun modelCallFinished(subagentId: String, modelKey: String, usage: Usage, costUsd: Double?)

    /**
     * The cost of an ask_parent answer, already saved as a usage row of the
     * thread; it adds to the subagent's own total.
     */
    suspend fun askCostAdded(subagentId: String, costUsd: Double)

    /** Prose the subagent wrote in a turn, for the latest line on its folded card. */
    suspend fun textWritten(subagentId: String, text: String)

    /** [answerText] is what goes back to the thread's agent. */
    suspend fun subagentFinished(subagentId: String, outcome: SubagentOutcome, answerText: String)
}

/** [costUsd] is the call's cost, counted against the asking subagent's budget; null when unknown. */
sealed interface ParentAnswer {
    val costUsd: Double?

    data class Answered(val text: String, override val costUsd: Double? = null) : ParentAnswer

    data class Failed(val message: String, override val costUsd: Double? = null) : ParentAnswer
}

/**
 * Answers a subagent's ask_parent with one model call on the thread's model
 * and the thread's conversation so far, without tools. The app implements it.
 */
fun interface ParentAsker {
    /** [delegateToolCallId] marks where the thread's conversation is cut; [agentLabel] names who asks. */
    suspend fun ask(question: String, agentLabel: String, delegateToolCallId: String): ParentAnswer
}

/**
 * Everything one subagent did, kept outside its loop, so that a subagent
 * stopped by the time limit still returns what it has.
 */
internal class SubagentProgress {
    private val texts = mutableListOf<String>()
    private val toolResults = mutableListOf<Pair<String, String>>()
    private val skippedParts = mutableListOf<String>()
    private var knownCostUsd: Double? = null

    var toolSteps: Int = 0
        private set

    val costUsd: Double?
        get() = knownCostUsd

    @Synchronized
    fun addText(text: String) {
        if (text.isNotBlank()) {
            texts += text.trim()
        }
    }

    @Synchronized
    fun countToolStep() {
        toolSteps += 1
    }

    @Synchronized
    fun addToolResult(toolName: String, text: String) {
        toolResults += toolName to text
    }

    @Synchronized
    fun addSkipped(part: String) {
        skippedParts += part
    }

    @Synchronized
    fun addCost(costUsd: Double?) {
        if (costUsd != null) {
            knownCostUsd = (knownCostUsd ?: 0.0) + costUsd
        }
    }

    @Synchronized
    fun finished(stop: SubagentStop, finalText: String): SubagentOutcome =
        SubagentOutcome(stop, finalText.trim(), skippedParts.toList(), toolSteps, knownCostUsd)

    /** For a stop without a final answer: its texts, then its last tool results, cut short. */
    @Synchronized
    fun stoppedEarly(stop: SubagentStop, failure: String? = null): SubagentOutcome {
        val parts = mutableListOf<String>()
        parts += texts
        val lastResults = toolResults.takeLast(RESULTS_KEPT)
        if (lastResults.isNotEmpty()) {
            parts += "Results of its last steps:"
            for ((toolName, text) in lastResults) {
                parts += "[$toolName]\n${text.take(RESULT_CHARACTERS_KEPT)}"
            }
        }
        return SubagentOutcome(stop, parts.joinToString("\n\n"), skippedParts.toList(), toolSteps, knownCostUsd, failure)
    }

    private companion object {
        const val RESULTS_KEPT = 3
        const val RESULT_CHARACTERS_KEPT = 3_000
    }
}
