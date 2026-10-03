package app.jonaki.ui

import app.jonaki.feature.chat.PythonInstallState
import app.jonaki.run.PythonCoreStatus
import app.jonaki.run.PythonDownload
import app.jonaki.run.PythonState
import app.jonaki.runtimes.pyodide.PyodideRelease
import app.jonaki.tools.runcode.InstallNeed
import org.junit.Assert.assertEquals
import org.junit.Test

class PythonCardsTest {
    private val pythonNeed = InstallNeed(emptyList())
    private val pandasNeed = InstallNeed(listOf("pandas"))
    private val notInstalled = PythonState(coreStatus = PythonCoreStatus.NOT_INSTALLED)
    private val installed = PythonState(coreStatus = PythonCoreStatus.INSTALLED)

    private fun stateOf(need: InstallNeed, python: PythonState, target: List<String> = emptyList()) =
        PythonCards.of("c1", need, python, downloadBytes = 13_532_188, installTarget = target).state

    @Test
    fun aMissingPythonIsOffered() {
        val card = PythonCards.of("c1", pythonNeed, notInstalled, downloadBytes = 13_532_188, installTarget = emptyList())

        assertEquals("python-c1", card.id)
        assertEquals(13_532_188L, card.downloadBytes)
        assertEquals(PythonInstallState.Offered, card.state)
    }

    @Test
    fun aRunningDownloadShowsItsProgress() {
        val downloading = notInstalled.copy(download = PythonDownload(emptyList(), doneBytes = 10, totalBytes = 20))

        assertEquals(PythonInstallState.Downloading(10, 20), stateOf(pythonNeed, downloading))
    }

    @Test
    fun afterTheInstallTheCardOffersTryAgain() {
        assertEquals(PythonInstallState.Installed, stateOf(pythonNeed, installed))
        assertEquals(
            PythonInstallState.Installed,
            stateOf(pandasNeed, installed.copy(installedPackages = listOf("numpy", "pandas"))),
        )
    }

    @Test
    fun packagesAreNotMetByPythonAlone() {
        assertEquals(PythonInstallState.Offered, stateOf(pandasNeed, installed, PyodideRelease.DATA_ADD_ON))
    }

    @Test
    fun aFailureOfThisInstallIsShown() {
        val failed = installed.copy(problem = "Could not install numpy", problemPackages = PyodideRelease.DATA_ADD_ON)

        assertEquals(PythonInstallState.Failed("Could not install numpy"), stateOf(pandasNeed, failed, PyodideRelease.DATA_ADD_ON))
        assertEquals(PythonInstallState.Offered, stateOf(InstallNeed(listOf("regex")), failed, listOf("regex")))
    }

    @Test
    fun theDataAddOnIsInstalledWholeWhenTheNeedLiesWithinIt() {
        assertEquals(PyodideRelease.DATA_ADD_ON, PythonCards.installTarget(pandasNeed, isWithinDataAddOn = true))
        assertEquals(listOf("regex"), PythonCards.installTarget(InstallNeed(listOf("regex")), isWithinDataAddOn = false))
        assertEquals(emptyList<String>(), PythonCards.installTarget(pythonNeed, isWithinDataAddOn = false))
    }
}
