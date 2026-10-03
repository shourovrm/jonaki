package app.jonaki.feature.threads

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.jonaki.core.ui.JonakiTheme

// The D-029 check in Bangla (M11): a 360 dp wide phone at font scale 1.3.

private const val PREVIEW_NOW = 1_790_000_000_000L

/** 40 characters: the list must keep it on one line and end it in "…". */
private const val LONG_BANGLA_TITLE = "নমুনার আকার: হাসপাতাল জরিপের নতুন গবেষণা"

@Preview(name = "Thread list, Bangla", locale = "bn", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun ThreadListBanglaPreview() {
    val sample = ThreadListSample.state(PREVIEW_NOW)
    val longTitled = sample.threads.first().copy(title = LONG_BANGLA_TITLE)
    JonakiTheme {
        ThreadListScreen(
            state = sample.copy(threads = listOf(longTitled) + sample.threads.drop(1)),
            nowMillis = PREVIEW_NOW,
            onSearchQueryChange = {},
            onThreadClick = {},
            onNewThread = {},
            onOpenSettings = {},
        )
    }
}

@Preview(name = "Thread list empty, Bangla", locale = "bn", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun ThreadListEmptyBanglaPreview() {
    JonakiTheme {
        ThreadListScreen(
            state = ThreadListSample.empty,
            nowMillis = PREVIEW_NOW,
            onSearchQueryChange = {},
            onThreadClick = {},
            onNewThread = {},
            onOpenSettings = {},
        )
    }
}
