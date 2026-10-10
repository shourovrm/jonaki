package app.jonaki.feature.threads

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.jonaki.core.ui.JonakiTheme

// The D-029 check for selection mode: 360 dp wide at font scale 1.3, with a
// 40-character thread name that is selected.

private const val PREVIEW_NOW = 1_790_000_000_000L

/** 40 characters. */
private const val LONG_ENGLISH_TITLE = "Compare three e-readers for Bangla PDFs!"

/** 40 characters. */
private const val LONG_BANGLA_TITLE = "নমুনার আকার: হাসপাতাল জরিপের নতুন গবেষণা"

@Composable
private fun SelectionPreviewScreen(longTitle: String) {
    val sample = ThreadListSample.state(PREVIEW_NOW)
    val longTitled = sample.threads.first().copy(title = longTitle)
    JonakiTheme {
        ThreadListScreen(
            state = sample.copy(threads = listOf(longTitled) + sample.threads.drop(1)),
            nowMillis = PREVIEW_NOW,
            onSearchQueryChange = {},
            onThreadClick = {},
            onNewThread = {},
            onOpenSettings = {},
            initialSelectedIds = setOf(longTitled.id, "sylhet"),
        )
    }
}

@Preview(name = "Thread selection", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun ThreadSelectionPreview() {
    SelectionPreviewScreen(LONG_ENGLISH_TITLE)
}

@Preview(name = "Thread selection, Bangla", locale = "bn", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun ThreadSelectionBanglaPreview() {
    SelectionPreviewScreen(LONG_BANGLA_TITLE)
}
