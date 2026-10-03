package app.jonaki.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.jonaki.core.ui.GroupDownloadUi
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.ToolGroupChoice
import app.jonaki.core.ui.ToolGroupRowUi

// The D-029 check: a 360 dp wide phone at font scale 1.3.

private val sampleRows: List<ToolGroupRowUi> = ToolGroupChoice.entries.map { group ->
    when (group) {
        ToolGroupChoice.FILES -> ToolGroupRowUi(group, isOn = true, canSwitch = false)
        ToolGroupChoice.SHARE -> ToolGroupRowUi(group, isOn = false)
        ToolGroupChoice.PYTHON -> ToolGroupRowUi(
            group,
            isOn = true,
            download = GroupDownloadUi.Downloading(doneBytes = 4_200_000, totalBytes = 13_532_188),
        )
        ToolGroupChoice.DATA_ADD_ON -> ToolGroupRowUi(group, isOn = false, download = GroupDownloadUi.Missing(7_889_748))
        else -> ToolGroupRowUi(group, isOn = true)
    }
}

@Preview(name = "Tool picker", widthDp = 360, heightDp = 1400, fontScale = 1.3f)
@Composable
private fun ToolPickerPreview() {
    JonakiTheme {
        ToolPickerScreen(sampleRows, onSwitch = { _, _ -> }, onDownload = {}, onCancelDownload = {}, onContinue = {})
    }
}

@Preview(name = "Tool picker, Python missing", widthDp = 360, heightDp = 1400, fontScale = 1.3f)
@Composable
private fun ToolPickerPythonMissingPreview() {
    val rows = sampleRows.map { row ->
        if (row.group == ToolGroupChoice.PYTHON) row.copy(download = GroupDownloadUi.Missing(13_532_188)) else row
    }
    JonakiTheme {
        ToolPickerScreen(rows, onSwitch = { _, _ -> }, onDownload = {}, onCancelDownload = {}, onContinue = {})
    }
}

@Preview(name = "Tool picker, Bangla", locale = "bn", widthDp = 360, heightDp = 1400, fontScale = 1.3f)
@Composable
private fun ToolPickerBanglaPreview() {
    JonakiTheme {
        ToolPickerScreen(sampleRows, onSwitch = { _, _ -> }, onDownload = {}, onCancelDownload = {}, onContinue = {})
    }
}
