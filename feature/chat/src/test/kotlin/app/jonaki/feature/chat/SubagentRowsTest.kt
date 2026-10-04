package app.jonaki.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubagentRowsTest {
    private fun subagent(
        status: SubagentUiStatus = SubagentUiStatus.RUNNING,
        steps: List<StepUi> = emptyList(),
        latestText: String? = null,
        filesWritten: List<String> = emptyList(),
        notesPosted: Int = 0,
        startedAtMillis: Long = 1_000,
        finishedAtMillis: Long? = null,
    ) = SubagentUi(
        id = "s1",
        label = "researcher 2",
        delegateStepId = "d1",
        task = "Same search on ryans.com",
        modelName = "Gemini 3.8 Flash",
        status = status,
        steps = steps,
        costUsd = 0.012,
        latestText = latestText,
        answer = null,
        startedAtMillis = startedAtMillis,
        finishedAtMillis = finishedAtMillis,
        filesWritten = filesWritten,
        notesPosted = notesPosted,
    )

    private fun step(id: String, toolName: String, status: StepUiStatus, detail: String = "") =
        StepUi(id, toolName, status, detail)

    @Test
    fun aRunningSubagentShowsItsRunningStepAndIsLit() {
        val fetch = step("c2", "web_fetch", StepUiStatus.RUNNING, "ryans.com/asus-vivobook-15")
        val row = SubagentRows.rowOf(subagent(steps = listOf(step("c1", "web_search", StepUiStatus.DONE), fetch)))

        assertEquals(SubagentRowIcon.LIVE, row.icon)
        assertEquals(SubagentNow.Working(fetch), row.now)
        assertFalse(row.needsLook)
    }

    @Test
    fun aCardWaitingForTheUserWinsOverOtherWorkAndNeedsALook() {
        val steps = listOf(
            step("c1", "web_fetch", StepUiStatus.RUNNING),
            step("c2", "write_file", StepUiStatus.WAITING_FOR_APPROVAL, "work/daraz.csv"),
        )
        val row = SubagentRows.rowOf(subagent(steps = steps))

        assertEquals(SubagentRowIcon.WAITING_FOR_YOU, row.icon)
        assertEquals(SubagentNow.WaitingForYou, row.now)
        assertTrue(row.needsLook)
    }

    @Test
    fun askParentReadsAsAskingTheMainAgent() {
        val row = SubagentRows.rowOf(subagent(steps = listOf(step("c1", "ask_parent", StepUiStatus.RUNNING))))

        assertEquals(SubagentRowIcon.ASKING_MAIN_AGENT, row.icon)
        assertEquals(SubagentNow.AskingMainAgent, row.now)
    }

    @Test
    fun betweenStepsItShowsTheFirstLineItWroteOrThinking() {
        val wrote = SubagentRows.rowOf(subagent(latestText = "\n  Ryans lists prices with VAT.\nMore text"))
        val quiet = SubagentRows.rowOf(subagent())

        assertEquals(SubagentNow.Wrote("Ryans lists prices with VAT."), wrote.now)
        assertEquals(SubagentNow.Thinking, quiet.now)
        assertEquals(SubagentRowIcon.LIVE, quiet.icon)
    }

    @Test
    fun doneCountsItsSkippedPartsAndFilesAndTime() {
        val steps = listOf(
            step("c1", "web_search", StepUiStatus.DONE),
            step("c2", "write_file", StepUiStatus.SKIPPED, "work/daraz.csv"),
        )
        val row = SubagentRows.rowOf(
            subagent(
                status = SubagentUiStatus.DONE,
                steps = steps,
                filesWritten = listOf("artifacts/guide.html"),
                finishedAtMillis = 42_000,
            ),
        )

        assertEquals(SubagentRowIcon.DONE, row.icon)
        assertEquals(SubagentNow.Done(skippedParts = 1, filesWritten = 1, durationMillis = 41_000), row.now)
        assertFalse(row.needsLook)
    }

    @Test
    fun everyEarlyStopIsAWarningThatNeedsALook() {
        val earlyStops = listOf(
            SubagentUiStatus.COST_LIMIT,
            SubagentUiStatus.TIME_LIMIT,
            SubagentUiStatus.FAILED,
            SubagentUiStatus.STOPPED,
        )
        for (status in earlyStops) {
            val row = SubagentRows.rowOf(subagent(status = status, finishedAtMillis = 5_000))

            assertEquals(SubagentRowIcon.WARNING, row.icon)
            assertEquals(SubagentNow.Ended(status), row.now)
            assertTrue(row.needsLook)
        }
    }

    @Test
    fun aSubagentAtItsStepLimitReadsAsWorkDoneNotAsAWarning() {
        val row = SubagentRows.rowOf(subagent(status = SubagentUiStatus.STEP_LIMIT, finishedAtMillis = 5_000))

        assertEquals(SubagentRowIcon.DONE, row.icon)
        assertEquals(SubagentNow.Ended(SubagentUiStatus.STEP_LIMIT), row.now)
        assertFalse(row.needsLook)
    }

    @Test
    fun theNotesBoardIsShownAsAStepButNotCountedAgainstTheBudget() {
        val steps = listOf(
            step("a", "web_search", StepUiStatus.DONE),
            step("b", "notes", StepUiStatus.DONE),
            step("c", "notes", StepUiStatus.DONE),
            step("d", "web_fetch", StepUiStatus.DONE),
        )

        val used = subagent(steps = steps)

        assertEquals(4, used.steps.size)
        assertEquals(2, used.stepsUsed)
    }

    @Test
    fun theGroupCountsEveryEndedSubagentAsDone() {
        val group = listOf(
            subagent(status = SubagentUiStatus.DONE),
            subagent(status = SubagentUiStatus.COST_LIMIT),
            subagent(status = SubagentUiStatus.RUNNING),
        )

        assertEquals(GroupProgress(done = 2, total = 3), SubagentRows.progressOf(group))
        assertEquals(GroupProgress(done = 0, total = 0), SubagentRows.progressOf(emptyList()))
    }

    @Test
    fun theWorkSummaryNamesWhatWentWrongAndTheNotes() {
        val skipped = listOf(step("c1", "write_file", StepUiStatus.SKIPPED), step("c2", "share_file", StepUiStatus.SKIPPED))
        val group = listOf(
            subagent(status = SubagentUiStatus.DONE, notesPosted = 2),
            subagent(status = SubagentUiStatus.COST_LIMIT, notesPosted = 3),
            subagent(status = SubagentUiStatus.DONE, steps = skipped, notesPosted = 1),
            subagent(status = SubagentUiStatus.FAILED),
        )

        val summary = SubagentRows.workSummaryOf(group)

        assertEquals(listOf(SubagentUiStatus.COST_LIMIT to 1, SubagentUiStatus.FAILED to 1), summary.earlyStops)
        assertEquals(2, summary.skippedParts)
        assertEquals(6, summary.notes)
        assertFalse(summary.allDone)
    }

    @Test
    fun aCleanRunIsAllDone() {
        val summary = SubagentRows.workSummaryOf(listOf(subagent(status = SubagentUiStatus.DONE, notesPosted = 4)))

        assertTrue(summary.allDone)
        assertEquals(4, summary.notes)
    }

    @Test
    fun theStepMeterIsFullAtTheLimitAndNeverBeyond() {
        assertEquals(0.5f, SubagentRows.share(5.0, 10.0), 0.0001f)
        assertEquals(1f, SubagentRows.share(11.0, 10.0), 0.0001f)
        assertEquals(0f, SubagentRows.share(1.0, 0.0), 0.0001f)
    }
}
