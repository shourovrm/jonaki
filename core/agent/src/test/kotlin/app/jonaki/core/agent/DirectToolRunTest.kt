package app.jonaki.core.agent

import app.jonaki.core.model.Role
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import java.nio.file.Files
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A tool call the user asked for directly: the same events as an agent-made call, and no model turn. */
class DirectToolRunTest {
    private val toolContext = ToolContext(
        threadFolder = Files.createTempDirectory("thread").toFile(),
        httpClient = OkHttpClient(),
    )
    private val recorder = InMemoryStepRecorder()
    private val arguments = buildJsonObject { put("prompt", "a blue door") }

    @Test
    fun recordsTheEventsInTheOrderOfAnAgentMadeCall() = runBlocking {
        val tool = FakeTool("generate_image", behaviour = { ToolOutput.success("Image saved: images/a.png") })

        val outcome = DirectToolRun(recorder).run(tool, arguments, toolContext, callId = "call-1")

        assertEquals(RunOutcome.Completed(""), outcome)
        val events = recorder.events
        assertEquals(
            listOf("AssistantMessage", "ToolStarted", "ToolFinished", "RunFinished"),
            events.map { event -> event::class.simpleName },
        )
        val assistantEvent = events[0] as AgentEvent.AssistantMessage
        assertEquals(Role.ASSISTANT, assistantEvent.message.role)
        assertEquals("", assistantEvent.message.text)
        assertNull(assistantEvent.usage)
        val savedCall = assistantEvent.message.toolCalls.single()
        assertEquals("call-1", savedCall.id)
        assertEquals("generate_image", savedCall.toolName)
        assertEquals("""{"prompt":"a blue door"}""", savedCall.argumentsJson)
        val finished = events[2] as AgentEvent.ToolFinished
        assertEquals("Image saved: images/a.png", finished.output.text)
        assertEquals(Role.TOOL, finished.message.role)
        assertEquals("call-1", finished.message.toolCallId)
        assertEquals(arguments, tool.receivedArguments.single())
    }

    @Test
    fun aFailingToolIsRecordedAsAnErrorResultNotAnException() = runBlocking {
        val tool = FakeTool("generate_image", behaviour = { ToolOutput.error("the service refused it", "Tell the user.") })

        DirectToolRun(recorder).run(tool, arguments, toolContext, callId = "call-1")

        val finished = recorder.events.filterIsInstance<AgentEvent.ToolFinished>().single()
        assertTrue(finished.output.isError)
        assertEquals("Error: the service refused it. Tell the user.", finished.message.text)
    }

    @Test
    fun theToolsTimeLimitApplies() = runBlocking {
        val tool = FakeTool(
            "generate_image",
            timeLimit = 50.milliseconds,
            behaviour = {
                delay(10_000)
                ToolOutput.success("never")
            },
        )

        DirectToolRun(recorder).run(tool, arguments, toolContext, callId = "call-1")

        val finished = recorder.events.filterIsInstance<AgentEvent.ToolFinished>().single()
        assertTrue(finished.output.isError)
        assertTrue(finished.output.text.contains("time limit"))
    }

    @Test
    fun stopRecordsAStoppedRunAndNoToolResult() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val tool = FakeTool(
            "generate_image",
            behaviour = {
                started.complete(Unit)
                delay(60_000)
                ToolOutput.success("never")
            },
        )

        val job = launch { DirectToolRun(recorder).run(tool, arguments, toolContext, callId = "call-1") }
        started.await()
        job.cancel(CancellationException("Stop"))
        job.join()

        val events = recorder.events
        assertEquals(listOf("AssistantMessage", "ToolStarted", "RunFinished"), events.map { event -> event::class.simpleName })
        assertEquals(RunOutcome.Stopped(""), (events.last() as AgentEvent.RunFinished).outcome)
    }

    private fun overTwoCalls(): FakeTool {
        var calls = 0
        return FakeTool(
            "generate_video",
            behaviour = {
                calls++
                if (calls == 1) ToolOutput.success("still being made, job-1") else ToolOutput.success("Video saved: videos/a.mp4")
            },
        )
    }

    private val collectJob1 = buildJsonObject { put("job_id", "job-1") }

    @Test
    fun aFollowUpCallIsRecordedAsItsOwnAssistantCallStepAndResultWithOneRunEnd() = runBlocking {
        val tool = overTwoCalls()
        val ids = mutableListOf("call-2")

        val outcome = DirectToolRun(recorder).run(
            tool, arguments, toolContext, callId = "call-1",
            nextCall = { output -> if (output.text.startsWith("still")) collectJob1 else null },
            maxCalls = 3,
            newCallId = { ids.removeAt(0) },
        )

        assertEquals(RunOutcome.Completed(""), outcome)
        assertEquals(
            listOf("AssistantMessage", "ToolStarted", "ToolFinished", "AssistantMessage", "ToolStarted", "ToolFinished", "RunFinished"),
            recorder.events.map { event -> event::class.simpleName },
        )
        val calls = recorder.events.filterIsInstance<AgentEvent.AssistantMessage>().map { event -> event.message.toolCalls.single() }
        assertEquals(listOf("call-1", "call-2"), calls.map { call -> call.id })
        assertEquals("""{"job_id":"job-1"}""", calls[1].argumentsJson)
        val results = recorder.events.filterIsInstance<AgentEvent.ToolFinished>().map { event -> event.message }
        assertEquals(listOf("call-1", "call-2"), results.map { message -> message.toolCallId })
        assertEquals(listOf(arguments, collectJob1), tool.receivedArguments)
    }

    @Test
    fun noFollowUpMeansOneCall() = runBlocking {
        val tool = overTwoCalls()

        DirectToolRun(recorder).run(tool, arguments, toolContext, callId = "call-1", nextCall = { null }, maxCalls = 3)

        assertEquals(1, tool.receivedArguments.size)
    }

    @Test
    fun theNumberOfCallsIsCappedAtMaxCalls() = runBlocking {
        val tool = FakeTool("generate_video", behaviour = { ToolOutput.success("still being made") })
        var ids = 1

        DirectToolRun(recorder).run(
            tool, arguments, toolContext, callId = "call-1",
            nextCall = { collectJob1 },
            maxCalls = 3,
            newCallId = { "call-${++ids}" },
        )

        assertEquals(3, tool.receivedArguments.size)
        assertEquals(3, recorder.events.count { event -> event is AgentEvent.ToolFinished })
        assertEquals(1, recorder.events.count { event -> event is AgentEvent.RunFinished })
    }

    @Test
    fun stopDuringAFollowUpCallKeepsTheFirstResultAndEndsStopped() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var calls = 0
        val tool = FakeTool(
            "generate_video",
            behaviour = {
                calls++
                if (calls == 1) {
                    ToolOutput.success("still being made")
                } else {
                    started.complete(Unit)
                    delay(60_000)
                    ToolOutput.success("never")
                }
            },
        )

        val job = launch {
            DirectToolRun(recorder).run(tool, arguments, toolContext, callId = "call-1", nextCall = { collectJob1 }, maxCalls = 3)
        }
        started.await()
        job.cancel(CancellationException("Stop"))
        job.join()

        assertEquals(
            listOf("AssistantMessage", "ToolStarted", "ToolFinished", "AssistantMessage", "ToolStarted", "RunFinished"),
            recorder.events.map { event -> event::class.simpleName },
        )
        assertEquals(RunOutcome.Stopped(""), (recorder.events.last() as AgentEvent.RunFinished).outcome)
    }
}
