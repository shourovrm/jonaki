package app.jonaki.ui

import app.jonaki.feature.chat.ChatItem
import app.jonaki.feature.chat.PythonInstallState
import app.jonaki.run.PythonCoreStatus
import app.jonaki.run.PythonState
import app.jonaki.runtimes.pyodide.PyodideRelease
import app.jonaki.tools.runcode.InstallNeed

/**
 * The chat's install card for a run_code result that found Python or its
 * packages missing (plan M8 step 4), from the saved result and Python's
 * state now. The card follows the files: once they are installed it offers
 * Try again instead of Install.
 */
object PythonCards {
    fun idFor(toolCallId: String): String = "python-$toolCallId"

    /** What Install downloads: the whole data add-on when the need lies within it, else exactly the named packages. */
    fun installTarget(need: InstallNeed, isWithinDataAddOn: Boolean): List<String> = when {
        need.packageNames.isEmpty() -> emptyList()
        isWithinDataAddOn -> PyodideRelease.DATA_ADD_ON
        else -> need.packageNames
    }

    fun of(
        toolCallId: String,
        need: InstallNeed,
        python: PythonState,
        /** The download's size, or null when unknown (packages outside the data add-on). */
        downloadBytes: Long?,
        installTarget: List<String>,
    ): ChatItem.PythonInstall = ChatItem.PythonInstall(
        id = idFor(toolCallId),
        packageNames = need.packageNames,
        downloadBytes = downloadBytes,
        state = stateOf(need, python, installTarget),
    )

    private fun stateOf(need: InstallNeed, python: PythonState, installTarget: List<String>): PythonInstallState {
        val download = python.download
        val problem = python.problem
        return when {
            isMet(need, python) -> PythonInstallState.Installed
            download != null -> PythonInstallState.Downloading(download.doneBytes, download.totalBytes)
            problem != null && python.problemPackages == installTarget -> PythonInstallState.Failed(problem)
            else -> PythonInstallState.Offered
        }
    }

    private fun isMet(need: InstallNeed, python: PythonState): Boolean {
        if (need.packageNames.isEmpty()) {
            return python.coreStatus == PythonCoreStatus.INSTALLED
        }
        return python.hasPackages(need.packageNames)
    }
}
