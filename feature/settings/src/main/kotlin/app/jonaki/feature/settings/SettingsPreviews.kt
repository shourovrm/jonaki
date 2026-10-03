package app.jonaki.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.ThemeMode

// The D-029 check: a 360 dp wide phone at font scale 1.3, in dark, light and
// Bangla, for the first page and two sub-pages (D-128).

private val noActions = SettingsActions(
    onBack = {},
    onKeySave = { _, _ -> },
    onKeyClear = {},
    onSearchServiceMove = { _, _ -> },
    onWebSearchOffInNewThreadsChange = {},
    onThemeModeChange = {},
)

@Composable
private fun HomeSample(themeMode: ThemeMode) {
    JonakiTheme(themeMode) { SettingsHomeScreen(SettingsSample.state, noActions) }
}

@Composable
private fun PageSample(page: SettingsPage, themeMode: ThemeMode) {
    JonakiTheme(themeMode) { SettingsPageScreen(page, SettingsSample.state, noActions) }
}

@Preview(name = "Settings, dark", widthDp = 360, heightDp = 900, fontScale = 1.3f)
@Composable
private fun SettingsHomeDarkPreview() {
    HomeSample(ThemeMode.DARK)
}

@Preview(name = "Settings, light", widthDp = 360, heightDp = 900, fontScale = 1.3f)
@Composable
private fun SettingsHomeLightPreview() {
    HomeSample(ThemeMode.LIGHT)
}

@Preview(name = "Settings, Bangla", locale = "bn", widthDp = 360, heightDp = 900, fontScale = 1.3f)
@Composable
private fun SettingsHomeBanglaPreview() {
    HomeSample(ThemeMode.LIGHT)
}

@Preview(name = "Settings, search", widthDp = 360, heightDp = 600, fontScale = 1.3f)
@Composable
private fun SettingsSearchPreview() {
    JonakiTheme(ThemeMode.DARK) {
        SettingsHomeScreen(SettingsSample.state, noActions, searchQuery = "persona")
    }
}

@Preview(name = "Models, dark", widthDp = 360, heightDp = 1400, fontScale = 1.3f)
@Composable
private fun ModelsDarkPreview() {
    PageSample(SettingsPage.MODELS, ThemeMode.DARK)
}

@Preview(name = "Models, light", widthDp = 360, heightDp = 1400, fontScale = 1.3f)
@Composable
private fun ModelsLightPreview() {
    PageSample(SettingsPage.MODELS, ThemeMode.LIGHT)
}

@Preview(name = "Models, Bangla", locale = "bn", widthDp = 360, heightDp = 1400, fontScale = 1.3f)
@Composable
private fun ModelsBanglaPreview() {
    PageSample(SettingsPage.MODELS, ThemeMode.LIGHT)
}

@Preview(name = "Tools and approvals, dark", widthDp = 360, heightDp = 1000, fontScale = 1.3f)
@Composable
private fun ToolsDarkPreview() {
    PageSample(SettingsPage.TOOLS, ThemeMode.DARK)
}

@Preview(name = "Tools and approvals, light", widthDp = 360, heightDp = 1000, fontScale = 1.3f)
@Composable
private fun ToolsLightPreview() {
    PageSample(SettingsPage.TOOLS, ThemeMode.LIGHT)
}

@Preview(name = "Tools and approvals, Bangla", locale = "bn", widthDp = 360, heightDp = 1000, fontScale = 1.3f)
@Composable
private fun ToolsBanglaPreview() {
    PageSample(SettingsPage.TOOLS, ThemeMode.LIGHT)
}
