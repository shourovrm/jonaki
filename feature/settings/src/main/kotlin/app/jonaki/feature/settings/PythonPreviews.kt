package app.jonaki.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.jonaki.core.ui.GroupDownloadUi
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.ToolGroupChoice
import app.jonaki.core.ui.ToolGroupRowUi

// The D-029 check: a 360 dp wide phone at font scale 1.3.

private val noActions = PythonActions(
    onBack = {},
    onInstall = {},
    onCancel = {},
    onRemove = {},
    onInstallDataAddOn = {},
    onRemoveDataAddOn = {},
    onInstallDocumentsAddOn = {},
    onRemoveDocumentsAddOn = {},
    onInstallPackage = {},
)

@Preview(name = "Python, not installed", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun PythonNotInstalledPreview() {
    JonakiTheme { PythonScreen(PythonSample.notInstalled, noActions) }
}

@Preview(name = "Python, downloading", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun PythonDownloadingPreview() {
    JonakiTheme { PythonScreen(PythonSample.downloading, noActions) }
}

@Preview(name = "Python, installed", widthDp = 360, heightDp = 1000, fontScale = 1.3f)
@Composable
private fun PythonInstalledPreview() {
    JonakiTheme { PythonScreen(PythonSample.installed, noActions) }
}

@Preview(name = "Tools", widthDp = 360, heightDp = 1400, fontScale = 1.3f)
@Composable
private fun ToolsPreview() {
    val rows = ToolGroupChoice.entries.map { group ->
        when (group) {
            ToolGroupChoice.FILES -> ToolGroupRowUi(group, isOn = true, canSwitch = false)
            ToolGroupChoice.PYTHON -> ToolGroupRowUi(group, isOn = true, download = GroupDownloadUi.Installed)
            ToolGroupChoice.DATA_ADD_ON -> ToolGroupRowUi(
                group,
                isOn = false,
                download = GroupDownloadUi.Failed("Could not install numpy: could not be downloaded (HTTP 503)", 7_889_748),
            )
            else -> ToolGroupRowUi(group, isOn = true)
        }
    }
    JonakiTheme { ToolsScreen(rows, onSwitch = { _, _ -> }, onDownload = {}, onCancelDownload = {}, onBack = {}) }
}
