package app.jonaki.core.agent

import app.jonaki.core.toolapi.SideEffect

/**
 * How much the user wants to be asked before a tool changes something
 * (user ruling 2026-10-03). Set for all threads in Settings and overridden
 * per thread in the chat's menu.
 */
enum class ApprovalMode {
    /** Every change asks: inside the thread folder and outside the app, a reminder too. */
    ASK,

    /**
     * Changes inside the thread folder run, and so do small reversible ones
     * (a reminder, a copy in Downloads/Jonaki); the other changes that
     * leave the app still ask.
     */
    AUTO,

    /** Nothing asks, except what always asks (see [ApprovalPolicy]). */
    BYPASS,
    ;

    companion object {
        /** True when a tool with [sideEffect] must show an approval card in [mode]. */
        fun needsApproval(sideEffect: SideEffect, mode: ApprovalMode): Boolean = when (sideEffect) {
            SideEffect.READ_ONLY, SideEffect.CHANGES_APP_DATA -> false
            SideEffect.CHANGES_THREAD_FOLDER, SideEffect.CHANGES_REVERSIBLE -> mode == ASK
            SideEffect.CHANGES -> mode != BYPASS
            SideEffect.NEEDS_USER -> true
        }
    }
}
