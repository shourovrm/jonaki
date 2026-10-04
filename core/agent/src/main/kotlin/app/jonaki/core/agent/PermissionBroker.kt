package app.jonaki.core.agent

import app.jonaki.core.guardapi.ActionVerdict
import app.jonaki.core.guardapi.Guard
import app.jonaki.core.guardapi.NoGuard
import app.jonaki.core.model.ToolCall
import app.jonaki.core.toolapi.Tool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** What the user answered on an approval card. */
enum class ApprovalDecision {
    ALLOW_ONCE,

    /** Every tool runs without a card in this thread from now on, except the calls that always ask. */
    ALLOW_ALL_IN_THREAD,
    DENY,
}

data class ApprovalRequest(
    val toolName: String,
    val toolCall: ToolCall,
    /** False for a call that always asks: the card then offers only Allow once and Deny. */
    val offersThreadAllowance: Boolean = true,
    /** True when the card is there because the thread read outside content and this call sends data out. */
    val afterOutsideContent: Boolean = false,
)

/**
 * Shows an approval card and suspends until the user answers. The chat
 * screen implements this; tests use a fixed answer.
 */
fun interface ApprovalRequester {
    suspend fun requestApproval(request: ApprovalRequest): ApprovalDecision
}

/**
 * Decides whether a call of the thread's agent may run, by [ApprovalPolicy]:
 * the call's own cost (a tool with mixed actions answers per call), the
 * thread's approval mode, its "Allow all in this thread" answer, the rules
 * from Settings and the fixed rule about outside content. Subagents never
 * ask through the broker; see [SubagentPermissions]. One broker serves one
 * thread.
 *
 * @param approvalMode read before every call, so a mode changed during a run
 *   applies from the next tool call.
 * @param settingsRules read before every call; only Settings creates them.
 */
class PermissionBroker(
    private val approvalRequester: ApprovalRequester,
    val threadState: ThreadApprovalState = ThreadApprovalState(),
    private val approvalMode: () -> ApprovalMode = { ApprovalMode.ASK },
    private val settingsRules: () -> List<ApprovalRule> = { emptyList() },
    /** A second opinion on calls that would show a card; [NoGuard] always shows the card. */
    private val guard: Guard = NoGuard,
    /** The user's latest message, which the guard compares the call with. */
    private val userRequest: () -> String = { "" },
) {
    suspend fun mayRun(tool: Tool, toolCall: ToolCall): Boolean {
        val arguments = argumentsOf(toolCall)
        val verdict = ApprovalPolicy.decide(
            facts = factsOf(tool, arguments),
            mode = approvalMode(),
            allowAllInThread = threadState.allowAllInThread,
            threadHasReadOutsideContent = threadState.readOutsideContent,
        )
        if (verdict !is ApprovalVerdict.Asks) {
            return true
        }
        // Only a call that may be allowed for the whole thread can be let through here: the calls that
        // always ask (very risky, after outside content, over the subagent cap) never are.
        if (verdict.offersThreadAllowance && guardLetsCallRunInsteadOfAsking(tool, arguments)) {
            return true
        }
        val request = ApprovalRequest(tool.name, toolCall, verdict.offersThreadAllowance, verdict.afterOutsideContent)
        return when (approvalRequester.requestApproval(request)) {
            ApprovalDecision.ALLOW_ONCE -> true
            ApprovalDecision.ALLOW_ALL_IN_THREAD -> {
                // A card that did not offer it cannot grant it; should the answer arrive, it counts as once.
                if (verdict.offersThreadAllowance) {
                    threadState.grantAllowAll()
                }
                true
            }
            ApprovalDecision.DENY -> false
        }
    }

    /** The loop calls this when a result was outside content, so that later send-outs ask. */
    suspend fun outsideContentWasRead() {
        threadState.markOutsideContentRead()
    }

    /**
     * The one place where a card is about to be shown for a call that is
     * not very risky and not caught by the outside-content rule. The guard
     * can let the call run instead; it can never deny one, and on any
     * failure it answers "show the card".
     */
    private suspend fun guardLetsCallRunInsteadOfAsking(tool: Tool, arguments: JsonObject): Boolean =
        guard.judgeAction(userRequest(), tool.name, arguments) is ActionVerdict.MayRunWithoutCard

    private fun factsOf(tool: Tool, arguments: JsonObject): CallFacts = CallFacts(
        sideEffect = tool.sideEffectOf(arguments),
        isVeryRisky = tool.isVeryRiskyOf(arguments),
        sendsOut = tool.sendsOutOf(arguments),
        matchesSettingsRule = settingsRules().any { rule -> rule.matches(tool, arguments) },
    )

    /** The loop checked that the arguments are a JSON object before asking; an empty object is a safe fallback. */
    private fun argumentsOf(toolCall: ToolCall): JsonObject =
        runCatching { Json.parseToJsonElement(toolCall.argumentsJson) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())
}
