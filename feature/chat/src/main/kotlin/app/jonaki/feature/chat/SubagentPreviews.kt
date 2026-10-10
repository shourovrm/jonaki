package app.jonaki.feature.chat

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.ThemeMode

// The D-029 check for the subagent rows, page and work sheet (D-126): a
// 360 dp wide phone at font scale 1.3, with tasks far longer than a row.

/** The laptop example from docs/mockups/subagents.html, cut to D-060's three subagents. */
internal object SubagentSamples {
    private const val START = 1_000_000L

    private val researcherRunning = SubagentUi(
        id = "s1",
        label = "researcher 1",
        delegateStepId = "d1",
        task = "Same search on ryans.com: laptops under ৳80,000 with 16 GB RAM and 512 GB SSD. " +
            "Note EMI offers from bKash and City Bank cards.",
        modelName = "Gemini 3.8 Flash",
        status = SubagentUiStatus.RUNNING,
        steps = listOf(
            StepUi("s1/c1", "web_search", StepUiStatus.DONE, "", query = "ryans laptop 16GB RAM 512GB SSD price", durationMillis = 1_600),
            StepUi("s1/c2", "web_fetch", StepUiStatus.DONE, "ryans.com/category/laptop-all-laptop", durationMillis = 3_100),
            StepUi("s1/c3", "notes", StepUiStatus.DONE, "", durationMillis = 100),
            StepUi("s1/c4", "web_fetch", StepUiStatus.RUNNING, "ryans.com/asus-vivobook-15-x1504va-i5-1335u-16gb"),
        ),
        costUsd = 0.012,
        latestText = "Ryans prices include 5% VAT.",
        answer = null,
        startedAtMillis = START,
        finishedAtMillis = null,
        notesPosted = 1,
    )

    private val researcherWaiting = SubagentUi(
        id = "s2",
        label = "researcher 2",
        delegateStepId = "d1",
        task = "Same search on daraz.com.bd, official brand stores only (Lenovo, ASUS, HP). Ignore resellers.",
        modelName = "Gemini 3.8 Flash",
        status = SubagentUiStatus.RUNNING,
        steps = listOf(
            StepUi("s2/c1", "web_search", StepUiStatus.DONE, "daraz.com.bd", durationMillis = 1_400),
            StepUi("s2/c2", "write_file", StepUiStatus.WAITING_FOR_APPROVAL, "work/daraz.csv"),
        ),
        costUsd = 0.006,
        latestText = null,
        answer = null,
        startedAtMillis = START,
        finishedAtMillis = null,
    )

    private val writerAsking = SubagentUi(
        id = "s3",
        label = "writer 3",
        delegateStepId = "d1",
        task = "Read inbox/cse-laptop-specs.pdf and draft artifacts/laptop-guide.html in Bangla for Ma",
        modelName = "DeepSeek V3.2",
        status = SubagentUiStatus.RUNNING,
        steps = listOf(
            StepUi("s3/c1", "read_document", StepUiStatus.DONE, "inbox/cse-laptop-specs.pdf", durationMillis = 1_200),
            StepUi("s3/c2", "ask_parent", StepUiStatus.RUNNING, "", query = "Bangla only, or English terms too?"),
        ),
        costUsd = 0.004,
        latestText = null,
        answer = null,
        startedAtMillis = START,
        finishedAtMillis = null,
    )

    private val running = listOf(researcherRunning, researcherWaiting, writerAsking)

    private val researcherOverBudget = researcherRunning.copy(
        status = SubagentUiStatus.COST_LIMIT,
        steps = researcherRunning.steps.map { step -> step.copy(status = StepUiStatus.DONE, durationMillis = 2_000) },
        costUsd = 0.1,
        answer = "Ryans: two models fit. The EMI rates need a City Bank login.",
        finishedAtMillis = START + 119_000,
    )

    val researcherSkipped: SubagentUi = researcherWaiting.copy(
        status = SubagentUiStatus.DONE,
        steps = listOf(
            StepUi("s2/c1", "web_search", StepUiStatus.DONE, "daraz.com.bd", durationMillis = 1_400),
            StepUi("s2/c2", "write_file", StepUiStatus.SKIPPED, "work/daraz.csv"),
            StepUi("s2/c3", "web_fetch", StepUiStatus.DONE, "daraz.com.bd/products/asus-vivobook-15-x1504va", durationMillis = 2_900),
        ),
        costUsd = 0.014,
        answer = "Two models from official stores fit the budget.\n\n" +
            "1. ASUS Vivobook 15 X1504VA, i5-1335U, 16 GB, 512 GB: ৳74,990 at the ASUS Official Store.\n" +
            "2. HP 15-fc0xxx, Ryzen 5 7520U, 16 GB, 512 GB: ৳66,500 at HP Official. RAM is soldered.\n\n" +
            "**Gaps**\n\nLenovo's store had no 16 GB model in stock.",
        finishedAtMillis = START + 112_000,
        notesPosted = 2,
    )

    private val writerDone = writerAsking.copy(
        status = SubagentUiStatus.DONE,
        steps = writerAsking.steps.map { step -> step.copy(status = StepUiStatus.DONE, durationMillis = 3_400) } +
            StepUi("s3/c3", "write_file", StepUiStatus.DONE, "artifacts/laptop-guide-for-ma-in-bangla-with-prices.html", durationMillis = 200),
        costUsd = 0.019,
        answer = "Wrote artifacts/laptop-guide-for-ma-in-bangla-with-prices.html.\n\n**Files written or changed**\n\n- work/specs.md",
        finishedAtMillis = START + 151_000,
        filesWritten = listOf("artifacts/laptop-guide-for-ma-in-bangla-with-prices.html", "work/specs.md"),
    )

    private val researcherFailed = researcherRunning.copy(
        status = SubagentUiStatus.FAILED,
        steps = researcherRunning.steps.map { step -> step.copy(status = StepUiStatus.DONE, durationMillis = 2_000) },
        failure = "Could not reach OpenRouter: Unable to resolve host \"openrouter.ai\": No address associated with hostname",
        finishedAtMillis = START + 87_000,
    )

    private val finished = listOf(researcherOverBudget, researcherSkipped, writerDone, researcherFailed)

    val runningRun = ChatItem.Run(
        id = "run-subagents",
        steps = listOf(
            StepUi("d0", "read_document", StepUiStatus.DONE, "inbox/cse-laptop-specs.pdf", durationMillis = 900),
            StepUi("d1", "delegate", StepUiStatus.RUNNING, "3 tasks"),
        ),
        isActive = true,
        subagents = running,
    )

    val approval = ChatItem.Approval(
        id = "s2/c2",
        toolName = "write_file",
        description = "Save the 38 Daraz listings so the writer can read them.",
    )

    val work = ChatItem.SubagentWork(
        id = "work-u1",
        subagents = finished,
        notesBoards = listOf(NotesBoardUi("work/delegations/3f9a1c2b7d4e/notes.md", notes = 3)),
    )

    val runningSubagent: SubagentUi = researcherRunning
    val runningSiblings: List<SubagentUi> = running
    val finishedSubagent: SubagentUi = writerDone
    val finishedSiblings: List<SubagentUi> = finished
}

@Composable
private fun RowsPreviewContent() {
    Surface {
        Column(Modifier.padding(18.dp)) {
            RunBlock(SubagentSamples.runningRun, onOpenStep = {})
            SubagentWorkRow(SubagentSamples.work, onOpen = {})
        }
    }
}

@Preview(name = "Subagent rows, dark", widthDp = 360, fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SubagentRowsDarkPreview() {
    JonakiTheme(ThemeMode.DARK) { RowsPreviewContent() }
}

@Preview(name = "Subagent rows, light", widthDp = 360, fontScale = 1.3f)
@Composable
private fun SubagentRowsLightPreview() {
    JonakiTheme(ThemeMode.LIGHT) { RowsPreviewContent() }
}

@Composable
private fun RunningPagePreviewContent() {
    SubagentPage(
        subagent = SubagentSamples.runningSubagent,
        siblings = SubagentSamples.runningSiblings,
        onSelect = {},
        onBack = {},
        onOpenFile = {},
        onOpenStep = {},
        onStop = {},
    )
}

@Composable
private fun FinishedPagePreviewContent() {
    SubagentPage(
        subagent = SubagentSamples.finishedSubagent,
        siblings = SubagentSamples.finishedSiblings,
        onSelect = {},
        onBack = {},
        onOpenFile = {},
        onOpenStep = {},
        onStop = null,
    )
}

@Preview(name = "Subagent page, running, dark", widthDp = 360, heightDp = 760, fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SubagentPageRunningDarkPreview() {
    JonakiTheme(ThemeMode.DARK) { RunningPagePreviewContent() }
}

@Preview(name = "Subagent page, running, light", widthDp = 360, heightDp = 760, fontScale = 1.3f)
@Composable
private fun SubagentPageRunningLightPreview() {
    JonakiTheme(ThemeMode.LIGHT) { RunningPagePreviewContent() }
}

@Preview(name = "Subagent page, done, dark", widthDp = 360, heightDp = 760, fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SubagentPageDoneDarkPreview() {
    JonakiTheme(ThemeMode.DARK) { FinishedPagePreviewContent() }
}

@Preview(name = "Subagent page, done, light", widthDp = 360, heightDp = 760, fontScale = 1.3f)
@Composable
private fun SubagentPageDoneLightPreview() {
    JonakiTheme(ThemeMode.LIGHT) { FinishedPagePreviewContent() }
}

@Composable
private fun WorkSheetPreviewContent() {
    Surface {
        SubagentWorkContent(SubagentSamples.work, onOpenSubagent = {}, onOpenFile = {})
    }
}

@Preview(name = "Work sheet, dark", widthDp = 360, fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SubagentWorkSheetDarkPreview() {
    JonakiTheme(ThemeMode.DARK) { WorkSheetPreviewContent() }
}

@Preview(name = "Work sheet, light", widthDp = 360, fontScale = 1.3f)
@Composable
private fun SubagentWorkSheetLightPreview() {
    JonakiTheme(ThemeMode.LIGHT) { WorkSheetPreviewContent() }
}
