package app.jonaki.guard

import app.jonaki.core.guardapi.ActionVerdict
import app.jonaki.core.guardapi.Guard
import app.jonaki.core.guardapi.GuardCallContext
import app.jonaki.core.guardapi.GuardUsage
import app.jonaki.core.guardapi.NoGuard
import app.jonaki.core.guardapi.ResultVerdict
import app.jonaki.core.providerapi.Usage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class RecordingGuardTest {
    private class SavedNote(val toolCallId: String, val note: String)

    private class SavedUsage(val threadId: String, val modelKey: String, val usage: Usage, val costUsd: Double?)

    private val notes = mutableListOf<SavedNote>()
    private val usages = mutableListOf<SavedUsage>()

    private val recorder = GuardRecorder(
        threadId = "thread-1",
        appendNote = { toolCallId, note -> notes.add(SavedNote(toolCallId, note)) },
        saveUsage = { threadId, modelKey, usage, costUsd -> usages.add(SavedUsage(threadId, modelKey, usage, costUsd)) },
    )

    private class FixedGuard(
        private val action: ActionVerdict = ActionVerdict.ShowCard("unused"),
        private val result: ResultVerdict = ResultVerdict(false, null, "unused"),
    ) : Guard {
        override suspend fun judgeAction(userRequest: String, toolName: String, arguments: JsonObject) = action

        override suspend fun screenResult(source: String, text: String) = result
    }

    private fun duringCall(toolCallId: String, block: suspend () -> Unit) = runBlocking {
        withContext(GuardCallContext(toolCallId)) { block() }
    }

    @Test
    fun noGuardIsNotWrapped() {
        assertSame(NoGuard, RecordingGuard.around(NoGuard, recorder))
    }

    @Test
    fun theVerdictIsPassedOnUnchanged() {
        val verdict = ActionVerdict.MayRunWithoutCard("ok", 0.00003, note = "Jev: ran without a card (reversible 0.97, asked for 0.81)")
        val guard = RecordingGuard.around(FixedGuard(action = verdict), recorder)

        duringCall("call-1") { assertSame(verdict, guard.judgeAction("remind me", "phone", JsonObject(emptyMap()))) }
    }

    @Test
    fun anActionNoteIsSavedOnTheStepOfTheCall() {
        val verdict = ActionVerdict.ShowCard("no", 0.00003, note = "Jev: card shown (not sure the user asked, 0.41)")
        val guard = RecordingGuard.around(FixedGuard(action = verdict), recorder)

        duringCall("call-1") { guard.judgeAction("remind me", "phone", JsonObject(emptyMap())) }

        assertEquals(listOf("call-1"), notes.map { it.toolCallId })
        assertEquals(listOf("Jev: card shown (not sure the user asked, 0.41)"), notes.map { it.note })
    }

    @Test
    fun aResultNoteIsSavedOnTheStepOfTheCall() {
        val verdict = ResultVerdict(true, 0.83, "flagged", 0.0001, note = "Jev: result flagged (0.83)")
        val guard = RecordingGuard.around(FixedGuard(result = verdict), recorder)

        duringCall("call-2") { guard.screenResult("web_fetch example.com", "text") }

        assertEquals(listOf("Jev: result flagged (0.83)"), notes.map { it.note })
        assertEquals(listOf("call-2"), notes.map { it.toolCallId })
    }

    @Test
    fun aGuardCallSavesItsCostAndTokensUnderTheJevModelName() {
        val verdict = ActionVerdict.MayRunWithoutCard("ok", 2.7594e-05, GuardUsage(657, 73), "note")
        val guard = RecordingGuard.around(FixedGuard(action = verdict), recorder)

        duringCall("call-1") { guard.judgeAction("remind me", "phone", JsonObject(emptyMap())) }

        val saved = usages.single()
        assertEquals("thread-1", saved.threadId)
        assertEquals("openrouter:typesafe/jev-1.13", saved.modelKey)
        assertEquals(2.7594e-05, saved.costUsd!!, 1e-12)
        assertEquals(Usage(inputTokens = 657, outputTokens = 73), saved.usage)
    }

    @Test
    fun aResultCallSavesItsCostToo() {
        val verdict = ResultVerdict(false, 0.04, "clear", 0.0002, GuardUsage(2_000, 23), "Jev: result clear (0.04)")
        val guard = RecordingGuard.around(FixedGuard(result = verdict), recorder)

        duringCall("call-1") { guard.screenResult("web_fetch example.com", "text") }

        assertEquals(0.0002, usages.single().costUsd!!, 1e-12)
        assertEquals(2_000, usages.single().usage.inputTokens)
    }

    @Test
    fun aCallThatCostNothingSavesNoUsageRow() {
        val verdict = ActionVerdict.ShowCard("no answer", note = "Jev: no answer, card shown")
        val guard = RecordingGuard.around(FixedGuard(action = verdict), recorder)

        duringCall("call-1") { guard.judgeAction("remind me", "phone", JsonObject(emptyMap())) }

        assertEquals(emptyList<SavedUsage>(), usages)
        assertEquals(1, notes.size)
    }

    @Test
    fun withoutACallInTheContextTheCostIsStillSavedButNoNoteIs() = runBlocking {
        val verdict = ResultVerdict(false, 0.04, "clear", 0.0002, GuardUsage(2_000, 23), "Jev: result clear (0.04)")
        val guard = RecordingGuard.around(FixedGuard(result = verdict), recorder)

        guard.screenResult("web_fetch example.com", "text")

        assertEquals(1, usages.size)
        assertEquals(emptyList<SavedNote>(), notes)
    }

    @Test
    fun aVerdictWithoutANoteSavesNoNote() {
        val guard = RecordingGuard.around(FixedGuard(action = ActionVerdict.ShowCard("plain")), recorder)

        duringCall("call-1") { guard.judgeAction("remind me", "phone", JsonObject(emptyMap())) }

        assertNull(notes.firstOrNull())
    }
}
