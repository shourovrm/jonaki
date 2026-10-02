package app.jonaki.ui

import app.jonaki.core.ui.GroupDownloadUi
import app.jonaki.core.ui.ToolGroupChoice
import app.jonaki.core.ui.ToolGroupRowUi
import app.jonaki.run.PythonCoreStatus
import app.jonaki.run.PythonDownload
import app.jonaki.run.PythonState
import app.jonaki.runtimes.pyodide.PyodideRelease
import app.jonaki.settings.ToolGroup
import app.jonaki.settings.ToolGroups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolGroupRowsTest {
    private val coreBytes = 13_532_188L
    private val addOnNames = setOf("numpy", "pandas", "python-dateutil", "pytz", "six")
    private val notInstalled = PythonState(coreStatus = PythonCoreStatus.NOT_INSTALLED)
    private val installed = PythonState(coreStatus = PythonCoreStatus.INSTALLED, dataAddOnPackages = addOnNames)

    private fun rows(python: PythonState, disabled: Set<ToolGroup> = emptySet()): Map<ToolGroupChoice, ToolGroupRowUi> =
        ToolGroupRows.of(ToolGroups.enabled(disabled), python, coreBytes).associateBy { row -> row.group }

    @Test
    fun everyGroupHasOneRowInOrder() {
        assertEquals(ToolGroupChoice.entries, ToolGroupRows.of(ToolGroups.enabled(emptySet()), notInstalled, coreBytes).map { it.group })
    }

    @Test
    fun filesIsOnAndCannotBeSwitched() {
        val files = rows(notInstalled, disabled = setOf(ToolGroup.FILES)).getValue(ToolGroupChoice.FILES)

        assertTrue(files.isOn)
        assertFalse(files.canSwitch)
    }

    @Test
    fun aSwitchedOffGroupShowsOff() {
        assertFalse(rows(notInstalled, disabled = setOf(ToolGroup.WEB)).getValue(ToolGroupChoice.WEB).isOn)
    }

    @Test
    fun missingPythonShowsItsDownloadSize() {
        val python = rows(notInstalled).getValue(ToolGroupChoice.PYTHON)

        assertTrue(python.isOn)
        assertEquals(GroupDownloadUi.Missing(coreBytes), python.download)
    }

    @Test
    fun theDataAddOnIsOnOnlyWhenInstalled() {
        val missing = rows(installed).getValue(ToolGroupChoice.DATA_ADD_ON)
        val present = rows(installed.copy(installedPackages = addOnNames.toList())).getValue(ToolGroupChoice.DATA_ADD_ON)

        assertFalse(missing.isOn)
        assertEquals(GroupDownloadUi.Missing(PyodideRelease.DATA_ADD_ON_DOWNLOAD_BYTES), missing.download)
        assertTrue(present.isOn)
        assertEquals(GroupDownloadUi.Installed, present.download)
    }

    @Test
    fun theDataAddOnWaitsForPythonsSwitch() {
        assertFalse(rows(installed, disabled = setOf(ToolGroup.PYTHON)).getValue(ToolGroupChoice.DATA_ADD_ON).canSwitch)
        assertTrue(rows(installed).getValue(ToolGroupChoice.DATA_ADD_ON).canSwitch)
    }

    @Test
    fun anAddOnDownloadWithoutPythonShowsOnBothRows() {
        val downloading = notInstalled.copy(download = PythonDownload(PyodideRelease.DATA_ADD_ON, doneBytes = 100, totalBytes = 200))

        val shown = rows(downloading)

        assertEquals(GroupDownloadUi.Downloading(100, 200), shown.getValue(ToolGroupChoice.PYTHON).download)
        assertEquals(GroupDownloadUi.Downloading(100, 200), shown.getValue(ToolGroupChoice.DATA_ADD_ON).download)
        assertTrue(shown.getValue(ToolGroupChoice.DATA_ADD_ON).isOn)
    }

    @Test
    fun aFailureShowsOnTheRowThatStartedIt() {
        val coreFailed = rows(notInstalled.copy(problem = "Could not install Python", problemPackages = emptyList()))
        val addOnFailed = rows(installed.copy(problem = "Could not install numpy", problemPackages = PyodideRelease.DATA_ADD_ON))

        assertEquals(GroupDownloadUi.Failed("Could not install Python", coreBytes), coreFailed.getValue(ToolGroupChoice.PYTHON).download)
        assertEquals(GroupDownloadUi.Installed, addOnFailed.getValue(ToolGroupChoice.PYTHON).download)
        assertEquals(
            GroupDownloadUi.Failed("Could not install numpy", PyodideRelease.DATA_ADD_ON_DOWNLOAD_BYTES),
            addOnFailed.getValue(ToolGroupChoice.DATA_ADD_ON).download,
        )
    }

    @Test
    fun damagedPythonSaysSo() {
        val damaged = rows(PythonState(coreStatus = PythonCoreStatus.DAMAGED, damagedFiles = listOf("pyodide.js")))

        assertEquals(GroupDownloadUi.Damaged, damaged.getValue(ToolGroupChoice.PYTHON).download)
    }
}
