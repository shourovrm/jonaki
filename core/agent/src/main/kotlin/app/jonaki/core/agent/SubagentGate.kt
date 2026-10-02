package app.jonaki.core.agent

import app.jonaki.core.model.ToolCall
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.Tool
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select

/** Waits for a time; the real one delays, tests pass a virtual clock. */
fun interface ApprovalTimer {
    suspend fun wait(duration: Duration)

    companion object {
        val REAL: ApprovalTimer = ApprovalTimer { duration -> delay(duration) }
    }
}

enum class GateAnswer {
    ALLOWED,
    DENIED,

    /** Nobody answered the card within the wait limit (D-015's 3-minute rule). */
    SKIPPED,
}

/**
 * The permission broker as one subagent meets it (D-015). The thread's
 * approval mode and allowances apply as for the thread's agent; beyond that
 * a card names the subagent, offers "Allow for this task", and counts as
 * skipped when nobody answers within [waitLimit]. The thread's own agent
 * never has such a limit. One gate serves one subagent.
 */
class SubagentGate(
    private val broker: PermissionBroker,
    private val agentLabel: String,
    private val timer: ApprovalTimer = ApprovalTimer.REAL,
    private val waitLimit: Duration = WAIT_LIMIT,
) {
    private val allowedForTask = mutableSetOf<String>()

    /** Tools granted "once" through request_tool: their next call runs without another card. */
    private val allowedOnce = mutableMapOf<String, Int>()

    /** Whether a call the subagent makes may run. [stepCall] is the call as saved in the steps. */
    suspend fun check(tool: Tool, stepCall: ToolCall): GateAnswer {
        if (runsWithoutCard(tool)) {
            return GateAnswer.ALLOWED
        }
        if (useAllowanceForOneCall(tool.name)) {
            return GateAnswer.ALLOWED
        }
        val decision = askWithinLimit(ApprovalRequest(tool.name, stepCall, SubagentAsk(agentLabel, reason = null)))
            ?: return GateAnswer.SKIPPED
        return answerFor(tool.name, decision, grantsLaterCall = false)
    }

    /**
     * request_tool: whether [tool] may be added. [stepCall] is the request_tool
     * call. For a tool that only changes the thread folder, the answer also
     * covers its next call (Allow once) or all of them (Allow for task). A
     * tool that leaves the app is only added: each of its calls still shows
     * its own card with its arguments, as the mode asks, so a subagent misled
     * by a web page cannot send a file out under a grant the user gave for a
     * reason (D-062).
     */
    suspend fun grant(tool: Tool, stepCall: ToolCall, reason: String): GateAnswer {
        if (runsWithoutCard(tool)) {
            return GateAnswer.ALLOWED
        }
        val decision = askWithinLimit(ApprovalRequest(tool.name, stepCall, SubagentAsk(agentLabel, reason)))
            ?: return GateAnswer.SKIPPED
        if (tool.sideEffect != SideEffect.CHANGES_THREAD_FOLDER) {
            return if (decision == ApprovalDecision.DENY) GateAnswer.DENIED else GateAnswer.ALLOWED
        }
        return answerFor(tool.name, decision, grantsLaterCall = true)
    }

    private fun runsWithoutCard(tool: Tool): Boolean = broker.runsWithoutAsking(tool) || tool.name in allowedForTask

    private fun useAllowanceForOneCall(toolName: String): Boolean {
        val left = allowedOnce[toolName] ?: 0
        if (left <= 0) {
            return false
        }
        allowedOnce[toolName] = left - 1
        return true
    }

    private fun answerFor(toolName: String, decision: ApprovalDecision, grantsLaterCall: Boolean): GateAnswer {
        when (decision) {
            ApprovalDecision.DENY -> return GateAnswer.DENIED
            ApprovalDecision.ALLOW_FOR_TASK, ApprovalDecision.ALLOW_FOR_THREAD -> allowedForTask += toolName
            ApprovalDecision.ALLOW_ONCE -> if (grantsLaterCall) {
                allowedOnce[toolName] = (allowedOnce[toolName] ?: 0) + 1
            }
        }
        return GateAnswer.ALLOWED
    }

    /**
     * The user's answer, or null when [waitLimit] passed first; the card is
     * withdrawn then. Both waits have ended when this returns, so the card's
     * own status writes never land after the step's final status.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun askWithinLimit(request: ApprovalRequest): ApprovalDecision? = coroutineScope {
        val answer = async { broker.askForSubagent(request) }
        val timeUp = async { timer.wait(waitLimit) }
        val firstDone = select<ApprovalDecision?> {
            answer.onAwait { decision -> decision }
            timeUp.onAwait { null }
        }
        // An answer given in the same moment the time ran out still counts.
        val decision = firstDone ?: if (answer.isCompleted && !answer.isCancelled) answer.getCompleted() else null
        answer.cancelAndJoin()
        timeUp.cancelAndJoin()
        decision
    }

    companion object {
        /** The user's rule (D-015): a subagent waits at most 3 minutes for an answer. */
        val WAIT_LIMIT: Duration = 3.minutes
    }
}
