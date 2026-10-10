package app.jonaki.core.agent

import app.jonaki.core.providerapi.ChatProvider
import app.jonaki.core.providerapi.StreamEvent
import app.jonaki.core.toolapi.SubagentModelInfo
import app.jonaki.core.toolapi.SubagentTask
import app.jonaki.core.toolapi.ToolContext
import java.nio.file.Files
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A subagent whose model has been withdrawn retries once on the thread's model. */
class SubagentModelFallbackTest {
    private val context = ToolContext(Files.createTempDirectory("thread").toFile(), OkHttpClient()).forCall("delegate-1")
    private val recorder = RecordingSubagents()
    private val withdrawnAnswer = "OpenRouter answered HTTP 404: No endpoints found for stealth/space-bunny-alpha."

    private fun modelOf(key: String, provider: ChatProvider) = SubagentModel(
        key = key, modelId = key.substringAfter(':'), provider = provider, thinkingLevel = null, acceptsImages = false,
        hasKnownPrice = false, priceOf = { null }, imageMessages = null,
    )

    private fun runner(scoutProvider: ChatProvider, threadProvider: ChatProvider?): SubagentRunner {
        val models = object : SubagentModels {
            override val scoped = emptyList<SubagentModelInfo>()

            override fun modelFor(agentType: AgentType, requestedKey: String?) =
                modelOf("openrouter:stealth/space-bunny-alpha", scoutProvider)

            override fun threadModel() = threadProvider?.let { provider -> modelOf("openrouter:z-ai/glm-5.3-flash", provider) }
        }
        return SubagentRunner(
            threadTools = emptyList(),
            subagentModels = models,
            recorder = recorder,
            parentAsker = ParentAsker { _, _, _ -> ParentAnswer.Answered("") },
            memorySection = "",
            skillSection = "",
            now = { ZonedDateTime.of(2026, 10, 3, 9, 0, 0, 0, ZoneOffset.UTC) },
            timer = VirtualClock(),
        )
    }

    private fun failing(message: String) = ScriptedProvider(flowOf<StreamEvent>(StreamEvent.Failed(message, retryable = false)))

    @Test
    fun aWithdrawnModelFallsBackToTheThreadsModelAndSaysSo() = runBlocking {
        val threadProvider = ScriptedProvider(textTurn("Found it."))

        val reports = runner(failing(withdrawnAnswer), threadProvider).launch(listOf(SubagentTask("scout", "Find")), context)

        val text = reports.single().text
        val expectedLine = "The configured model openrouter:stealth/space-bunny-alpha was unavailable; " +
            "openrouter:z-ai/glm-5.3-flash answered instead."
        assertTrue(text, text.startsWith(expectedLine))
        assertTrue(text, text.contains("Found it."))
        assertEquals(SubagentStop.COMPLETED, recorder.outcomes.single().stop)
        assertEquals("z-ai/glm-5.3-flash", threadProvider.requests.single().model)
    }

    @Test
    fun otherErrorsDoNotFallBack() = runBlocking {
        val badKey = failing("OpenRouter answered HTTP 401: No auth credentials found")
        val threadProvider = ScriptedProvider(textTurn("Should not run."))

        val reports = runner(badKey, threadProvider).launch(listOf(SubagentTask("scout", "Find")), context)

        assertEquals(SubagentStop.FAILED, recorder.outcomes.single().stop)
        assertTrue(threadProvider.requests.isEmpty())
        assertFalse(reports.single().text.contains("answered instead"))
    }

    @Test
    fun whenTheFallbackIsUnavailableTooTheSubagentFailsAfterOneRetry() = runBlocking {
        val alsoWithdrawn = failing(withdrawnAnswer)

        val reports = runner(failing(withdrawnAnswer), alsoWithdrawn).launch(listOf(SubagentTask("scout", "Find")), context)

        assertEquals(SubagentStop.FAILED, recorder.outcomes.single().stop)
        assertEquals(1, alsoWithdrawn.requests.size)
        assertTrue(reports.single().text.contains("Stopped because the model call failed"))
    }

    @Test
    fun withoutAThreadModelAFailureStaysAFailureWithNextSteps() = runBlocking {
        val reports = runner(failing(withdrawnAnswer), threadProvider = null).launch(listOf(SubagentTask("scout", "Find")), context)

        val text = reports.single().text
        assertEquals(SubagentStop.FAILED, recorder.outcomes.single().stop)
        assertTrue(text, text.contains("is no longer available"))
        assertTrue(text, text.contains("\"model\""))
        assertEquals(withdrawnAnswer, SubagentEnding.failureOf(text))
    }

    @Test
    fun anOrdinaryFailureTellsTheParentWhatToTryNext() {
        val outcome = SubagentOutcome(SubagentStop.FAILED, "", 0, null, failure = "OpenRouter answered HTTP 401: bad key")

        val text = SubagentPrompt.resultText(outcome, SubagentLimits())

        assertTrue(text, text.contains("do the task yourself"))
        assertEquals("OpenRouter answered HTTP 401: bad key", SubagentEnding.failureOf(text))
    }
}
