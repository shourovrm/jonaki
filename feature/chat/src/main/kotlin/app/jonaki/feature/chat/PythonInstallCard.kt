package app.jonaki.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.DownloadProgress
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.UsageFormat

/**
 * "Needs Python (13.5 MB). Install?" under the run whose run_code found
 * Python or its packages missing. After the install the user can try
 * again, or simply ask again (plan M8 step 4).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PythonInstallCard(item: ChatItem.PythonInstall, canTryAgain: Boolean, onAction: (ChatItem.PythonInstall, PythonCardAction) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 6.dp)) {
            Text(
                title(item),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(end = 8.dp),
            )
            val state = item.state
            when (state) {
                is PythonInstallState.Downloading -> DownloadProgress(
                    doneBytes = state.doneBytes,
                    totalBytes = state.totalBytes,
                    onCancel = { onAction(item, PythonCardAction.CANCEL) },
                    modifier = Modifier.padding(top = 10.dp),
                )
                is PythonInstallState.Failed -> Text(
                    state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = JonakiTheme.colors.deny,
                    modifier = Modifier.padding(top = 4.dp, end = 8.dp),
                )
                else -> Unit
            }
            if (state is PythonInstallState.Downloading) {
                return@Column
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                if (state is PythonInstallState.Installed) {
                    Button(onClick = { onAction(item, PythonCardAction.TRY_AGAIN) }, enabled = canTryAgain) {
                        Text(stringResource(R.string.chat_python_try_again))
                    }
                } else {
                    Button(onClick = { onAction(item, PythonCardAction.INSTALL) }) {
                        Text(stringResource(R.string.chat_python_install))
                    }
                }
                TextButton(onClick = { onAction(item, PythonCardAction.NOT_NOW) }) {
                    val dismiss = if (state is PythonInstallState.Installed) R.string.chat_python_close else R.string.chat_python_not_now
                    Text(stringResource(dismiss))
                }
            }
        }
    }
}

@Composable
private fun title(item: ChatItem.PythonInstall): String {
    val what = if (item.packageNames.isEmpty()) {
        stringResource(R.string.chat_python_python)
    } else {
        item.packageNames.joinToString(", ")
    }
    if (item.state is PythonInstallState.Installed) {
        return stringResource(R.string.chat_python_installed, what)
    }
    val bytes = item.downloadBytes ?: return stringResource(R.string.chat_python_needs, what)
    return stringResource(R.string.chat_python_needs_size, what, UsageFormat.byteSize(bytes))
}
