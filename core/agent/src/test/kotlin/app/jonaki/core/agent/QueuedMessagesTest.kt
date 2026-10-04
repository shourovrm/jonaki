package app.jonaki.core.agent

import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.providerapi.ChatProvider
import app.jonaki.core.providerapi.StreamEvent
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import java.nio.file.Files
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Messages the user sends during a run (the queue): when the loop takes them, and when it must not. */
class QueuedMessagesTest {
    private val toolContext = ToolContext(
        threadFolder = Files.createTempDirectory("thread").toFile(),
        httpClient = OkHttpClient(),
    )
    private val recorder = InMemoryStepRecorder()
    private val history = listOf(Message(Role.USER, "Find something"))

    /** Hands out one batch of queued messages per call, like the runner's queue; [calls] counts the loop's takes. */
    private class FakeQueue(vararg batches: List<Message>) {
        private val remaining = ArrayDeque(batches.toList())
        var calls = 0

        suspend fun take(): List<Message> {
            calls += 1
            return remaining.removeFirstOrNull().orEmpty()
        }
    }

    private fun loop(provider: ChatProvider, tools: List<Tool>, queue: FakeQueue, stepBudget: Int = 10) = AgentLoop(
        provider = provider,
        tools = tools,
        toolContext = toolContext,
        permissionBroker = PermissionBroker(FixedApprover(ApprovalDecision.ALLOW_ONCE)),
        recorder = recorder,
        settings = AgentSettings(model = "test-model", systemPrompt = "You are Jonaki.", stepBudget = stepBudget),
        takeQueuedMessages = queue::take,
    )

    private fun queued(text: String) = Message(Role.USER, text)

    @Test
    fun aQueuedMessageArrivesAfterTheToolResultsAndBeforeTheNextRequest() = runBlocking {
        val provider = ScriptedProvider(
            toolCallTurn(call("call-1", "lookup"), call("call-2", "lookup")),
            textTurn("Done with both."),
        )
        val queue = FakeQueue(listOf(queued("Also check B"), queued("And C")))

        val outcome = loop(provider, listOf(FakeTool("lookup")), queue).run(history)

        assertEquals(RunOutcome.Completed("Done with both."), outcome)
        val secondRequest = provider.requests[1].messages
        assertEquals(
            listOf(Role.USER, Role.ASSISTANT, Role.TOOL, Role.TOOL, Role.USER, Role.USER),
            secondRequest.map { message -> message.role },
        )
        assertEquals(listOf("Also check B", "And C"), secondRequest.takeLast(2).map { message -> message.text })
    }

    @Test
    fun theSystemPromptStaysTheSameWhenMessagesAreQueued() = runBlocking {
        val provider = ScriptedProvider(toolCallTurn(call("call-1", "lookup")), textTurn("Done."))
        val queue = FakeQueue(listOf(queued("More")))

        loop(provider, listOf(FakeTool("lookup")), queue).run(history)

        assertEquals(1, provider.requests.map { request -> request.systemPrompt }.toSet().size)
        // The first request's messages are the start of the second one, so the prompt cache holds.
        val first = provider.requests[0].messages
        assertEquals(first, provider.requests[1].messages.subList(0, first.size))
    }

    @Test
    fun aMessageQueuedWhenTheModelAnswersInTextContinuesTheRun() = runBlocking {
        val provider = ScriptedProvider(textTurn("First answer."), textTurn("Second answer."))
        val queue = FakeQueue(listOf(queued("One more thing")))

        val outcome = loop(provider, emptyList(), queue).run(history)

        assertEquals(RunOutcome.Completed("Second answer."), outcome)
        assertEquals(2, provider.requests.size)
        val secondRequest = provider.requests[1].messages
        assertEquals(listOf(Role.USER, Role.ASSISTANT, Role.USER), secondRequest.map { message -> message.role })
        assertEquals("One more thing", secondRequest.last().text)
        // Only the last answer ends the run.
        assertEquals(1, recorder.events.count { event -> event is AgentEvent.RunFinished })
    }

    @Test
    fun theStepBudgetStartsFreshFromADeliveredMessage() = runBlocking {
        val provider = ScriptedProvider(
            toolCallTurn(call("call-1", "lookup")),
            toolCallTurn(call("call-2", "lookup")),
            toolCallTurn(call("call-3", "lookup")),
            textTurn("Done."),
        )
        // The budget is 3 tool turns. Without the restart the third tool turn would use it up before the fourth request.
        val queue = FakeQueue(listOf(queued("Change of plan")))

        val outcome = loop(provider, listOf(FakeTool("lookup")), queue, stepBudget = 3).run(history)

        assertEquals(RunOutcome.Completed("Done."), outcome)
        assertTrue(provider.requests.none { request -> request.messages.any { message -> message.text.contains("step budget") } })
        assertTrue(provider.requests.all { request -> request.tools.isNotEmpty() })
    }

    @Test
    fun nothingIsTakenAfterStop() = runBlocking {
        val provider = ScriptedProvider(hangingTurn("Half an ans"))
        val queue = FakeQueue(listOf(queued("Never delivered")))
        val job = launch { loop(provider, emptyList(), queue).run(history) }
        withTimeout(2_000) {
            while (recorder.events.none { event -> event is AgentEvent.TextDelta }) {
                delay(10)
            }
        }

        job.cancel()
        job.join()

        assertEquals(0, queue.calls)
        assertEquals(1, provider.requests.size)
    }

    @Test
    fun nothingIsTakenAfterAProviderFailure() = runBlocking {
        val provider = ScriptedProvider(flowOf(StreamEvent.Failed("overloaded", retryable = false)))
        val queue = FakeQueue(listOf(queued("Stays queued")))

        val outcome = loop(provider, emptyList(), queue).run(history)

        assertTrue(outcome is RunOutcome.ProviderFailed)
        assertEquals(0, queue.calls)
    }
}
