package app.jonaki.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubagentEndingTest {
    private val limits = SubagentLimits()

    private fun failed(answer: String, failure: String?) =
        SubagentOutcome(SubagentStop.FAILED, answer, toolSteps = 17, costUsd = 0.02, failure = failure)

    @Test
    fun theFailureComesBackOutOfTheSavedResultText() {
        val failure = "Could not reach OpenRouter: Unable to resolve host \"openrouter.ai\""
        val saved = SubagentPrompt.resultText(failed("Found two shops.", failure), limits)

        assertEquals(failure, SubagentEnding.failureOf(saved))
    }

    @Test
    fun aFailureWithoutAnAnswerStillComesBack() {
        val saved = SubagentPrompt.resultText(failed("", "The connection to OpenRouter broke: timeout"), limits)

        assertEquals("The connection to OpenRouter broke: timeout", SubagentEnding.failureOf(saved))
    }

    @Test
    fun aRowSavedBeforeTheEndingExistedHasNoFailure() {
        assertNull(SubagentEnding.failureOf("Found two shops."))
        assertNull(SubagentEnding.failureOf(""))
        assertNull(SubagentEnding.failureOf(null))
    }

    @Test
    fun aStopThatIsNotAFailureHasNoFailure() {
        val timedOut = SubagentOutcome(SubagentStop.TIME_LIMIT, "Notes so far.", toolSteps = 3, costUsd = null)

        assertNull(SubagentEnding.failureOf(SubagentPrompt.resultText(timedOut, limits)))
    }

    @Test
    fun aVeryLongFailureIsCutAndMarked() {
        val saved = SubagentPrompt.resultText(failed("", "x".repeat(5_000)), limits)

        val failure = SubagentEnding.failureOf(saved)!!
        assertTrue(failure.length < 1_100)
        assertTrue(failure.endsWith("…"))
    }

    @Test
    fun theEndingSurvivesCappingALongAnswer() {
        val longAnswer = "line\n".repeat(10_000)
        val saved = SubagentPrompt.resultText(failed(longAnswer, "HTTP 503"), limits, limitAnswer = { answer -> answer.take(100) })

        assertEquals("HTTP 503", SubagentEnding.failureOf(saved))
        assertTrue(saved.length < 400)
    }
}
