package app.jonaki.core.agent

import app.jonaki.core.providerapi.FinishReason
import app.jonaki.core.providerapi.StreamEvent
import app.jonaki.core.providerapi.Usage
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSummarizerTest {

    @Test
    fun theSummaryIsTheModelsTextWithItsUsage() = runBlocking {
        val provider = ScriptedProvider(textTurn("## Goal\n", "Plan a trip"))

        val outcome = ConversationSummarizer(provider, "cheap-model").summarize(previousSummary = null, transcript = "User: plan a trip")

        val written = outcome as SummaryOutcome.Written
        assertEquals("## Goal\nPlan a trip", written.text)
        assertEquals(10, written.usage?.inputTokens)
        val request = provider.requests.single()
        assertEquals("cheap-model", request.model)
        assertTrue(request.tools.isEmpty())
        for (section in ConversationSummarizer.SECTIONS) {
            assertTrue("system prompt names $section", request.systemPrompt.contains(section))
        }
        assertTrue(request.messages.single().text.contains("User: plan a trip"))
    }

    @Test
    fun aPreviousSummaryIsFoldedIn() = runBlocking {
        val provider = ScriptedProvider(textTurn("merged"))

        ConversationSummarizer(provider, "m").summarize(previousSummary = "## Goal\nOld goal", transcript = "User: more")

        val sent = provider.requests.single().messages.single().text
        assertTrue(sent.contains("Old goal"))
        assertTrue(sent.indexOf("Old goal") < sent.indexOf("User: more"))
    }

    @Test
    fun aFailedCallIsReported() = runBlocking {
        val provider = ScriptedProvider(flowOf(StreamEvent.Failed("HTTP 503", retryable = true)))

        val outcome = ConversationSummarizer(provider, "m").summarize(previousSummary = null, transcript = "User: hi")

        assertEquals(SummaryOutcome.Failed("HTTP 503"), outcome)
    }

    @Test
    fun anEmptyOrCutOffSummaryIsAFailure() = runBlocking {
        val empty = ScriptedProvider(textTurn("  "))
        val cutOff = ScriptedProvider(
            flowOf(StreamEvent.TextDelta("## Goal\nhalf"), StreamEvent.Finished(FinishReason.LENGTH, Usage(10, 4000))),
        )

        assertTrue(ConversationSummarizer(empty, "m").summarize(null, "User: hi") is SummaryOutcome.Failed)
        assertTrue(ConversationSummarizer(cutOff, "m").summarize(null, "User: hi") is SummaryOutcome.Failed)
    }
}
