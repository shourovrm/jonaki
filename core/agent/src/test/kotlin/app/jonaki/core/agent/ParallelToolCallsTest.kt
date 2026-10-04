package app.jonaki.core.agent

import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.model.ToolCall
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.SubagentModelInfo
import app.jonaki.core.toolapi.SubagentTask
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import java.nio.file.Files
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Read-only calls of one turn run side by side; every other call runs alone in its place (D-080). */
class ParallelToolCallsTest {
    private val toolContext = ToolContext(Files.createTempDirectory("thread").toFile(), OkHttpClient())
    private val recorder = InMemoryStepRecorder()
    private val history = listOf(Message(Role.USER, "Find something"))
    private val clock = VirtualClock()

    /** "start name" and "end name" lines, in the order they happened. */
    private val timeline = mutableListOf<String>()

    private fun loop(provider: ScriptedProvider, tools: List<Tool>, stepBudget: Int = 10) = AgentLoop(
        provider = provider,
        tools = tools,
        toolContext = toolContext,
        permissionBroker = PermissionBroker(FixedApprover(ApprovalDecision.ALLOW_ONCE)),
        recorder = recorder,
        settings = AgentSettings(model = "test-model", systemPrompt = "You are Jonaki.", stepBudget = stepBudget),
        waitTimer = clock,
    )

    /** A tool that notes its start, waits for [gate] and notes its end. */
    private fun gatedTool(
        name: String,
        gate: CompletableDeferred<Unit>,
        sideEffect: SideEffect = SideEffect.READ_ONLY,
    ) = FakeTool(name, sideEffect = sideEffect) { arguments ->
        val label = arguments["label"].toString().trim('"')
        timeline += "start $label"
        gate.await()
        timeline += "end $label"
        ToolOutput.success("result $label")
    }

    /** A tool that notes its start, pauses for [pause] of real time, and notes its end. */
    private fun pausingTool(name: String, sideEffect: SideEffect = SideEffect.READ_ONLY, pause: (String) -> Long = { 20 }) =
        FakeTool(name, sideEffect = sideEffect) { arguments ->
            val label = arguments["label"].toString().trim('"')
            timeline += "start $label"
            delay(pause(label))
            timeline += "end $label"
            ToolOutput.success("result $label")
        }

    private suspend fun waitUntil(condition: () -> Boolean) {
        withTimeout(2_000) {
            while (!condition()) {
                delay(5)
            }
        }
    }

    private fun startedCount(): Int = timeline.count { line -> line.startsWith("start") }

    @Test
    fun threeReadOnlyCallsRunAtTheSameTime() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val provider = ScriptedProvider(
            toolCallTurn(call("c1", "lookup", "label" to "a"), call("c2", "lookup", "label" to "b"), call("c3", "lookup", "label" to "c")),
            textTurn("Done."),
        )
        val run = launch { loop(provider, listOf(gatedTool("lookup", gate))).run(history) }

        // Run one after another, the first call would hold the others back until the gate opens.
        waitUntil { startedCount() == 3 }
        assertTrue(timeline.none { line -> line.startsWith("end") })
        gate.complete(Unit)
        run.join()

        val outcome = (recorder.events.last() as AgentEvent.RunFinished).outcome
        assertEquals(RunOutcome.Completed("Done."), outcome)
    }

    @Test
    fun aWriteBetweenReadsSplitsTheGroups() = runBlocking {
        val provider = ScriptedProvider(
            toolCallTurn(
                call("c1", "lookup", "label" to "r1"),
                call("c2", "lookup", "label" to "r2"),
                call("c3", "save", "label" to "w"),
                call("c4", "lookup", "label" to "r3"),
                call("c5", "lookup", "label" to "r4"),
            ),
            textTurn("Done."),
        )
        val tools = listOf(pausingTool("lookup"), pausingTool("save", SideEffect.CHANGES))

        loop(provider, tools).run(history)

        assertEquals(listOf("start r1", "start r2"), timeline.take(2))
        assertEquals(setOf("end r1", "end r2"), timeline.subList(2, 4).toSet())
        assertEquals(listOf("start w", "end w"), timeline.subList(4, 6))
        assertEquals(listOf("start r3", "start r4"), timeline.subList(6, 8))
    }

    @Test
    fun anUnknownToolOrBadArgumentsRunAlone() {
        val tools = mapOf("lookup" to FakeTool("lookup"), "save" to FakeTool("save", sideEffect = SideEffect.CHANGES))
        val calls = listOf(
            call("c1", "lookup"),
            call("c2", "lookup"),
            call("c3", "teleport"),
            call("c4", "lookup"),
            ToolCall("c5", "lookup", "{not json"),
            call("c6", "lookup"),
            call("c7", "save"),
            call("c8", "memory"),
        )

        val groups = ToolCallScheduler.groups(calls) { toolCall -> ToolCallScheduler.readsOnly(tools[toolCall.toolName], toolCall) }

        assertEquals(
            listOf(listOf("c1", "c2"), listOf("c3"), listOf("c4"), listOf("c5"), listOf("c6"), listOf("c7"), listOf("c8")),
            groups.map { group -> group.map { toolCall -> toolCall.id } },
        )
    }

    @Test
    fun delegateRunsAloneThoughItIsDeclaredReadOnly() {
        val delegate = FakeTool("delegate")

        assertTrue(!ToolCallScheduler.readsOnly(delegate, call("c1", "delegate")))
    }

    @Test
    fun resultsGoBackAndAreRecordedInCallOrder() = runBlocking {
        val provider = ScriptedProvider(
            toolCallTurn(call("c1", "lookup", "label" to "slow"), call("c2", "lookup", "label" to "fast")),
            textTurn("Done."),
        )
        val tool = pausingTool("lookup") { label -> if (label == "slow") 80 else 1 }

        loop(provider, listOf(tool)).run(history)

        // The fast call finished first, yet the slow call's result comes first everywhere.
        assertEquals(listOf("start slow", "start fast", "end fast", "end slow"), timeline)
        val sent = provider.requests[1].messages.filter { message -> message.role == Role.TOOL }
        assertEquals(listOf("c1", "c2"), sent.map { message -> message.toolCallId })
        assertEquals(listOf("result slow", "result fast"), sent.map { message -> message.text })
        val recorded = recorder.events.filterIsInstance<AgentEvent.ToolFinished>().map { event -> event.toolCall.id }
        assertEquals(listOf("c1", "c2"), recorded)
    }

    @Test
    fun webSearchesStartHalfASecondApartAndOverlap() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val startTimes = mutableMapOf<String, Duration>()
        val search = FakeTool("web_search") { arguments ->
            startTimes[arguments["label"].toString().trim('"')] = clock.now
            gate.await()
            ToolOutput.success("found")
        }
        val read = FakeTool("read_file") { arguments ->
            startTimes[arguments["label"].toString().trim('"')] = clock.now
            gate.await()
            ToolOutput.success("read")
        }
        val provider = ScriptedProvider(
            toolCallTurn(
                call("c1", "web_search", "label" to "s1"),
                call("c2", "web_search", "label" to "s2"),
                call("c3", "read_file", "label" to "r"),
                call("c4", "web_search", "label" to "s3"),
            ),
            textTurn("Done."),
        )
        val run = launch { loop(provider, listOf(search, read)).run(history) }

        waitUntil { startTimes.size == 2 && clock.pendingWaits == 1 }
        clock.advanceBy(500.milliseconds)
        waitUntil { startTimes.size == 3 && clock.pendingWaits == 1 }
        clock.advanceBy(500.milliseconds)
        waitUntil { startTimes.size == 4 }

        assertEquals(Duration.ZERO, startTimes["s1"])
        assertEquals(Duration.ZERO, startTimes["r"])
        assertEquals(500.milliseconds, startTimes["s2"])
        assertEquals(1_000.milliseconds, startTimes["s3"])
        gate.complete(Unit)
        run.join()
    }

    @Test
    fun atMostFourCallsRunAtOnce() = runBlocking {
        val gates = List(6) { CompletableDeferred<Unit>() }
        val tool = FakeTool("lookup") { arguments ->
            val index = arguments["label"].toString().trim('"').toInt()
            timeline += "start $index"
            gates[index].await()
            ToolOutput.success("ok")
        }
        val calls = (0 until 6).map { index -> call("c$index", "lookup", "label" to "$index") }
        val provider = ScriptedProvider(toolCallTurn(*calls.toTypedArray()), textTurn("Done."))
        val run = launch { loop(provider, listOf(tool)).run(history) }

        waitUntil { startedCount() == 4 }
        repeat(20) { yield() }
        assertEquals(4, startedCount())
        gates[1].complete(Unit)
        waitUntil { startedCount() == 5 }
        gates.forEach { gate -> gate.complete(Unit) }
        run.join()
    }

    @Test
    fun oneTurnWithSeveralCallsUsesOneStep() = runBlocking {
        val provider = ScriptedProvider(
            toolCallTurn(call("c1", "lookup", "label" to "a"), call("c2", "lookup", "label" to "b"), call("c3", "lookup", "label" to "c")),
            textTurn("All I found."),
        )

        val outcome = loop(provider, listOf(pausingTool("lookup") { 1 }), stepBudget = 1).run(history)

        assertEquals(RunOutcome.BudgetReached("All I found."), outcome)
        assertEquals(3, timeline.count { line -> line.startsWith("end") })
    }

    @Test
    fun stopDuringAParallelGroupStopsEveryCall() = runBlocking {
        var cancelledCalls = 0
        val tool = FakeTool("lookup") { arguments ->
            timeline += "start ${arguments["label"]}"
            try {
                awaitCancellation()
            } catch (cancellation: CancellationException) {
                cancelledCalls += 1
                throw cancellation
            }
        }
        val provider = ScriptedProvider(
            toolCallTurn(call("c1", "lookup", "label" to "a"), call("c2", "lookup", "label" to "b"), call("c3", "lookup", "label" to "c")),
        )
        val run = launch { loop(provider, listOf(tool)).run(history) }
        waitUntil { startedCount() == 3 }

        run.cancel()
        run.join()

        assertEquals(3, cancelledCalls)
        assertTrue(recorder.events.none { event -> event is AgentEvent.ToolFinished })
        val finishes = recorder.events.filterIsInstance<AgentEvent.RunFinished>()
        assertEquals(listOf(RunOutcome.Stopped("")), finishes.map { event -> event.outcome })
        assertEquals(3, recorder.events.count { event -> event is AgentEvent.ToolStarted })
    }

    @Test
    fun aSubagentRunsItsReadsSideBySideWithinItsStepLimit() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val readFile = gatedTool("read_file", gate)
        val provider = ScriptedProvider(
            toolCallTurn(
                call("c1", "read_file", "label" to "a"),
                call("c2", "read_file", "label" to "b"),
                call("c3", "read_file", "label" to "c"),
            ),
            textTurn("Read two."),
        )
        val runner = subagentRunner(provider, listOf(readFile), SubagentLimits(maxToolSteps = 2))
        val context = toolContext.forCall("delegate-1")
        val reports = async { runner.launch(listOf(SubagentTask("researcher", "Read")), context) }

        waitUntil { startedCount() == 2 }
        gate.complete(Unit)
        reports.await()

        assertEquals(2, readFile.receivedArguments.size)
        val results = provider.requests[1].messages.filter { message -> message.role == Role.TOOL }
        assertEquals(listOf("c1", "c2", "c3"), results.map { message -> message.toolCallId })
        assertTrue(results[2].text.startsWith("Not run"))
    }

    private fun subagentRunner(provider: ScriptedProvider, tools: List<Tool>, limits: SubagentLimits): SubagentRunner {
        val models = object : SubagentModels {
            override val scoped = emptyList<SubagentModelInfo>()

            override fun modelFor(agentType: AgentType, requestedKey: String?) = SubagentModel(
                key = "test:x", modelId = "x", provider = provider, thinkingLevel = null, acceptsImages = false,
                hasKnownPrice = false, priceOf = { null }, imageMessages = null,
            )
        }
        return SubagentRunner(
            threadTools = tools,
            broker = PermissionBroker(FixedApprover(ApprovalDecision.ALLOW_ONCE)),
            subagentModels = models,
            recorder = RecordingSubagents(),
            parentAsker = ParentAsker { _, _, _ -> ParentAnswer.Answered("") },
            memorySection = "",
            skillSection = "",
            now = { ZonedDateTime.of(2026, 10, 3, 9, 0, 0, 0, ZoneOffset.UTC) },
            limitsOverride = limits,
            timer = clock,
        )
    }
}
