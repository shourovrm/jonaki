package app.jonaki.settings

import app.jonaki.core.agent.ApprovalMode

/** Which approval mode a thread runs with (D-058). */
object ApprovalModes {
    /** The default for new installs and for every thread that has no mode of its own. */
    fun fromName(name: String?): ApprovalMode =
        ApprovalMode.entries.firstOrNull { mode -> mode.name == name } ?: ApprovalMode.ASK

    /** The thread's own mode when it has one, else the default from Settings. */
    fun effective(threadMode: String?, defaultMode: ApprovalMode): ApprovalMode =
        ApprovalMode.entries.firstOrNull { mode -> mode.name == threadMode } ?: defaultMode
}
