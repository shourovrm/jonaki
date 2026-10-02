package app.jonaki.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.DownloadProgress
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.UsageFormat

enum class PythonStatusUi {
    CHECKING,
    NOT_INSTALLED,
    INSTALLED,
    DAMAGED,
}

/** Everything Settings > Python shows (plan M8 step 4). */
@Immutable
data class PythonUiState(
    val status: PythonStatusUi,
    /** The pinned Pyodide release, for example "314.0.7" (D-069). */
    val version: String,
    val coreDownloadBytes: Long,
    val storageBytes: Long,
    val installedPackages: List<String> = emptyList(),
    val damagedFiles: List<String> = emptyList(),
    val dataAddOnInstalled: Boolean = false,
    val dataAddOnDownloadBytes: Long,
    /** Null while nothing downloads. */
    val download: PythonDownloadUi? = null,
    /** The last install's error, as the installer said it. */
    val problem: String? = null,
)

/** [packageNames] is empty while only Python itself downloads. */
@Immutable
data class PythonDownloadUi(
    val packageNames: List<String>,
    val doneBytes: Long,
    val totalBytes: Long?,
)

class PythonActions(
    val onBack: () -> Unit,
    /** Also repairs damaged files: only files with the wrong checksum are fetched again. */
    val onInstall: () -> Unit,
    val onCancel: () -> Unit,
    val onRemove: () -> Unit,
    val onInstallDataAddOn: () -> Unit,
    val onRemoveDataAddOn: () -> Unit,
    /** A package name from Pyodide's lock file, as typed. */
    val onInstallPackage: (name: String) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PythonScreen(state: PythonUiState, actions: PythonActions, modifier: Modifier = Modifier) {
    var confirmingRemove by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_python), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Card {
                StatusSection(state, actions, onRemove = { confirmingRemove = true })
            }
            if (state.status == PythonStatusUi.INSTALLED) {
                Spacer(Modifier.size(16.dp))
                Card {
                    PackagesSection(state, actions)
                }
            }
            val problem = state.problem
            if (problem != null) {
                Text(
                    problem,
                    style = MaterialTheme.typography.bodyMedium,
                    color = JonakiTheme.colors.deny,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                )
            }
        }
    }
    if (confirmingRemove) {
        AlertDialog(
            onDismissRequest = { confirmingRemove = false },
            title = { Text(stringResource(R.string.settings_python_remove_title)) },
            text = { Text(stringResource(R.string.settings_python_remove_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingRemove = false
                        actions.onRemove()
                    },
                ) {
                    Text(stringResource(R.string.settings_python_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingRemove = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusSection(state: PythonUiState, actions: PythonActions, onRemove: () -> Unit) {
    Column(Modifier.padding(16.dp)) {
        Text(stringResource(R.string.settings_python_status), style = MaterialTheme.typography.labelLarge)
        Text(
            statusText(state),
            style = MaterialTheme.typography.titleMedium,
            color = if (state.status == PythonStatusUi.DAMAGED) JonakiTheme.colors.deny else MaterialTheme.colorScheme.onSurface,
        )
        if (state.status == PythonStatusUi.DAMAGED) {
            Text(
                state.damagedFiles.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(12.dp))
        DetailLine(stringResource(R.string.settings_python_version), stringResource(R.string.settings_python_version_value, state.version))
        DetailLine(stringResource(R.string.settings_python_storage), UsageFormat.byteSize(state.storageBytes))
        val download = state.download
        if (download != null) {
            Spacer(Modifier.size(12.dp))
            Text(downloadingText(download), style = MaterialTheme.typography.bodyMedium)
            DownloadProgress(download.doneBytes, download.totalBytes, onCancel = actions.onCancel)
            return@Column
        }
        Spacer(Modifier.size(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state.status) {
                PythonStatusUi.CHECKING -> Unit
                PythonStatusUi.NOT_INSTALLED -> Button(onClick = actions.onInstall) {
                    Text(stringResource(R.string.settings_python_install, UsageFormat.byteSize(state.coreDownloadBytes)))
                }
                PythonStatusUi.DAMAGED -> {
                    Button(onClick = actions.onInstall) { Text(stringResource(R.string.settings_python_repair)) }
                    OutlinedButton(onClick = onRemove) { Text(stringResource(R.string.settings_python_remove)) }
                }
                PythonStatusUi.INSTALLED -> OutlinedButton(onClick = onRemove) {
                    Text(stringResource(R.string.settings_python_remove))
                }
            }
        }
        // Files of a cancelled download stay until removed; they still take space.
        if (state.status == PythonStatusUi.NOT_INSTALLED && state.storageBytes > 0) {
            TextButton(onClick = onRemove) { Text(stringResource(R.string.settings_python_remove_partial)) }
        }
    }
}

@Composable
private fun PackagesSection(state: PythonUiState, actions: PythonActions) {
    var packageName by rememberSaveable { mutableStateOf("") }
    val isDownloading = state.download != null
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(
            stringResource(R.string.settings_python_packages),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_python_data_add_on), style = MaterialTheme.typography.titleSmall)
                val detail = if (state.dataAddOnInstalled) {
                    stringResource(R.string.settings_python_installed)
                } else {
                    stringResource(R.string.settings_python_add_on_size, UsageFormat.byteSize(state.dataAddOnDownloadBytes))
                }
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            if (state.dataAddOnInstalled) {
                TextButton(onClick = actions.onRemoveDataAddOn, enabled = !isDownloading) {
                    Text(stringResource(R.string.settings_python_remove))
                }
            } else {
                TextButton(onClick = actions.onInstallDataAddOn, enabled = !isDownloading) {
                    Text(stringResource(R.string.settings_python_install_short))
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            if (state.installedPackages.isEmpty()) {
                stringResource(R.string.settings_python_no_packages)
            } else {
                state.installedPackages.joinToString(", ")
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp),
        ) {
            val install = {
                actions.onInstallPackage(packageName)
                packageName = ""
            }
            OutlinedTextField(
                value = packageName,
                onValueChange = { text -> packageName = text },
                label = { Text(stringResource(R.string.settings_python_package_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (packageName.isNotBlank() && !isDownloading) install() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = install, enabled = packageName.isNotBlank() && !isDownloading) {
                Text(stringResource(R.string.settings_python_install_short))
            }
        }
        Text(
            stringResource(R.string.settings_python_package_help, state.version),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun statusText(state: PythonUiState): String = stringResource(
    when (state.status) {
        PythonStatusUi.CHECKING -> R.string.settings_python_checking
        PythonStatusUi.NOT_INSTALLED -> R.string.settings_python_not_installed
        PythonStatusUi.INSTALLED -> R.string.settings_python_installed
        PythonStatusUi.DAMAGED -> R.string.settings_python_damaged
    },
)

@Composable
private fun downloadingText(download: PythonDownloadUi): String {
    if (download.packageNames.isEmpty()) {
        return stringResource(R.string.settings_python_downloading_python)
    }
    return stringResource(R.string.settings_python_downloading, download.packageNames.joinToString(", "))
}

/** A label and its value; the value wraps under the label when the line is too narrow (D-029). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailLine(label: String, value: String) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        content()
    }
}

object PythonSample {
    val notInstalled = PythonUiState(
        status = PythonStatusUi.NOT_INSTALLED,
        version = "314.0.7",
        coreDownloadBytes = 13_532_188,
        storageBytes = 0,
        dataAddOnDownloadBytes = 7_889_748,
    )

    val installed = notInstalled.copy(
        status = PythonStatusUi.INSTALLED,
        storageBytes = 21_421_936,
        installedPackages = listOf("numpy", "pandas", "python-dateutil", "pytz", "six"),
        dataAddOnInstalled = true,
        problem = "requests is not available for Pyodide 314.0.7",
    )

    val downloading = notInstalled.copy(
        download = PythonDownloadUi(packageNames = emptyList(), doneBytes = 4_200_000, totalBytes = 13_532_188),
    )
}
