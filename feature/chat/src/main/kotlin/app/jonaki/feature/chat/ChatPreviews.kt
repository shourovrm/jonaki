package app.jonaki.feature.chat

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.ThemeMode

// The D-029 check for the revamped chat (D-123): a 360 dp wide phone at font
// scale 1.3, with a 40-character title and a 40-character model name.

private const val LONG_TITLE = "Compare three e-readers for Bangla PDFs!"

private const val LONG_MODEL_NAME = "deepseek/deepseek-v4-flash-0925-preview-x"

private val previewSubagent = ChatItem.Subagent(
    id = "sub1",
    label = "researcher 2",
    task = "Find whether the Kobo Clara BW renders embedded Bangla fonts in PDFs",
    status = SubagentUiStatus.RUNNING,
    steps = listOf(
        StepUi("sub1-s1", "web_search", StepUiStatus.DONE, "Tavily · 5 results", durationMillis = 1_800),
        StepUi("sub1-s2", "web_fetch", StepUiStatus.RUNNING, "help.kobo.com/hc/en-us/articles/360017"),
    ),
    costUsd = 0.0031,
    latestText = "The Kobo help pages list the supported PDF fonts.",
    answer = null,
)

private val previewState: ChatUiState = ChatSample.withUsage.copy(
    title = LONG_TITLE,
    status = ChatSample.withUsage.status?.copy(modelName = LONG_MODEL_NAME),
    items = ChatSample.withUsage.items + previewSubagent,
)

@Composable
private fun ChatPreviewScreen(state: ChatUiState) {
    ChatScreen(
        state = state,
        onBack = {},
        onDraftChange = {},
        onSend = {},
        onStop = {},
        onApprovalChoice = { _, _ -> },
        onRetry = {},
        onWebSearchChange = {},
    )
}

@Preview(name = "Chat, dark", widthDp = 360, heightDp = 760, fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatDarkPreview() {
    JonakiTheme(ThemeMode.DARK) { ChatPreviewScreen(previewState) }
}

@Preview(name = "Chat, light", widthDp = 360, heightDp = 760, fontScale = 1.3f)
@Composable
private fun ChatLightPreview() {
    JonakiTheme(ThemeMode.LIGHT) { ChatPreviewScreen(previewState) }
}

@Preview(name = "Chat, finished, light", widthDp = 360, heightDp = 760, fontScale = 1.3f)
@Composable
private fun ChatFinishedPreview() {
    JonakiTheme(ThemeMode.LIGHT) { ChatPreviewScreen(ChatSample.finished.copy(title = LONG_TITLE)) }
}

@Composable
private fun StatusStripPreviewContent() {
    val status = ChatStatusUi(
        modelName = LONG_MODEL_NAME,
        contextWindowTokens = 200_000,
        contextUsedTokens = 48_210,
        costUsd = 0.0134,
    )
    Surface {
        Column {
            StatusStrip(status, isRunning = true, onModelClick = {}, onCostClick = {}, webSearchEnabled = true, onWebSearchChange = {})
            StatusStrip(status, isRunning = false, onModelClick = {}, onCostClick = {}, webSearchEnabled = false, onWebSearchChange = {})
            StatusStrip(
                status.copy(bypassApprovals = true),
                isRunning = false,
                onModelClick = {},
                onCostClick = {},
                webSearchEnabled = false,
                onWebSearchChange = {},
            )
        }
    }
}

@Preview(name = "Status strip, dark", widthDp = 360, fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StatusStripDarkPreview() {
    JonakiTheme(ThemeMode.DARK) { StatusStripPreviewContent() }
}

@Preview(name = "Status strip, light", widthDp = 360, fontScale = 1.3f)
@Composable
private fun StatusStripLightPreview() {
    JonakiTheme(ThemeMode.LIGHT) { StatusStripPreviewContent() }
}
