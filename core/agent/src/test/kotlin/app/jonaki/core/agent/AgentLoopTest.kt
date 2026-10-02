package app.jonaki.core.agent

import app.jonaki.core.model.ImagePart
import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.providerapi.ChatProvider
import app.jonaki.core.providerapi.StreamEvent
import app.jonaki.core.toolapi.ImageSource
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import app.jonaki.core.toolapi.ViewedImages
import java.nio.file.Files
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLoopTest {
    private val toolContext = ToolContext(
        threadFolder = Files.createTempDirectory("thread").toFile(),
        httpClient = OkHttpClient(),
    )
    private val recorder = InMemoryStepRecorder()
    private val history = listOf(Message(Role.USER, "Find something"))

    private fun loop(
        provider: ChatProvider,
        tools: List<Tool>,
        stepBudget: Int = 10,
        approver: ApprovalRequester = FixedApprover(ApprovalDecision.ALLOW_ONCE),
    ) = AgentLoop(
        provider = provider,
        tools = tools,
        toolContext = toolContext,
        permissionBroker = PermissionBroker(approver),
        recorder = recorder,
        settings = AgentSettings(model = "test-model", systemPrompt = "You are Jonaki.", stepBudget = stepBudget),
    )

    @Test
    fun aViewedImageReachesTheNextRequestAndStaysThereUnchanged() = runBlocking {
        val viewTool = FakeTool(ViewedImages.TOOL_NAME) { ToolOutput.success(ViewedImages.resultText(ImageSource("work/a.png"))) }
        val provider = ScriptedProvider(
            toolCallTurn(call("call-1", ViewedImages.TOOL_NAME, "path" to "work/a.png")),
            toolCallTurn(call("call-2", "lookup")),
            textTurn("A bar chart."),
        )
        val imageLoop = AgentLoop(
            provider = provider,
            tools = listOf(viewTool, FakeTool("lookup")),
            toolContext = toolContext,
            permissionBroker = PermissionBroker(FixedApprover(ApprovalDecision.ALLOW_ONCE)),
            recorder = recorder,
            settings = AgentSettings(model = "test-model", systemPrompt = "You are Jonaki."),
            imageMessages = ImageMessages({ ImagePart("image/jpeg", "abc") }, modelAcceptsImages = true),
        )

        imageLoop.run(history)

        val second = provider.requests[1].messages
        assertEquals(Role.USER, second.last().role)
        assertEquals(listOf(ImagePart("image/jpeg", "abc")), second.last().images)
        // The third request starts with exactly the second one, so the prompt cache holds.
        assertEquals(second, provider.requests[2].messages.subList(0, second.size))
        val recordedTool = recorder.events.filterIsInstance<AgentEvent.ToolFinished>().first().message
        assertTrue(recordedTool.images.isEmpty())
    }

    private fun toolResults(): List<ToolOutput> =
        recorder.events.filterIsInstance<AgentEvent.ToolFinished>().map { event -> event.output }

    @Test
    fun toolCallThenFinalAnswer() = runBlocking {
        val fakeTool = FakeTool("lookup")
        val provider = ScriptedProvider(
            toolCallTurn(call("call-1", "lookup", "query" to "jonaki")),
            textTurn("Found ", "it."),
        )

        val outcome = loop(provider, listOf(fakeTool)).run(history)

        assertEquals(RunOutcome.Completed("Found it."), outcome)
        assertEquals("jonaki", fakeTool.receivedArguments.single()["query"].toString().trim('"'))

        val secondRequest = provider.requests[1]
        val sentMessages = secondRequest.messages
        assertEquals(3, sentMessages.size)
        assertEquals(Role.ASSISTANT, sentMessages[1].role)
        assertEquals("lookup", sentMessages[1].toolCalls.single().toolName)
        assertEquals(Role.TOOL, sentMessages[2].role)
        assertEquals("call-1", sentMessages[2].toolCallId)
        assertTrue(sentMessages[2].text.startsWith("lookup got"))
        assertEquals("lookup", secondRequest.tools.single().name)
    }

    @Test
    fun systemPromptIsIdenticalOnEveryTurn() = runBlocking {
        val provider = ScriptedProvider(
            toolCallTurn(call("call-1", "lookup")),
            toolCallTurn(call("call-2", "lookup")),
            textTurn("Done."),
        )
        loop(provider, listOf(FakeTool("lookup"))).run(history)

        val systemPrompts = provider.requests.map { request -> request.systemPrompt }.toSet()
        assertEquals(1, systemPrompts.size)
    }

    @Test
    fun everyStepIsRecordedInOrder() = runBlocking {
        val provider = ScriptedProvider(
            toolCallTurn(call("call-1", "lookup")),
            textTurn("Done."),
        )
        loop(provider, listOf(FakeTool("lookup"))).run(history)

        val kinds = recorder.events.map { event -> event::class.simpleName }
        assertEquals(
            listOf("AssistantMessage", "ToolStarted", "ToolFinished", "TextDelta", "AssistantMessage", "RunFinished"),
            kinds,
        )
    }

    @Test
    fun stepBudgetEndsWithOneAnswerWithoutTools() = runBlocking {
        val provider = ScriptedProvider(
            toolCallTurn(call("call-1", "lookup")),
            toolCallTurn(call("call-2", "lookup")),
            textTurn("Here is what I found so far."),
        )

        val outcome = loop(provider, listOf(FakeTool("lookup")), stepBudget = 2).run(history)

        assertEquals(RunOutcome.BudgetReached("Here is what I found so far."), outcome)
        val finalRequest = provider.requests[2]
        assertTrue(finalRequest.tools.isEmpty())
        assertTrue(finalRequest.messages.last().text.contains("step budget"))
    }

    @Test
    fun toolOverItsTimeLimitReturnsAnErrorToTheModel() = runBlocking {
        val slowTool = FakeTool("slow", timeLimit = 100.milliseconds) { _ ->
            delay(5_000)
            ToolOutput.success("never")
        }
        val provider = ScriptedProvider(toolCallTurn(call("call-1", "slow")), textTurn("Sorry."))

        val outcome = loop(provider, listOf(slowTool)).run(history)

        assertEquals(RunOutcome.Completed("Sorry."), outcome)
        val result = toolResults().single()
        assertTrue(result.isError)
        assertTrue(result.text, result.text.contains("time limit"))
    }

    @Test
    fun stopSavesThePartialAnswerAndRecordsStopped() = runBlocking {
        val provider = ScriptedProvider(hangingTurn("Half an ans"))
        val job = launch { loop(provider, emptyList()).run(history) }
        withTimeout(2_000) {
            while (recorder.events.none { event -> event is AgentEvent.TextDelta }) {
                delay(10)
            }
        }

        job.cancel()
        job.join()

        val saved = recorder.events.filterIsInstance<AgentEvent.AssistantMessage>().single()
        assertEquals("Half an ans", saved.message.text)
        assertEquals(RunOutcome.Stopped("Half an ans"), (recorder.events.last() as AgentEvent.RunFinished).outcome)
    }

    @Test
    fun providerFailureEndsTheRun() = runBlocking {
        val provider = ScriptedProvider(flowOf(StreamEvent.Failed("model overloaded", retryable = true)))

        val outcome = loop(provider, emptyList()).run(history)

        assertEquals(RunOutcome.ProviderFailed("model overloaded", retryable = true), outcome)
    }

    @Test
    fun streamEndingWithoutFinishIsAFailure() = runBlocking {
        val provider = ScriptedProvider(flowOf(StreamEvent.TextDelta("cut")))

        val outcome = loop(provider, emptyList()).run(history)

        assertTrue(outcome is RunOutcome.ProviderFailed)
    }

    @Test
    fun unknownToolNamesTheAvailableTools() = runBlocking {
        val provider = ScriptedProvider(toolCallTurn(call("call-1", "teleport")), textTurn("OK."))

        loop(provider, listOf(FakeTool("lookup"))).run(history)

        val result = toolResults().single()
        assertTrue(result.isError)
        assertTrue(result.text.contains("teleport"))
        assertTrue(result.text.contains("lookup"))
    }

    @Test
    fun toolErrorTextReachesTheModel() = runBlocking {
        val failingTool = FakeTool("lookup") { _ -> ToolOutput.error("index offline", "Try again later.") }
        val provider = ScriptedProvider(toolCallTurn(call("call-1", "lookup")), textTurn("OK."))

        loop(provider, listOf(failingTool)).run(history)

        val toolMessage = provider.requests[1].messages.last()
        assertEquals("Error: index offline. Try again later.", toolMessage.text)
    }

    @Test
    fun toolExceptionBecomesAnErrorText() = runBlocking {
        val crashingTool = FakeTool("lookup") { _ -> throw IllegalStateException("disk full") }
        val provider = ScriptedProvider(toolCallTurn(call("call-1", "lookup")), textTurn("OK."))

        loop(provider, listOf(crashingTool)).run(history)

        val result = toolResults().single()
        assertTrue(result.isError)
        assertTrue(result.text.contains("disk full"))
    }

    @Test
    fun invalidArgumentsJsonIsAnError() = runBlocking {
        val tool = FakeTool("lookup")
        val badCall = app.jonaki.core.model.ToolCall("call-1", "lookup", "{not json")
        val provider = ScriptedProvider(toolCallTurn(badCall), textTurn("OK."))

        loop(provider, listOf(tool)).run(history)

        assertTrue(tool.receivedArguments.isEmpty())
        assertTrue(toolResults().single().isError)
    }

    @Test
    fun deniedChangeIsNotRunAndTheModelIsTold() = runBlocking {
        val writer = FakeTool("write", sideEffect = SideEffect.CHANGES)
        val provider = ScriptedProvider(toolCallTurn(call("call-1", "write")), textTurn("OK."))

        loop(provider, listOf(writer), approver = FixedApprover(ApprovalDecision.DENY)).run(history)

        assertTrue(writer.receivedArguments.isEmpty())
        val result = toolResults().single()
        assertTrue(result.isError)
        assertTrue(result.text.contains("denied"))
        assertFalse(result.text.contains("write got"))
    }
}
