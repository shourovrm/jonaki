package app.jonaki.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.jonaki.core.ui.JonakiTheme

// The D-029 check in Bangla (M11): a 360 dp wide phone at font scale 1.3,
// with a 40-character title and an approval card's buttons.

/** 40 characters, as in the thread list preview. */
private const val LONG_BANGLA_TITLE = "নমুনার আকার: হাসপাতাল জরিপের নতুন গবেষণা"

@Composable
private fun ChatBanglaSample(state: ChatUiState) {
    JonakiTheme {
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
}

@Preview(name = "Chat with approval, Bangla", locale = "bn", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun ChatBanglaPreview() {
    ChatBanglaSample(ChatSample.running.copy(title = LONG_BANGLA_TITLE))
}

@Preview(name = "Chat finished, Bangla", locale = "bn", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun ChatFinishedBanglaPreview() {
    ChatBanglaSample(ChatSample.finished)
}
