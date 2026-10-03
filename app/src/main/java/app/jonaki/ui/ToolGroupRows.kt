package app.jonaki.ui

import app.jonaki.core.ui.GroupDownloadUi
import app.jonaki.core.ui.ToolGroupChoice
import app.jonaki.core.ui.ToolGroupRowUi
import app.jonaki.run.PythonCoreStatus
import app.jonaki.run.PythonState
import app.jonaki.runtimes.pyodide.PyodideRelease
import app.jonaki.settings.ToolGroup

/**
 * The rows of the tool picker and Settings > Tools, from the saved switches
 * and Python's installed state. Python's switch says whether the model is
 * offered Python; the data add-on's switch says whether numpy and pandas
 * are installed, as the add-on has no tool of its own.
 */
object ToolGroupRows {
    fun of(enabled: Set<ToolGroup>, python: PythonState, coreDownloadBytes: Long): List<ToolGroupRowUi> =
        ToolGroupChoice.entries.map { choice ->
            when (choice) {
                ToolGroupChoice.PYTHON -> ToolGroupRowUi(
                    group = choice,
                    isOn = ToolGroup.PYTHON in enabled,
                    download = pythonDownload(python, coreDownloadBytes),
                )
                ToolGroupChoice.DATA_ADD_ON -> dataAddOnRow(enabled, python)
                else -> {
                    val group = groupOf(choice)
                    ToolGroupRowUi(group = choice, isOn = group in enabled, canSwitch = group.canSwitchOff)
                }
            }
        }

    /** The data add-on has no group: it is a download, not tools, so it is never passed here. */
    fun groupOf(choice: ToolGroupChoice): ToolGroup = when (choice) {
        ToolGroupChoice.FILES -> ToolGroup.FILES
        ToolGroupChoice.WEB -> ToolGroup.WEB
        ToolGroupChoice.YOUTUBE -> ToolGroup.YOUTUBE
        ToolGroupChoice.MEMORY -> ToolGroup.MEMORY
        ToolGroupChoice.SUBAGENTS -> ToolGroup.SUBAGENTS
        ToolGroupChoice.REPORTS -> ToolGroup.REPORTS
        ToolGroupChoice.SHARE -> ToolGroup.SHARE
        ToolGroupChoice.PHONE -> ToolGroup.PHONE
        ToolGroupChoice.SCHEDULE -> ToolGroup.SCHEDULE
        ToolGroupChoice.MCP -> ToolGroup.MCP
        ToolGroupChoice.JAVASCRIPT -> ToolGroup.JAVASCRIPT
        ToolGroupChoice.PYTHON -> ToolGroup.PYTHON
        ToolGroupChoice.DATA_ADD_ON -> error("the data add-on is not a tool group")
    }

    /** Any running download shows on the Python row while Python itself is missing, since it fetches Python first. */
    private fun pythonDownload(python: PythonState, coreDownloadBytes: Long): GroupDownloadUi {
        val download = python.download
        if (download != null && (download.packageNames.isEmpty() || python.coreStatus != PythonCoreStatus.INSTALLED)) {
            return GroupDownloadUi.Downloading(download.doneBytes, download.totalBytes)
        }
        val problem = python.problem
        return when {
            python.coreStatus == PythonCoreStatus.INSTALLED -> GroupDownloadUi.Installed
            python.coreStatus == PythonCoreStatus.DAMAGED -> GroupDownloadUi.Damaged
            problem != null && python.problemPackages.isEmpty() -> GroupDownloadUi.Failed(problem, coreDownloadBytes)
            else -> GroupDownloadUi.Missing(coreDownloadBytes)
        }
    }

    private fun dataAddOnRow(enabled: Set<ToolGroup>, python: PythonState): ToolGroupRowUi {
        val addOnDownload = python.download?.takeIf { download -> download.packageNames == PyodideRelease.DATA_ADD_ON }
        val problem = python.problem
        val state = when {
            addOnDownload != null -> GroupDownloadUi.Downloading(addOnDownload.doneBytes, addOnDownload.totalBytes)
            python.isDataAddOnInstalled -> GroupDownloadUi.Installed
            problem != null && python.problemPackages == PyodideRelease.DATA_ADD_ON ->
                GroupDownloadUi.Failed(problem, PyodideRelease.DATA_ADD_ON_DOWNLOAD_BYTES)
            else -> GroupDownloadUi.Missing(PyodideRelease.DATA_ADD_ON_DOWNLOAD_BYTES)
        }
        return ToolGroupRowUi(
            group = ToolGroupChoice.DATA_ADD_ON,
            isOn = addOnDownload != null || python.isDataAddOnInstalled,
            // The add-on only serves Python, so it waits for Python's switch.
            canSwitch = ToolGroup.PYTHON in enabled,
            download = state,
        )
    }
}
