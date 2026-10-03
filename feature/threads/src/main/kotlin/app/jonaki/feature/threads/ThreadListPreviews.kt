package app.jonaki.feature.threads

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.ThemeMode

// The D-029 check for the revamped thread list (D-123): a 360 dp wide phone at
// font scale 1.3, with a 40-character thread title and a 40-character project name.

private const val PREVIEW_NOW = 1_790_000_000_000L

private const val MINUTE = 60_000L

private const val DAY = 24 * 60 * MINUTE

private val previewProjects = listOf(
    ProjectUi("home", "Home", instructions = "", modelKey = null, modelName = null),
    ProjectUi("long", "Quarterly sales analysis for the north z", instructions = "", modelKey = null, modelName = "GLM 5.3 Flash"),
)

private val previewState = ThreadListUiState(
    threads = listOf(
        ThreadRow("e", "Compare three e-readers for Bangla PDFs!", "Searching kobo.com for Bangla font support", PREVIEW_NOW - 20_000, ThreadRunState.Running(3)),
        ThreadRow("b", "অক্টোবরের বাজেট রিপোর্ট", "report.html saved, 3 charts", PREVIEW_NOW - 90 * MINUTE, ThreadRunState.Idle, costUsd = 0.0087),
        ThreadRow("l", "Lease agreement questions", "Needs approval: save notes.md", PREVIEW_NOW - 3 * 60 * MINUTE, ThreadRunState.WaitingForApproval, projectId = "home", projectName = "Home"),
        ThreadRow("i", "Doctor's appointment", "Asked about the clinic's hours", PREVIEW_NOW - 4 * 60 * MINUTE, ThreadRunState.Idle, costUsd = 0.0011, incognito = true),
        ThreadRow("k", "Kurzgesagt on sleep, summary", "Five points, with timestamps", PREVIEW_NOW - DAY, ThreadRunState.Idle, costUsd = 0.0311),
        ThreadRow("d", "Weekly plan for Dhaka trip", "Reminder set for Sunday 07:30", PREVIEW_NOW - 3 * DAY, ThreadRunState.Idle, costUsd = 0.0022),
        ThreadRow("o", "Kotlin coroutines notes", "Edited work/flows.md, 2 changes", PREVIEW_NOW - 12 * DAY, ThreadRunState.Failed),
    ),
    monthCostUsd = 0.21,
    projects = previewProjects,
)

@Composable
private fun ThreadListPreviewScreen() {
    ThreadListScreen(
        state = previewState,
        nowMillis = PREVIEW_NOW,
        onSearchQueryChange = {},
        onThreadClick = {},
        onNewThread = {},
        onOpenSettings = {},
    )
}

@Preview(name = "Threads, dark", widthDp = 360, heightDp = 760, fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ThreadListDarkPreview() {
    JonakiTheme(ThemeMode.DARK) { ThreadListPreviewScreen() }
}

@Preview(name = "Threads, light", widthDp = 360, heightDp = 760, fontScale = 1.3f)
@Composable
private fun ThreadListLightPreview() {
    JonakiTheme(ThemeMode.LIGHT) { ThreadListPreviewScreen() }
}
