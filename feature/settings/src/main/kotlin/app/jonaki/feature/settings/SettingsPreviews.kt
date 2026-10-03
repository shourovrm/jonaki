package app.jonaki.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.jonaki.core.ui.JonakiTheme

// The D-029 check in Bangla (M11): a 360 dp wide phone at font scale 1.3.

private val noActions = SettingsActions(
    onBack = {},
    onKeySave = { _, _ -> },
    onKeyClear = {},
    onSearchServiceMove = { _, _ -> },
    onWebSearchOffInNewThreadsChange = {},
    onThemeModeChange = {},
)

@Preview(name = "Settings, Bangla", locale = "bn", widthDp = 360, heightDp = 2400, fontScale = 1.3f)
@Composable
private fun SettingsBanglaPreview() {
    JonakiTheme { SettingsHomeScreen(SettingsSample.state, noActions) }
}
