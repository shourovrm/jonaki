package app.jonaki.update

import app.jonaki.core.appupdate.ApkDownloader
import app.jonaki.core.appupdate.LatestRelease
import app.jonaki.core.appupdate.UpdateCheckOutcome
import app.jonaki.core.appupdate.UpdateChecker
import app.jonaki.core.appupdate.UpdateFailure
import app.jonaki.feature.settings.UpdateFailureReason
import app.jonaki.feature.settings.UpdateUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * Runs the About page's update control. It does only what the user taps: a check, or a
 * download followed by Android's installer. No background work and no check at app start.
 */
class AppUpdateController(
    private val scope: CoroutineScope,
    httpClient: OkHttpClient,
    private val installedVersion: String,
    private val installer: ApkInstaller,
) {
    private val checker = UpdateChecker(httpClient)
    private val downloader = ApkDownloader(httpClient)
    private val mutableState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = mutableState

    private var newerRelease: LatestRelease? = null

    fun check() {
        if (mutableState.value is UpdateUiState.Checking || mutableState.value is UpdateUiState.Downloading) {
            return
        }
        mutableState.value = UpdateUiState.Checking
        scope.launch {
            mutableState.value = when (val outcome = checker.check(installedVersion)) {
                is UpdateCheckOutcome.UpToDate -> UpdateUiState.UpToDate(outcome.versionName)
                is UpdateCheckOutcome.NewerAvailable -> {
                    newerRelease = outcome.release
                    UpdateUiState.Available(outcome.release.versionName)
                }
                is UpdateCheckOutcome.Failed -> failedState(outcome.failure)
            }
        }
    }

    /** Downloads when the APK is not on the phone yet; installs when it is. */
    fun downloadOrInstall() {
        val release = newerRelease ?: return
        when (mutableState.value) {
            is UpdateUiState.ReadyToInstall, is UpdateUiState.NeedsInstallPermission -> install(release)
            is UpdateUiState.Available -> download(release)
            else -> Unit
        }
    }

    private fun download(release: LatestRelease) {
        mutableState.value = UpdateUiState.Downloading(percent = null)
        scope.launch {
            val failure = downloader.download(release.apkUrl, installer.apkFile) { percent ->
                mutableState.value = UpdateUiState.Downloading(percent)
            }
            if (failure != null) {
                mutableState.value = failedState(failure)
                return@launch
            }
            install(release)
        }
    }

    private fun install(release: LatestRelease) {
        if (!installer.canInstallFromThisApp()) {
            mutableState.value = UpdateUiState.NeedsInstallPermission(release.versionName)
            installer.openInstallPermissionSettings()
            return
        }
        mutableState.value = UpdateUiState.ReadyToInstall(release.versionName)
        installer.startInstaller()
    }

    private fun failedState(failure: UpdateFailure): UpdateUiState.Failed = when (failure) {
        UpdateFailure.NoNetwork -> UpdateUiState.Failed(UpdateFailureReason.NO_NETWORK)
        is UpdateFailure.HttpStatus -> UpdateUiState.Failed(UpdateFailureReason.HTTP_STATUS, failure.code)
        UpdateFailure.UnreadableAnswer -> UpdateUiState.Failed(UpdateFailureReason.UNREADABLE_ANSWER)
        UpdateFailure.DownloadBroken -> UpdateUiState.Failed(UpdateFailureReason.DOWNLOAD_BROKEN)
    }
}
