package app.jonaki.core.agent

import app.jonaki.core.toolapi.SideEffect

/** What the thread's agent may do with one tool call. */
sealed interface ApprovalVerdict {
    /** The call runs without a card. */
    data object Runs : ApprovalVerdict

    /**
     * The call shows a card.
     *
     * @param offersThreadAllowance true when the card may offer "Allow all in this
     *   thread"; false for the calls that always ask and offer only Allow once and Deny.
     * @param afterOutsideContent true when the card is there because the thread has read outside
     *   content and the call sends data out; the card says so.
     */
    data class Asks(val offersThreadAllowance: Boolean, val afterOutsideContent: Boolean) : ApprovalVerdict
}

/** What the policy needs to know about one call, worked out by the broker from the tool and its arguments. */
data class CallFacts(
    val sideEffect: SideEffect,
    /** Deletes or overwrites outside the thread folder, or sends a file or data to another app or server. */
    val isVeryRisky: Boolean,
    val sendsOut: Boolean,
    /** A Settings rule names exactly this action. */
    val matchesSettingsRule: Boolean,
)

/**
 * The decision whether a call of the thread's agent shows a card, as a pure
 * function of the call, the mode and the thread's state, so that every
 * combination can be tested. The order is the point; each step names what
 * it protects:
 *
 * 1. A call that needs the user (a delegate call over the subagent cap)
 *    always asks, with Allow once and Deny only.
 * 2. A call that sends data out after the thread read outside content always
 *    asks, with Allow once and Deny only: no mode, no allowance and no
 *    Settings rule is stronger than a prompt injection.
 * 3. A Settings rule for this action lets it run.
 * 4. The mode lets it run when it asks for nothing of this kind.
 * 5. "Allow all in this thread" lets it run unless it is very risky.
 * 6. Otherwise a card; a very risky call's card offers Allow once and Deny only.
 */
object ApprovalPolicy {
    fun decide(
        facts: CallFacts,
        mode: ApprovalMode,
        allowAllInThread: Boolean,
        threadHasReadOutsideContent: Boolean,
    ): ApprovalVerdict {
        if (facts.sideEffect == SideEffect.NEEDS_USER) {
            return ApprovalVerdict.Asks(offersThreadAllowance = false, afterOutsideContent = false)
        }
        if (OutsideContent.sendOutNeedsCard(facts.sendsOut, threadHasReadOutsideContent)) {
            return ApprovalVerdict.Asks(offersThreadAllowance = false, afterOutsideContent = true)
        }
        if (facts.matchesSettingsRule) {
            return ApprovalVerdict.Runs
        }
        if (!ApprovalMode.needsApproval(facts.sideEffect, mode)) {
            return ApprovalVerdict.Runs
        }
        if (allowAllInThread && !facts.isVeryRisky) {
            return ApprovalVerdict.Runs
        }
        return ApprovalVerdict.Asks(offersThreadAllowance = !facts.isVeryRisky, afterOutsideContent = false)
    }
}
