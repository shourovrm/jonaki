package app.jonaki.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** The tool groups of the first-run picker and Settings > Tools, in their order on screen. */
enum class ToolGroupChoice {
    FILES,
    WEB,
    YOUTUBE,
    MEMORY,
    SUBAGENTS,
    REPORTS,
    SHARE,
    PHONE,
    SCHEDULE,
    MCP,
    JAVASCRIPT,
    PYTHON,
    DATA_ADD_ON,
}

/** A download that a group needs: Python and its data add-on. */
@Immutable
sealed interface GroupDownloadUi {
    data class Missing(val downloadBytes: Long) : GroupDownloadUi

    /** [totalBytes] is null when the size is not known before the download. */
    data class Downloading(val doneBytes: Long, val totalBytes: Long?) : GroupDownloadUi

    data object Installed : GroupDownloadUi

    data object Damaged : GroupDownloadUi

    data class Failed(val message: String, val downloadBytes: Long) : GroupDownloadUi
}

@Immutable
data class ToolGroupRowUi(
    val group: ToolGroupChoice,
    val isOn: Boolean,
    /** Files cannot be switched off: every other tool works on thread files. */
    val canSwitch: Boolean = true,
    /** Null for groups that download nothing. */
    val download: GroupDownloadUi? = null,
)

/**
 * One row per tool group with a one-line description and a switch. The
 * Python rows say their download size and show the download's progress.
 */
@Composable
fun ToolGroupList(
    rows: List<ToolGroupRowUi>,
    onSwitch: (ToolGroupChoice, Boolean) -> Unit,
    onDownload: (ToolGroupChoice) -> Unit,
    onCancelDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            ToolGroupRow(row, onSwitch, onDownload, onCancelDownload)
        }
    }
}

@Composable
private fun ToolGroupRow(
    row: ToolGroupRowUi,
    onSwitch: (ToolGroupChoice, Boolean) -> Unit,
    onDownload: (ToolGroupChoice) -> Unit,
    onCancelDownload: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = row.isOn,
                    enabled = row.canSwitch,
                    role = Role.Switch,
                    onValueChange = { on -> onSwitch(row.group, on) },
                )
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(toolGroupName(row.group), style = MaterialTheme.typography.titleSmall)
                Text(
                    toolGroupDescription(row.group),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val download = row.download
                if (download != null && download !is GroupDownloadUi.Downloading) {
                    DownloadStateLine(download)
                }
            }
            Spacer(Modifier.width(12.dp))
            // A group that is always on (Files) keeps the "on" colours; Material's disabled look reads as off.
            val enabledColors = SwitchDefaults.colors()
            val colors = SwitchDefaults.colors(
                disabledCheckedThumbColor = enabledColors.checkedThumbColor,
                disabledCheckedTrackColor = enabledColors.checkedTrackColor,
                disabledCheckedBorderColor = enabledColors.checkedBorderColor,
                disabledCheckedIconColor = enabledColors.checkedIconColor,
            )
            Switch(checked = row.isOn, onCheckedChange = null, enabled = row.canSwitch, colors = colors)
        }
        val download = row.download
        if (download is GroupDownloadUi.Downloading) {
            DownloadProgress(
                doneBytes = download.doneBytes,
                totalBytes = download.totalBytes,
                onCancel = onCancelDownload,
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
            )
        }
        val offersDownload = download is GroupDownloadUi.Missing ||
            download is GroupDownloadUi.Damaged ||
            download is GroupDownloadUi.Failed
        if (row.isOn && row.canSwitch && offersDownload) {
            val label = if (download is GroupDownloadUi.Missing) R.string.ui_download else R.string.ui_download_again
            TextButton(onClick = { onDownload(row.group) }, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)) {
                Text(stringResource(label))
            }
        }
    }
}

@Composable
private fun DownloadStateLine(download: GroupDownloadUi) {
    val (text, isProblem) = when (download) {
        is GroupDownloadUi.Missing -> stringResource(R.string.ui_download_size, UsageFormat.byteSize(download.downloadBytes)) to false
        is GroupDownloadUi.Installed -> stringResource(R.string.ui_installed) to false
        is GroupDownloadUi.Damaged -> stringResource(R.string.ui_damaged) to true
        is GroupDownloadUi.Failed -> download.message to true
        is GroupDownloadUi.Downloading -> return
    }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (isProblem) JonakiTheme.colors.deny else MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 2.dp),
    )
}

/**
 * A progress bar with "3.2 of 13.5 MB" and Cancel; without a known total the
 * bar moves on its own and only the downloaded amount shows.
 */
@Composable
fun DownloadProgress(doneBytes: Long, totalBytes: Long?, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        if (totalBytes != null && totalBytes > 0) {
            LinearProgressIndicator(
                progress = { (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(end = 8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val text = if (totalBytes != null) {
                stringResource(R.string.ui_downloaded_of, UsageFormat.byteSize(doneBytes), UsageFormat.byteSize(totalBytes))
            } else {
                stringResource(R.string.ui_downloaded, UsageFormat.byteSize(doneBytes))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.ui_cancel))
            }
        }
    }
}

@Composable
fun toolGroupName(group: ToolGroupChoice): String = stringResource(
    when (group) {
        ToolGroupChoice.FILES -> R.string.ui_tools_files
        ToolGroupChoice.WEB -> R.string.ui_tools_web
        ToolGroupChoice.YOUTUBE -> R.string.ui_tools_youtube
        ToolGroupChoice.MEMORY -> R.string.ui_tools_memory
        ToolGroupChoice.SUBAGENTS -> R.string.ui_tools_subagents
        ToolGroupChoice.REPORTS -> R.string.ui_tools_reports
        ToolGroupChoice.SHARE -> R.string.ui_tools_share
        ToolGroupChoice.PHONE -> R.string.ui_tools_phone
        ToolGroupChoice.SCHEDULE -> R.string.ui_tools_schedule
        ToolGroupChoice.MCP -> R.string.ui_tools_mcp
        ToolGroupChoice.JAVASCRIPT -> R.string.ui_tools_javascript
        ToolGroupChoice.PYTHON -> R.string.ui_tools_python
        ToolGroupChoice.DATA_ADD_ON -> R.string.ui_tools_data_add_on
    },
)

@Composable
private fun toolGroupDescription(group: ToolGroupChoice): String = stringResource(
    when (group) {
        ToolGroupChoice.FILES -> R.string.ui_tools_files_help
        ToolGroupChoice.WEB -> R.string.ui_tools_web_help
        ToolGroupChoice.YOUTUBE -> R.string.ui_tools_youtube_help
        ToolGroupChoice.MEMORY -> R.string.ui_tools_memory_help
        ToolGroupChoice.SUBAGENTS -> R.string.ui_tools_subagents_help
        ToolGroupChoice.REPORTS -> R.string.ui_tools_reports_help
        ToolGroupChoice.SHARE -> R.string.ui_tools_share_help
        ToolGroupChoice.PHONE -> R.string.ui_tools_phone_help
        ToolGroupChoice.SCHEDULE -> R.string.ui_tools_schedule_help
        ToolGroupChoice.MCP -> R.string.ui_tools_mcp_help
        ToolGroupChoice.JAVASCRIPT -> R.string.ui_tools_javascript_help
        ToolGroupChoice.PYTHON -> R.string.ui_tools_python_help
        ToolGroupChoice.DATA_ADD_ON -> R.string.ui_tools_data_add_on_help
    },
)
