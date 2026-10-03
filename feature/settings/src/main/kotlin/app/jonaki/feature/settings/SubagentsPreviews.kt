package app.jonaki.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.ThemeMode

// The D-029 check for Settings > Subagents and its editor (D-138): a 360 dp
// wide phone at font scale 1.3, in dark, light and Bangla, with a 30-character
// name and a long description.

private val noActions = SettingsActions(
    onBack = {},
    onKeySave = { _, _ -> },
    onKeyClear = {},
    onSearchServiceMove = { _, _ -> },
    onWebSearchOffInNewThreadsChange = {},
    onThemeModeChange = {},
)

private val sampleSubagent = CustomSubagentEditorUi(
    name = "bangla-legal-summary-writer-02",
    description = "Summarises court papers and land deeds in plain Bangla for a reader without legal training.",
    instructions = "Read the papers in inbox/. Write a summary of at most one page.\nEnd with the dates and amounts named.",
    tools = listOf("read_file", "read_document", "write_file"),
    modelKey = "openrouter:qwen/qwen-4-coder-480b-a35b-instruct-turbo",
)

private val sampleTools = listOf(
    "read_file", "write_file", "edit_file", "find_files", "search_files", "read_document", "view_image",
    "web_search", "web_fetch", "youtube_summarize", "artifact", "share_file", "phone", "schedule", "mcp", "run_code",
)

@Composable
private fun PageSample(themeMode: ThemeMode) {
    JonakiTheme(themeMode) { SettingsPageScreen(SettingsPage.SUBAGENTS, SettingsSample.state, noActions) }
}

@Composable
private fun EditorSample(themeMode: ThemeMode) {
    JonakiTheme(themeMode) {
        CustomSubagentEditorScreen(
            initial = sampleSubagent,
            takenNames = setOf("researcher", "scout", "writer", "worker", sampleSubagent.name),
            toolOptions = sampleTools,
            modelOptions = listOf(
                ModelOptionUi("openrouter:z-ai/glm-5.3-flash", "GLM 5.3 Flash"),
                ModelOptionUi("openrouter:qwen/qwen-4-coder-480b-a35b-instruct-turbo", "Qwen 4 Coder 480B A35B Instruct Turbo"),
            ),
            onSave = {},
            onBack = {},
            onDelete = {},
        )
    }
}

@Preview(name = "Subagents, dark", widthDp = 360, heightDp = 1500, fontScale = 1.3f)
@Composable
private fun SubagentsDarkPreview() {
    PageSample(ThemeMode.DARK)
}

@Preview(name = "Subagents, light", widthDp = 360, heightDp = 1500, fontScale = 1.3f)
@Composable
private fun SubagentsLightPreview() {
    PageSample(ThemeMode.LIGHT)
}

@Preview(name = "Subagents, Bangla", locale = "bn", widthDp = 360, heightDp = 1500, fontScale = 1.3f)
@Composable
private fun SubagentsBanglaPreview() {
    PageSample(ThemeMode.LIGHT)
}

@Preview(name = "Subagent editor, dark", widthDp = 360, heightDp = 1800, fontScale = 1.3f)
@Composable
private fun EditorDarkPreview() {
    EditorSample(ThemeMode.DARK)
}

@Preview(name = "Subagent editor, Bangla", locale = "bn", widthDp = 360, heightDp = 1800, fontScale = 1.3f)
@Composable
private fun EditorBanglaPreview() {
    EditorSample(ThemeMode.LIGHT)
}
