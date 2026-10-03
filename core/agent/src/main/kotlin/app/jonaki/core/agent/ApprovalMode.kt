package app.jonaki.core.agent

import app.jonaki.core.toolapi.SideEffect

/**
 * How much the user wants to be asked before a tool changes something
 * (user ruling 2026-10-03). Set for all threads in Settings and overridden
 * per thread in the chat's menu.
 */
enum class ApprovalMode {
    /** Every change asks: inside the thread folder and outside the app. */
    ASK,

    /** Changes inside the thread folder run; changes that leave the app still ask. */
    AUTO,

    /** Nothing asks, also not for subagents. */
    BYPASS,
    ;

    companion object {
        /** True when a tool with [sideEffect] must show an approval card in [mode]. */
        fun needsApproval(sideEffect: SideEffect, mode: ApprovalMode): Boolean = when (sideEffect) {
            SideEffect.READ_ONLY, SideEffect.CHANGES_APP_DATA -> false
            SideEffect.CHANGES_THREAD_FOLDER -> mode == ASK
            SideEffect.CHANGES -> mode != BYPASS
        }
    }
}
