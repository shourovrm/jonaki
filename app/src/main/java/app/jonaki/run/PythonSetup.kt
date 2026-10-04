package app.jonaki.run

import app.jonaki.runtimes.pyodide.DownloadProgress
import app.jonaki.runtimes.pyodide.InstallResult
import app.jonaki.runtimes.pyodide.PyodideFolder
import app.jonaki.runtimes.pyodide.PyodideInstaller
import app.jonaki.runtimes.pyodide.PinnedWheel
import app.jonaki.runtimes.pyodide.PyodideRelease
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Whether Python's core files are on the phone and intact. */
enum class PythonCoreStatus {
    /** Not read from the files yet. */
    CHECKING,
    NOT_INSTALLED,
    INSTALLED,

    /** Every file is present, but at least one has the wrong SHA-256. */
    DAMAGED,
}

/** One download in progress: the core, packages, or both for the data add-on. */
data class PythonDownload(
    /** Empty while only the core is downloaded. */
    val packageNames: List<String>,
    val doneBytes: Long,
    /** Null when the size is not known before the download (packages outside the data add-on). */
    val totalBytes: Long?,
)

data class PythonState(
    val coreStatus: PythonCoreStatus = PythonCoreStatus.CHECKING,
    val damagedFiles: List<String> = emptyList(),
    val installedPackages: List<String> = emptyList(),
    /** The documents add-on's pinned wheels that are on the phone at their pinned size. */
    val installedWheels: List<PinnedWheel> = emptyList(),
    /** File names of pinned wheels that are present with the wrong bytes; Repair fetches them again. */
    val damagedWheelFiles: List<String> = emptyList(),
    /** numpy and pandas with their dependencies; empty until the core's lock file is there. */
    val dataAddOnPackages: Set<String> = emptySet(),
    val storageBytes: Long = 0,
    /** Null while nothing downloads. */
    val download: PythonDownload? = null,
    /** What went wrong in the last install, as the installer said it; cleared by the next one. */
    val problem: String? = null,
    /** The packages of the install that failed; empty when it was Python itself. */
    val problemPackages: List<String> = emptyList(),
) {
    val isDataAddOnInstalled: Boolean
        get() = dataAddOnPackages.isNotEmpty() && installedPackages.containsAll(dataAddOnPackages)

    /** Every lock package and every pinned wheel of the documents add-on is installed, and no wheel is damaged. */
    val isDocumentsAddOnInstalled: Boolean
        get() = damagedWheelFiles.isEmpty() && hasPackages(PyodideRelease.DOCUMENTS_ADD_ON)

    /**
     * True when every named package is installed; the names are lock names or
     * wheel names, as run_code reports them.
     */
    fun hasPackages(names: List<String>): Boolean {
        val installedNames = installedPackages + installedWheels.map { wheel -> wheel.packageName }
        return coreStatus == PythonCoreStatus.INSTALLED && installedNames.containsAll(names)
    }
}

/**
 * Installs and removes Python for the settings screen, the tool picker and
 * the chat's install card, one download at a time. Downloads run in the
 * app's scope, so leaving a screen does not stop them; Cancel does. The
 * state is read back from the files after every change (D-069: no record
 * besides the files).
 */
class PythonSetup(
    private val folder: PyodideFolder,
    private val installer: PyodideInstaller,
    private val scope: CoroutineScope,
    private val fileDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutableState = MutableStateFlow(PythonState())
    val state: StateFlow<PythonState> = mutableState.asStateFlow()

    // Set from the screens and cleared from the download's own coroutine.
    @Volatile
    private var downloadJob: Job? = null

    val release: PyodideRelease get() = folder.release

    /** Reads the installed state from the files; hashing the core takes about 13 ms (D-013). */
    suspend fun refresh() {
        val read = withContext(fileDispatcher) { readFromFiles() }
        mutableState.update { current ->
            read.copy(download = current.download, problem = current.problem, problemPackages = current.problemPackages)
        }
    }

    /** Null when a download is already running, so a second tap starts nothing. */
    fun installCore(): Job? = startDownload(packageNames = emptyList(), totalBytes = release.coreDownloadBytes)

    /**
     * Installs numpy and pandas, and the core first when it is missing; the
     * add-on switch and the install card both use it.
     */
    fun installDataAddOn(): Job? = installPackages(PyodideRelease.DATA_ADD_ON)

    /**
     * Installs lxml, pillow, beautifulsoup4 and their dependencies from the
     * lock file and the five pinned wheels; the core comes first when missing.
     * Also Repair: a missing or damaged file is fetched again, the rest is kept.
     */
    fun installDocumentsAddOn(): Job? = installPackages(PyodideRelease.DOCUMENTS_ADD_ON)

    /**
     * Installs packages from the lock file with their dependencies; the core
     * comes first when missing. The documents add-on's wheel names are
     * installed from their pinned URLs. A name outside the lock file fails with the
     * installer's message ("requests is not available for Pyodide 314.0.7").
     */
    fun installPackages(names: List<String>): Job? {
        val cleanNames = names.map { name -> name.trim() }.filter { name -> name.isNotEmpty() }
        if (cleanNames.isEmpty()) {
            return null
        }
        return startDownload(cleanNames, totalBytesFor(cleanNames))
    }

    /** Stops the running download; files already checked stay, the half-downloaded one is deleted. */
    fun cancel() {
        val job = downloadJob ?: return
        job.cancel()
        downloadJob = null
        mutableState.update { current -> current.copy(download = null, problem = null) }
        scope.launch { refresh() }
    }

    /** Removes Python with every package; a running download is cancelled first. */
    fun remove(): Job = scope.launch {
        downloadJob?.cancel()
        downloadJob?.join()
        downloadJob = null
        withContext(fileDispatcher) { folder.remove() }
        mutableState.update { current -> current.copy(download = null, problem = null) }
        refresh()
    }

    fun removeDataAddOn(): Job = scope.launch {
        withContext(fileDispatcher) { folder.removePackages(PyodideRelease.DATA_ADD_ON) }
        refresh()
    }

    fun removeDocumentsAddOn(): Job = scope.launch {
        withContext(fileDispatcher) {
            folder.removePackages(PyodideRelease.DOCUMENTS_ADD_ON_LOCK_PACKAGES)
            folder.removeWheels(release.addOnWheels)
        }
        refresh()
    }

    /** The add-on's measured size, with the core's when that is missing too; null for other packages. */
    fun totalBytesFor(packageNames: List<String>): Long? {
        val current = state.value
        val coreBytes = if (current.coreStatus == PythonCoreStatus.INSTALLED) 0 else release.coreDownloadBytes
        val addOnBytes = when {
            isWithinDataAddOn(packageNames) -> PyodideRelease.DATA_ADD_ON_DOWNLOAD_BYTES
            isWithinDocumentsAddOn(packageNames) -> PyodideRelease.DOCUMENTS_ADD_ON_DOWNLOAD_BYTES
            else -> return null
        }
        return coreBytes + addOnBytes
    }

    /** True for the documents add-on's lock packages and wheels; names compare without regard to case. */
    fun isWithinDocumentsAddOn(packageNames: List<String>): Boolean {
        val addOnNames = PyodideRelease.DOCUMENTS_ADD_ON.map { name -> name.lowercase() }.toSet()
        return packageNames.isNotEmpty() && packageNames.all { name -> name.lowercase() in addOnNames }
    }

    /**
     * True for packages that the data add-on brings. Before the core's lock
     * file is there only numpy and pandas themselves are known.
     */
    fun isWithinDataAddOn(packageNames: List<String>): Boolean {
        val addOn = state.value.dataAddOnPackages.ifEmpty { PyodideRelease.DATA_ADD_ON.toSet() }
        return packageNames.isNotEmpty() && addOn.containsAll(packageNames)
    }

    private fun startDownload(packageNames: List<String>, totalBytes: Long?): Job? {
        if (downloadJob?.isActive == true) {
            return null
        }
        mutableState.update { current ->
            current.copy(download = PythonDownload(packageNames, doneBytes = 0, totalBytes = totalBytes), problem = null)
        }
        // Started only after downloadJob is set: a download that fails at once would otherwise
        // compare itself with a still empty downloadJob and never show its problem.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val ownJob = coroutineContext[Job]
            val result = download(packageNames)
            // A cancelled call can end as an IOException, which is the user's Cancel, not a problem.
            val problem = if (isActive) (result as? InstallResult.Failed)?.message else null
            // Saved even when Cancel arrives at the last moment, so the state matches the files.
            withContext(NonCancellable) {
                // After Cancel a newer download may already show; this one must not clear it.
                if (downloadJob === ownJob) {
                    mutableState.update { current ->
                        current.copy(download = null, problem = problem, problemPackages = packageNames)
                    }
                }
                refresh()
            }
        }
        downloadJob = job
        job.start()
        return job
    }

    private suspend fun download(packageNames: List<String>): InstallResult {
        var coreBytes = 0L
        val coreMissing = withContext(fileDispatcher) { !folder.isCoreInstalled() || folder.damagedCoreFiles().isNotEmpty() }
        if (coreMissing) {
            val coreResult = installer.installCore { progress ->
                coreBytes = progress.doneBytes
                showProgress(progress.doneBytes)
            }
            if (coreResult is InstallResult.Failed) {
                return coreResult
            }
        }
        if (packageNames.isEmpty()) {
            return InstallResult.Installed
        }
        val wheels = release.addOnWheels.filter { wheel -> wheel.packageName in packageNames }
        val lockNames = packageNames.filter { name -> release.addOnWheels.none { wheel -> wheel.packageName == name } }
        var lockBytes = 0L
        if (lockNames.isNotEmpty()) {
            val lockResult = installer.installPackages(lockNames) { progress: DownloadProgress ->
                lockBytes = progress.doneBytes
                showProgress(coreBytes + progress.doneBytes)
            }
            if (lockResult is InstallResult.Failed) {
                return lockResult
            }
        }
        if (wheels.isEmpty()) {
            return InstallResult.Installed
        }
        return installer.installWheels(wheels) { progress: DownloadProgress ->
            showProgress(coreBytes + lockBytes + progress.doneBytes)
        }
    }

    private fun showProgress(doneBytes: Long) {
        mutableState.update { current ->
            val download = current.download ?: return@update current
            current.copy(download = download.copy(doneBytes = doneBytes))
        }
    }

    private fun readFromFiles(): PythonState {
        if (!folder.isCoreInstalled()) {
            return PythonState(coreStatus = PythonCoreStatus.NOT_INSTALLED, storageBytes = folder.storageBytes())
        }
        val damaged = folder.damagedCoreFiles()
        if (damaged.isNotEmpty()) {
            // A damaged lock file may not even parse, so packages are not read from it.
            return PythonState(
                coreStatus = PythonCoreStatus.DAMAGED,
                damagedFiles = damaged,
                storageBytes = folder.storageBytes(),
            )
        }
        val lock = folder.lock()
        val addOnPackages = lock?.withDependencies(PyodideRelease.DATA_ADD_ON)?.map { lockPackage -> lockPackage.name }.orEmpty()
        return PythonState(
            coreStatus = PythonCoreStatus.INSTALLED,
            installedPackages = folder.installedPackageNames().sorted(),
            installedWheels = folder.installedWheels(),
            damagedWheelFiles = folder.damagedWheels().map { wheel -> wheel.fileName },
            dataAddOnPackages = addOnPackages.toSet(),
            storageBytes = folder.storageBytes(),
        )
    }
}
