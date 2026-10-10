package app.jonaki.core.agent

import app.jonaki.core.guardapi.ActionVerdict
import app.jonaki.core.guardapi.Guard
import app.jonaki.core.guardapi.NoGuard
import app.jonaki.core.model.ToolCall
import app.jonaki.core.toolapi.Tool
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /**
     * Settings > Guardrails: whether a thread that read outside content asks
     * before every call that sends data out (D-143, rule 1). Read before
     * every call, so the switch applies at once.
     */
    private val asksAfterOutsideContent: () -> Boolean = { true },
    /**
     * Which addresses already appeared in the thread. After outside content a
     * call to an address that appeared nowhere asks, because the model may
     * have put data into it. Consulted only then, so research before any
     * outside content costs nothing.
     */
    private val knownAddresses: KnownAddresses = KnownAddresses.NONE,
) {
    // Calls of one turn that read only may run side by side; their cards still come one at a time.
    private val cardLock = Mutex()

    /**
     * Hosts the user allowed a composed address on, for as long as this
     * broker lives (one run). The first allowed request could already carry
     * anything to that host, so asking again for the same host protects
     * nothing and only floods a research task with cards.
     */
    private val approvedHosts: MutableSet<String> = ConcurrentHashMap.newKeySet()

    suspend fun mayRun(tool: Tool, toolCall: ToolCall): Boolean {
        val arguments = argumentsOf(toolCall)
        val threadHasReadOutsideContent = asksAfterOutsideContent() && threadState.readOutsideContent
        val verdict = ApprovalPolicy.decide(
            facts = factsOf(tool, arguments, threadHasReadOutsideContent),
            mode = approvalMode(),
            allowAllInThread = threadState.allowAllInThread,
            threadHasReadOutsideContent = threadHasReadOutsideContent,
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
        val decision = cardLock.withLock { approvalRequester.requestApproval(request) }
        if (decision != ApprovalDecision.DENY) {
            tool.contactedAddressOf(arguments)?.let(WebAddresses::hostOf)?.let { host -> approvedHosts += host }
        }
        return when (decision) {
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

    private suspend fun factsOf(tool: Tool, arguments: JsonObject, threadHasReadOutsideContent: Boolean): CallFacts = CallFacts(
        sideEffect = tool.sideEffectOf(arguments),
        isVeryRisky = tool.isVeryRiskyOf(arguments),
        sendsOut = tool.sendsOutOf(arguments) || contactsUnknownAddress(tool, arguments, threadHasReadOutsideContent),
        matchesSettingsRule = settingsRules().any { rule -> rule.matches(tool, arguments) },
    )

    /**
     * An address the model composed can carry data in its path and query. The
     * lookup runs only for a thread that read outside content, since before
     * that the rule does not apply.
     */
    private suspend fun contactsUnknownAddress(tool: Tool, arguments: JsonObject, threadHasReadOutsideContent: Boolean): Boolean {
        if (!threadHasReadOutsideContent) {
            return false
        }
        val address = tool.contactedAddressOf(arguments) ?: return false
        if (WebAddresses.hostOf(address) in approvedHosts) {
            return false
        }
        return !knownAddresses.isKnown(address)
    }

    /**
     * True when this call may show a card after the thread read outside
     * content although the tool only reads, so that the scheduler runs it
     * alone and cards come one at a time. Needs no lookup: it is a safe
     * over-estimate that ignores whether the address is known.
     */
    fun mayAskAboutAddress(tool: Tool, toolCall: ToolCall): Boolean {
        if (!asksAfterOutsideContent() || !threadState.readOutsideContent) {
            return false
        }
        return tool.contactedAddressOf(argumentsOf(toolCall)) != null
    }

    /** The loop checked that the arguments are a JSON object before asking; an empty object is a safe fallback. */
    private fun argumentsOf(toolCall: ToolCall): JsonObject =
        runCatching { Json.parseToJsonElement(toolCall.argumentsJson) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())
}
