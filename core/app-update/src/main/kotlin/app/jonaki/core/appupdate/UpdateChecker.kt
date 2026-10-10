package app.jonaki.core.appupdate

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

const val LATEST_RELEASE_URL = "https://api.github.com/repos/shourovrm/jonaki/releases/latest"

sealed interface UpdateCheckOutcome {
    data class UpToDate(val versionName: String) : UpdateCheckOutcome
    data class NewerAvailable(val release: LatestRelease) : UpdateCheckOutcome
    data class Failed(val failure: UpdateFailure) : UpdateCheckOutcome
}

/** Asks GitHub for the latest release and compares it with the installed version. */
class UpdateChecker(
    private val httpClient: OkHttpClient,
    private val latestReleaseUrl: String = LATEST_RELEASE_URL,
) {
    suspend fun check(installedVersion: String): UpdateCheckOutcome = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(latestReleaseUrl)
            .header("Accept", "application/vnd.github+json")
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateCheckOutcome.Failed(UpdateFailure.HttpStatus(response.code))
                }
                val release = parseLatestRelease(response.body?.string().orEmpty())
                    ?: return@withContext UpdateCheckOutcome.Failed(UpdateFailure.UnreadableAnswer)
                if (isNewerVersion(candidate = release.versionName, installed = installedVersion)) {
                    UpdateCheckOutcome.NewerAvailable(release)
                } else {
                    UpdateCheckOutcome.UpToDate(installedVersion)
                }
            }
        } catch (networkError: IOException) {
            UpdateCheckOutcome.Failed(UpdateFailure.NoNetwork)
        }
    }
}
