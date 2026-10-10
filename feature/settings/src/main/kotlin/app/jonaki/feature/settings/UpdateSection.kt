package app.jonaki.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** What the About page's update control shows. Nothing is checked until the user taps. */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class UpToDate(val versionName: String) : UpdateUiState
    data class Available(val versionName: String) : UpdateUiState

    /** [percent] is null while the server has not said how big the file is. */
    data class Downloading(val percent: Int?) : UpdateUiState
    data class ReadyToInstall(val versionName: String) : UpdateUiState

    /** Android needs the user to allow installs from Jonaki before it will install. */
    data class NeedsInstallPermission(val versionName: String) : UpdateUiState
    data class Failed(val reason: UpdateFailureReason, val httpCode: Int = 0) : UpdateUiState
}

enum class UpdateFailureReason {
    NO_NETWORK,
    HTTP_STATUS,
    UNREADABLE_ANSWER,
    DOWNLOAD_BROKEN,
}

/** One status line and the button that fits it, under the About text. */
@Composable
internal fun UpdateSection(
    state: UpdateUiState,
    onCheckForUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        val statusLine = statusLineFor(state)
        if (statusLine != null) {
            Text(
                statusLine,
                style = MaterialTheme.typography.bodyMedium,
                color = if (state is UpdateUiState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (state is UpdateUiState.Downloading) {
            val percent = state.percent
            val barModifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            if (percent == null) {
                LinearProgressIndicator(modifier = barModifier)
            } else {
                LinearProgressIndicator(progress = { percent / 100f }, modifier = barModifier)
            }
        }
        UpdateButton(state, onCheckForUpdate, onInstallUpdate)
    }
}

@Composable
private fun statusLineFor(state: UpdateUiState): String? = when (state) {
    UpdateUiState.Idle -> null
    UpdateUiState.Checking -> stringResource(R.string.settings_update_checking)
    is UpdateUiState.UpToDate -> stringResource(R.string.settings_update_up_to_date, state.versionName)
    is UpdateUiState.Available -> stringResource(R.string.settings_update_available, state.versionName)
    is UpdateUiState.Downloading -> if (state.percent == null) {
        stringResource(R.string.settings_update_downloading_unknown)
    } else {
        stringResource(R.string.settings_update_downloading, state.percent)
    }
    is UpdateUiState.ReadyToInstall -> stringResource(R.string.settings_update_ready, state.versionName)
    is UpdateUiState.NeedsInstallPermission -> stringResource(R.string.settings_update_allow_installs)
    is UpdateUiState.Failed -> when (state.reason) {
        UpdateFailureReason.NO_NETWORK -> stringResource(R.string.settings_update_failed_no_network)
        UpdateFailureReason.HTTP_STATUS -> stringResource(R.string.settings_update_failed_http, state.httpCode)
        UpdateFailureReason.UNREADABLE_ANSWER -> stringResource(R.string.settings_update_failed_unreadable)
        UpdateFailureReason.DOWNLOAD_BROKEN -> stringResource(R.string.settings_update_failed_download)
    }
}

@Composable
private fun UpdateButton(state: UpdateUiState, onCheckForUpdate: () -> Unit, onInstallUpdate: () -> Unit) {
    val buttonModifier = Modifier.heightIn(min = 48.dp)
    when (state) {
        is UpdateUiState.Checking, is UpdateUiState.Downloading -> Unit
        is UpdateUiState.Available -> Button(onClick = onInstallUpdate, modifier = buttonModifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.settings_update_download_install))
        }
        is UpdateUiState.ReadyToInstall, is UpdateUiState.NeedsInstallPermission ->
            Button(onClick = onInstallUpdate, modifier = buttonModifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.settings_update_install))
            }
        UpdateUiState.Idle, is UpdateUiState.UpToDate, is UpdateUiState.Failed ->
            OutlinedButton(onClick = onCheckForUpdate, modifier = buttonModifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.settings_update_check))
            }
    }
}
