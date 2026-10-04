package app.jonaki.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.jonaki.JonakiApplication
import app.jonaki.core.ui.ToolGroupChoice
import app.jonaki.core.ui.ToolGroupRowUi
import app.jonaki.feature.onboarding.ToolPickerScreen
import app.jonaki.feature.settings.PythonActions
import app.jonaki.feature.settings.PythonDownloadUi
import app.jonaki.feature.settings.PythonScreen
import app.jonaki.feature.settings.PythonStatusUi
import app.jonaki.feature.settings.PythonUiState
import app.jonaki.feature.settings.ToolsScreen
import app.jonaki.run.PythonCoreStatus
import app.jonaki.run.PythonSetup
import app.jonaki.run.PythonState
import app.jonaki.runtimes.pyodide.PyodideRelease
import app.jonaki.settings.AppSettings
import app.jonaki.settings.ToolGroup
import app.jonaki.settings.ToolPicker

/** The first-run tool picker; Continue marks it seen so it does not show again (plan M8 step 3). */
@Composable
fun ToolPickerRoute(application: JonakiApplication) {
    val rows = toolGroupRows(application)
    ToolPickerScreen(
        rows = rows,
        onSwitch = { choice, on -> switchToolGroup(application.settings, application.python, choice, on) },
        onDownload = { choice -> startDownload(application.python, choice) },
        onCancelDownload = application.python::cancel,
        onContinue = {
            application.settings.update { current -> current.copy(toolPickerSeenVersion = ToolPicker.CURRENT) }
        },
    )
}

@Composable
fun ToolsRoute(application: JonakiApplication, onBack: () -> Unit) {
    ToolsScreen(
        rows = toolGroupRows(application),
        onSwitch = { choice, on -> switchToolGroup(application.settings, application.python, choice, on) },
        onDownload = { choice -> startDownload(application.python, choice) },
        onCancelDownload = application.python::cancel,
        onBack = onBack,
    )
}

@Composable
fun PythonRoute(application: JonakiApplication, onBack: () -> Unit) {
    val python = application.python
    val state by python.state.collectAsState()
    LaunchedEffect(Unit) {
        // Files can change outside the app's view, for example a run that found them damaged.
        python.refresh()
    }
    PythonScreen(
        state = pythonUiState(state, python.release),
        actions = PythonActions(
            onBack = onBack,
            onInstall = { python.installCore() },
            onCancel = python::cancel,
            onRemove = { python.remove() },
            onInstallDataAddOn = { python.installDataAddOn() },
            onRemoveDataAddOn = { python.removeDataAddOn() },
            onInstallDocumentsAddOn = { python.installDocumentsAddOn() },
            onRemoveDocumentsAddOn = { python.removeDocumentsAddOn() },
            onInstallPackage = { name -> python.installPackages(listOf(name)) },
        ),
    )
}

@Composable
private fun toolGroupRows(application: JonakiApplication): List<ToolGroupRowUi> {
    val snapshot by application.settings.snapshot.collectAsState()
    val pythonState by application.python.state.collectAsState()
    LaunchedEffect(Unit) {
        application.python.refresh()
    }
    return ToolGroupRows.of(snapshot.enabledToolGroups, pythonState, application.python.release.coreDownloadBytes)
}

/**
 * Switching Python on starts its download when it is missing: the row
 * names the size beside the switch, so the download is never silent.
 * The data add-on's switch installs or removes numpy and pandas.
 */
private fun switchToolGroup(settings: AppSettings, python: PythonSetup, choice: ToolGroupChoice, on: Boolean) {
    if (choice == ToolGroupChoice.DATA_ADD_ON) {
        switchDataAddOn(python, on)
        return
    }
    val group = ToolGroupRows.groupOf(choice)
    settings.update { current ->
        val disabled = if (on) current.disabledToolGroups - group else current.disabledToolGroups + group
        current.copy(disabledToolGroups = disabled)
    }
    val pythonMissing = python.state.value.coreStatus != PythonCoreStatus.INSTALLED
    if (group == ToolGroup.PYTHON && on && pythonMissing) {
        python.installCore()
    }
}

private fun switchDataAddOn(python: PythonSetup, on: Boolean) {
    if (on) {
        python.installDataAddOn()
        return
    }
    val download = python.state.value.download
    if (download != null && download.packageNames == PyodideRelease.DATA_ADD_ON) {
        python.cancel()
        return
    }
    python.removeDataAddOn()
}

private fun startDownload(python: PythonSetup, choice: ToolGroupChoice) {
    when (choice) {
        ToolGroupChoice.PYTHON -> python.installCore()
        ToolGroupChoice.DATA_ADD_ON -> python.installDataAddOn()
        else -> Unit
    }
}

private fun pythonUiState(state: PythonState, release: PyodideRelease): PythonUiState = PythonUiState(
    status = when (state.coreStatus) {
        PythonCoreStatus.CHECKING -> PythonStatusUi.CHECKING
        PythonCoreStatus.NOT_INSTALLED -> PythonStatusUi.NOT_INSTALLED
        PythonCoreStatus.INSTALLED -> PythonStatusUi.INSTALLED
        PythonCoreStatus.DAMAGED -> PythonStatusUi.DAMAGED
    },
    version = release.version,
    coreDownloadBytes = release.coreDownloadBytes,
    storageBytes = state.storageBytes,
    installedPackages = state.installedPackages + state.installedWheels.map { wheel -> "${wheel.packageName} ${wheel.version}" },
    damagedFiles = state.damagedFiles,
    dataAddOnInstalled = state.isDataAddOnInstalled,
    dataAddOnDownloadBytes = PyodideRelease.DATA_ADD_ON_DOWNLOAD_BYTES,
    documentsAddOnInstalled = state.isDocumentsAddOnInstalled,
    documentsAddOnDamaged = state.damagedWheelFiles.isNotEmpty(),
    documentsAddOnDownloadBytes = PyodideRelease.DOCUMENTS_ADD_ON_DOWNLOAD_BYTES,
    download = state.download?.let { download ->
        PythonDownloadUi(
            packageNames = download.packageNames,
            doneBytes = download.doneBytes,
            totalBytes = download.totalBytes,
            isDocumentsAddOn = download.packageNames == PyodideRelease.DOCUMENTS_ADD_ON,
        )
    },
    problem = state.problem,
)
