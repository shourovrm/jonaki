package app.jonaki.core.agent

import app.jonaki.core.model.ToolCall
import app.jonaki.core.providerapi.ChatProvider
import app.jonaki.core.providerapi.FinishReason
import app.jonaki.core.providerapi.StreamEvent
import app.jonaki.core.providerapi.Usage
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.SubagentModelInfo
import app.jonaki.core.toolapi.SubagentTask
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import java.nio.file.Files
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubagentRunnerTest {
    private val threadFolder = Files.createTempDirectory("thread").toFile()
    private val context = ToolContext(threadFolder, OkHttpClient()).forCall("delegate-1")
    private val clock = VirtualClock()
    private val recorder = RecordingSubagents()

    private val webSearch = FakeTool("web_search")
    private val readFile = FakeTool("read_file")
    private val writeFile = FakeTool("write_file", sideEffect = SideEffect.CHANGES_THREAD_FOLDER)
    private val shareFile = FakeTool("share_file", sideEffect = SideEffect.CHANGES)
    private val memory = FakeTool("memory", sideEffect = SideEffect.CHANGES_APP_DATA)
    private val viewImage = FakeTool("view_image")
    private val threadTools = listOf(webSearch, readFile, writeFile, shareFile, memory, viewImage)

    private fun runner(
        providers: Map<String, ChatProvider>,
        approver: ApprovalRequester = FixedApprover(ApprovalDecision.ALLOW_ONCE),
        limits: SubagentLimits = SubagentLimits(),
        acceptsImages: Boolean = false,
        pricePerCall: Double? = 0.01,
        asker: ParentAsker = ParentAsker { question, _, _ -> ParentAnswer.Answered("Answer to $question") },
        tools: List<app.jonaki.core.toolapi.Tool> = threadTools,
    ): SubagentRunner {
        var nextId = 0
        val models = object : SubagentModels {
            override val scoped = listOf(SubagentModelInfo("test:model", "Test model"))

            override fun modelFor(agentType: AgentType, requestedKey: String?): SubagentModel? {
                val provider = providers[agentType.name] ?: return null
                return SubagentModel(
                    key = requestedKey ?: "test:${agentType.name}",
                    modelId = agentType.name,
                    provider = provider,
                    thinkingLevel = null,
                    acceptsImages = acceptsImages,
                    hasKnownPrice = pricePerCall != null,
                    priceOf = { pricePerCall },
                    imageMessages = null,
                )
            }
        }
        return SubagentRunner(
            threadTools = tools,
            broker = PermissionBroker(approver),
            subagentModels = models,
            recorder = recorder,
            parentAsker = asker,
            memorySection = "Memory:\n- [1] The user lives in Dhaka.",
            skillSection = "Skills:\n- report: writes reports (/skills/report/SKILL.md)",
            now = { ZonedDateTime.of(2026, 10, 3, 9, 0, 0, 0, ZoneOffset.UTC) },
            limits = limits,
            timer = clock,
            newId = { "s${nextId++}" },
        )
    }

    private fun usageTurn(vararg calls: ToolCall): Flow<StreamEvent> = flowOf(
        *calls.map { call -> StreamEvent.ToolCallReady(call) }.toTypedArray(),
        StreamEvent.Finished(FinishReason.TOOL_CALLS, Usage(inputTokens = 100, outputTokens = 10)),
    )

    @Test
    fun aSubagentGetsOnlyItsTypesToolsAndNeverDelegateOrMemory() = runBlocking {
        val provider = ScriptedProvider(textTurn("Found it."))
        runner(mapOf("researcher" to provider)).launch(listOf(SubagentTask("researcher", "Find rain data")), context)

        val request = provider.requests.single()
        val names = request.tools.map { it.name }
        assertEquals(listOf("ask_parent", "read_file", "request_tool", "web_search"), names)
        assertFalse(request.systemPrompt.contains("Skills:"))
        assertTrue(request.systemPrompt.contains("The user lives in Dhaka."))
        assertTrue(request.messages.single().text.endsWith("Find rain data"))
    }

    @Test
    fun theWorkerGetsEveryThreadToolExceptMemoryAndSeesSkills() = runBlocking {
        val provider = ScriptedProvider(textTurn("Done."))
        runner(mapOf("worker" to provider)).launch(listOf(SubagentTask("worker", "Tidy work/")), context)

        val request = provider.requests.single()
        assertEquals(
            listOf("ask_parent", "read_file", "request_tool", "share_file", "web_search", "write_file"),
            request.tools.map { it.name },
        )
        assertTrue(request.systemPrompt.contains("Skills:"))
    }

    @Test
    fun viewImageFollowsTheSubagentsModel() = runBlocking {
        val provider = ScriptedProvider(textTurn("A cat."))
        runner(mapOf("researcher" to provider), acceptsImages = true)
            .launch(listOf(SubagentTask("researcher", "Describe inbox/cat.jpg")), context)

        assertTrue(provider.requests.single().tools.any { it.name == "view_image" })
    }

    @Test
    fun theSystemPromptIsTheSameForEveryRequestOfASubagent() = runBlocking {
        val provider = ScriptedProvider(
            usageTurn(call("c1", "request_tool", "name" to "write_file", "reason" to "save")),
            usageTurn(call("c2", "write_file", "path" to "work/a.md")),
            textTurn("Wrote work/a.md."),
        )
        runner(mapOf("researcher" to provider), approver = FixedApprover(ApprovalDecision.ALLOW_FOR_TASK))
            .launch(listOf(SubagentTask("researcher", "Write it down")), context)

        assertEquals(1, provider.requests.map { it.systemPrompt }.distinct().size)
        // The granted tool joins the tool list from the next request on.
        assertFalse(provider.requests[0].tools.any { it.name == "write_file" })
        assertTrue(provider.requests[1].tools.any { it.name == "write_file" })
        assertEquals(1, writeFile.receivedArguments.size)
    }

    @Test
    fun anUnansweredToolRequestIsSkippedAfterThreeMinutesAndListed() = runBlocking {
        val approver = WaitingApprover()
        val provider = ScriptedProvider(
            usageTurn(call("c1", "request_tool", "name" to "share_file", "reason" to "save the report to Downloads")),
            textTurn("The report is in work/report.md; saving it was skipped."),
        )
        val reports = async {
            runner(mapOf("writer" to provider), approver = approver)
                .launch(listOf(SubagentTask("writer", "Write and save a report")), context)
        }
        repeat(50) { yield() }
        assertEquals("share_file", approver.requests.single().toolName)
        assertEquals("save the report to Downloads", approver.requests.single().subagent?.reason)

        clock.advanceBy(3.minutes)
        val text = reports.await().single().text

        assertTrue(text.startsWith("The report is in work/report.md"))
        assertTrue(text.contains("Skipped"))
        assertTrue(text.contains("request_tool share_file: save the report to Downloads"))
        assertEquals(SubagentStepStatus.SKIPPED, recorder.finishedSteps.single().second)
        assertEquals(listOf("request_tool share_file: save the report to Downloads"), recorder.outcomes.single().skipped)
        // The model was told, so it could go on with the other parts.
        assertTrue(provider.requests[1].messages.last().text.contains("3 minutes"))
    }

    @Test
    fun anUnansweredCallIsSkippedAndTheSubagentGoesOn() = runBlocking {
        val approver = WaitingApprover()
        val provider = ScriptedProvider(
            usageTurn(call("c1", "write_file", "path" to "work/a.md"), call("c2", "read_file", "path" to "inbox/b.md")),
            textTurn("Read b; writing a was skipped."),
        )
        val reports = async {
            runner(mapOf("worker" to provider), approver = approver).launch(listOf(SubagentTask("worker", "Copy b to a")), context)
        }
        repeat(50) { yield() }
        clock.advanceBy(3.minutes)
        reports.await()

        assertTrue(writeFile.receivedArguments.isEmpty())
        assertEquals(1, readFile.receivedArguments.size)
        assertEquals(listOf("write_file: work/a.md"), recorder.outcomes.single().skipped)
    }

    @Test
    fun theStepLimitStopsToolsAndAsksForAnAnswer() = runBlocking {
        val provider = ScriptedProvider(
            usageTurn(
                call("c1", "read_file", "path" to "a"),
                call("c2", "read_file", "path" to "b"),
                call("c3", "read_file", "path" to "c"),
            ),
            textTurn("Partial answer."),
        )
        val text = runner(mapOf("researcher" to provider), limits = SubagentLimits(maxToolSteps = 2))
            .launch(listOf(SubagentTask("researcher", "Search")), context).single().text

        assertEquals(2, readFile.receivedArguments.size)
        val secondRequest = provider.requests[1]
        assertTrue(secondRequest.tools.isEmpty())
        // The third call still gets a result, as every provider requires.
        assertTrue(secondRequest.messages.any { it.toolCallId == "c3" && it.text.startsWith("Not run") })
        assertTrue(text.startsWith("Partial answer."))
        assertTrue(text.contains("step limit of 2"))
        assertEquals(SubagentStop.STEP_LIMIT, recorder.outcomes.single().stop)
    }

    @Test
    fun theCostCapStopsTheSubagentWithWhatItHas() = runBlocking {
        val provider = ScriptedProvider(
            usageTurn(call("c1", "web_search", "query" to "a")),
            flowOf(
                StreamEvent.TextDelta("Looking further."),
                StreamEvent.ToolCallReady(call("c2", "web_search", "query" to "b")),
                StreamEvent.Finished(FinishReason.TOOL_CALLS, Usage(100, 10)),
            ),
        )
        val text = runner(mapOf("researcher" to provider), pricePerCall = 0.06)
            .launch(listOf(SubagentTask("researcher", "Search")), context).single().text

        assertEquals(1, webSearch.receivedArguments.size)
        assertEquals(2, provider.requests.size)
        assertTrue(text.contains("Looking further."))
        assertTrue(text.contains("web_search got"))
        assertTrue(text.contains("cost limit of $0.10"))
        assertEquals(0.12, recorder.outcomes.single().costUsd!!, 0.0001)
        assertEquals(2, recorder.modelCalls)
    }

    @Test
    fun aModelWithoutAPriceHasOnlyTheStepLimit() = runBlocking {
        val provider = ScriptedProvider(
            usageTurn(call("c1", "web_search", "query" to "a")),
            usageTurn(call("c2", "web_search", "query" to "b")),
            textTurn("Done."),
        )
        runner(mapOf("researcher" to provider), pricePerCall = null).launch(listOf(SubagentTask("researcher", "Search")), context)

        assertEquals(SubagentStop.COMPLETED, recorder.outcomes.single().stop)
        assertFalse(provider.requests.first().systemPrompt.contains("$0.10"))
    }

    @Test
    fun theTimeLimitReturnsWhatItHas() = runBlocking {
        val slowTool = FakeTool("web_search") { delay(Long.MAX_VALUE); ToolOutput.success("never") }
        val provider = ScriptedProvider(
            flowOf(
                StreamEvent.TextDelta("Started on it."),
                StreamEvent.ToolCallReady(call("c1", "web_search", "query" to "a")),
                StreamEvent.Finished(FinishReason.TOOL_CALLS, null),
            ),
        )
        val slowRunner = SubagentRunner(
            threadTools = listOf(slowTool),
            broker = PermissionBroker(FixedApprover(ApprovalDecision.ALLOW_ONCE)),
            subagentModels = singleModel(provider),
            recorder = recorder,
            parentAsker = ParentAsker { _, _, _ -> ParentAnswer.Failed("none") },
            memorySection = "",
            skillSection = "",
            now = { ZonedDateTime.now(ZoneOffset.UTC) },
            limits = SubagentLimits(timeLimit = 200.milliseconds),
        )
        val text = slowRunner.launch(listOf(SubagentTask("researcher", "Search")), context).single().text

        assertTrue(text.contains("Started on it."))
        assertTrue(text.contains("time limit"))
        assertEquals(SubagentStop.TIME_LIMIT, recorder.outcomes.single().stop)
    }

    @Test
    fun askParentIsAnsweredTwiceThenRefused() = runBlocking {
        val provider = ScriptedProvider(
            usageTurn(call("c1", "ask_parent", "question" to "Which year?")),
            usageTurn(call("c2", "ask_parent", "question" to "Which city?")),
            usageTurn(call("c3", "ask_parent", "question" to "Which month?")),
            textTurn("Done."),
        )
        runner(mapOf("scout" to provider)).launch(listOf(SubagentTask("scout", "Find")), context)

        val toolResults = provider.requests[3].messages.filter { it.role == app.jonaki.core.model.Role.TOOL }.map { it.text }
        assertEquals("Answer to Which year?", toolResults[0])
        assertEquals("Answer to Which city?", toolResults[1])
        assertTrue(toolResults[2].startsWith("Error: you have asked 2 questions already"))
    }

    @Test
    fun parallelSubagentsShareOneNotesBoard() = runBlocking {
        val first = ScriptedProvider(
            usageTurn(call("c1", "notes", "action" to "post", "text" to "Review site: https://example.com")),
            textTurn("First done."),
        )
        val second = ScriptedProvider(
            usageTurn(call("c1", "notes", "action" to "read")),
            textTurn("Second done."),
        )
        val reports = runner(mapOf("researcher" to first, "scout" to second)).launch(
            listOf(SubagentTask("researcher", "Laptop A"), SubagentTask("scout", "Laptop B")),
            context,
        )

        assertEquals(listOf("researcher 1", "scout 2"), reports.map { it.label })
        val folder = java.io.File(threadFolder, "work/delegations").listFiles()!!.single()
        val notes = java.io.File(folder, "notes.md").readText()
        assertTrue(notes.contains("**researcher 1**: Review site: https://example.com"))
        // The chat finds the same board from the delegate call's id alone.
        assertEquals(java.io.File(folder, "notes.md"), java.io.File(threadFolder, SubagentRunner.notesBoardPath("delegate-1")))
        assertTrue(first.requests.first().tools.any { it.name == "notes" })
        assertEquals(listOf("delegate-1", "delegate-1"), recorder.starts.map { it.parentToolCallId })
    }

    @Test
    fun oneSubagentAloneHasNoNotesBoard() = runBlocking {
        val provider = ScriptedProvider(textTurn("Done."))
        runner(mapOf("scout" to provider)).launch(listOf(SubagentTask("scout", "Find")), context)

        assertFalse(provider.requests.single().tools.any { it.name == "notes" })
    }

    @Test
    fun stepsAreSavedUnderIdsThatCannotClashWithTheThreadsOwn() = runBlocking {
        val provider = ScriptedProvider(usageTurn(call("c1", "read_file", "path" to "a.md")), textTurn("Done."))
        runner(mapOf("scout" to provider)).launch(listOf(SubagentTask("scout", "Read")), context)

        assertEquals(listOf("s0/c1"), recorder.finishedSteps.map { it.first.id })
        // The model still sees its own ids.
        assertEquals("c1", provider.requests[1].messages.last().toolCallId)
    }

    @Test
    fun aMissingModelKeyIsReportedWithoutRunning() = runBlocking {
        val reports = runner(emptyMap()).launch(listOf(SubagentTask("scout", "Find")), context)

        assertTrue(reports.single().text.startsWith("Error:"))
        assertTrue(recorder.starts.isEmpty())
    }

    private fun singleModel(provider: ChatProvider) = object : SubagentModels {
        override val scoped = emptyList<SubagentModelInfo>()

        override fun modelFor(agentType: AgentType, requestedKey: String?) = SubagentModel(
            key = "test:x", modelId = "x", provider = provider, thinkingLevel = null, acceptsImages = false,
            hasKnownPrice = false, priceOf = { null }, imageMessages = null,
        )
    }

    @Test
    fun aLongCallIdStillGivesAShortFolder() = runBlocking {
        // Gemini 3 carries its whole thought signature in the call id.
        val longContext = ToolContext(threadFolder, OkHttpClient()).forCall("call_1~" + "A".repeat(600))
        val first = ScriptedProvider(usageTurn(call("c1", "notes", "action" to "post", "text" to "x")), textTurn("a"))
        val second = ScriptedProvider(textTurn("b".repeat(20_000)))

        val reports = runner(mapOf("researcher" to first, "scout" to second)).launch(
            listOf(SubagentTask("researcher", "A"), SubagentTask("scout", "B")),
            longContext,
        )

        val folders = java.io.File(threadFolder, "work/delegations").listFiles()!!.map { it.name }
        assertEquals(1, folders.size)
        assertTrue(folders.single().length <= 16)
        assertTrue(reports[1].text.contains("work/delegations/${folders.single()}/scout-2.md"))
        assertEquals("call_1~" + "A".repeat(600), recorder.starts.first().parentToolCallId)
    }

    @Test
    fun aBlankCallIdIsTreatedAsNone() = runBlocking {
        val provider = ScriptedProvider(textTurn("c".repeat(20_000)))
        val reports = runner(mapOf("scout" to provider)).launch(
            listOf(SubagentTask("scout", "Find")),
            ToolContext(threadFolder, OkHttpClient()).forCall(""),
        )

        assertFalse(reports.single().text.contains("work/delegations//"))
    }

    @Test
    fun aLongAnswerIsCutWhenItCannotBeSaved() = runBlocking {
        java.io.File(threadFolder, "work").mkdirs()
        // A file where the folder should be, so saving the answer fails.
        java.io.File(threadFolder, "work/delegations").writeText("in the way")
        val provider = ScriptedProvider(textTurn("d".repeat(20_000)))

        val text = runner(mapOf("scout" to provider)).launch(listOf(SubagentTask("scout", "Find")), context).single().text

        assertTrue(text.length < 17_000)
        assertTrue(text.contains("cut at 16 KB"))
        assertEquals(SubagentStop.COMPLETED, recorder.outcomes.single().stop)
    }

    @Test
    fun aCrashingSubagentFailsAloneAndIsRecorded() = runBlocking {
        val crashing = object : ChatProvider {
            override val id = "crashing"
            override fun stream(request: app.jonaki.core.providerapi.ChatRequest): Flow<StreamEvent> = kotlinx.coroutines.flow.flow {
                throw IllegalStateException("socket closed")
            }
        }
        val healthy = ScriptedProvider(textTurn("Fine."))

        val reports = runner(mapOf("researcher" to crashing, "scout" to healthy)).launch(
            listOf(SubagentTask("researcher", "A"), SubagentTask("scout", "B")),
            context,
        )

        assertTrue(reports[0].text.contains("socket closed"))
        assertTrue(reports[1].text.startsWith("Fine."))
        assertEquals(setOf(SubagentStop.FAILED, SubagentStop.COMPLETED), recorder.outcomes.map { it.stop }.toSet())
    }

    @Test
    fun anAskCountsAgainstTheSubagentsCostCap() = runBlocking {
        val provider = ScriptedProvider(
            usageTurn(call("c1", "ask_parent", "question" to "Which?")),
            usageTurn(call("c2", "web_search", "query" to "a")),
        )
        val asker = ParentAsker { _, _, _ -> ParentAnswer.Answered("This one.", costUsd = 0.08) }

        runner(mapOf("researcher" to provider), pricePerCall = 0.01, asker = asker)
            .launch(listOf(SubagentTask("researcher", "Search")), context)

        assertEquals(SubagentStop.COST_LIMIT, recorder.outcomes.single().stop)
        assertEquals(0.10, recorder.outcomes.single().costUsd!!, 0.0001)
        assertEquals(listOf(0.08), recorder.askCosts)
        assertTrue(webSearch.receivedArguments.isEmpty())
    }

    @Test
    fun eachTurnMayBeRetriedOnce() = runBlocking {
        val overloaded = flowOf<StreamEvent>(StreamEvent.Failed("503", retryable = true))
        val provider = ScriptedProvider(
            overloaded,
            usageTurn(call("c1", "web_search", "query" to "a")),
            overloaded,
            textTurn("Done."),
        )

        runner(mapOf("researcher" to provider), limits = SubagentLimits(retryDelay = 1.milliseconds))
            .launch(listOf(SubagentTask("researcher", "Search")), context)

        assertEquals(SubagentStop.COMPLETED, recorder.outcomes.single().stop)
    }

    @Test
    fun stoppingOneSubagentLeavesTheOthersRunning() = runBlocking {
        val searchStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val slowSearch = FakeTool("web_search") {
            searchStarted.complete(Unit)
            delay(Long.MAX_VALUE)
            ToolOutput.success("never")
        }
        val stuck = ScriptedProvider(
            flowOf(
                StreamEvent.TextDelta("Looking at Ryans."),
                StreamEvent.ToolCallReady(call("c1", "web_search", "query" to "ryans laptop")),
                StreamEvent.Finished(FinishReason.TOOL_CALLS, null),
            ),
        )
        val healthy = ScriptedProvider(usageTurn(call("c1", "read_file", "path" to "a.md")), textTurn("Daraz done."))
        val subagents = runner(mapOf("researcher" to stuck, "scout" to healthy), tools = listOf(slowSearch, readFile))

        val launched = async {
            subagents.launch(listOf(SubagentTask("researcher", "Ryans"), SubagentTask("scout", "Daraz")), context)
        }
        // s0 is the researcher, stopped while its search hangs.
        searchStarted.await()
        assertTrue(subagents.stop("s0"))
        val reports = launched.await()

        assertTrue(reports[0].text.contains("Looking at Ryans."))
        assertTrue(reports[0].text.contains("Stopped by the user."))
        assertTrue(reports[1].text.startsWith("Daraz done."))
        assertEquals(setOf(SubagentStop.STOPPED, SubagentStop.COMPLETED), recorder.outcomes.map { it.stop }.toSet())
    }

    @Test
    fun aSubagentThatHasEndedCannotBeStopped() = runBlocking {
        val provider = ScriptedProvider(textTurn("Done."))
        val subagents = runner(mapOf("scout" to provider))
        subagents.launch(listOf(SubagentTask("scout", "Find")), context)

        assertFalse(subagents.stop("s0"))
    }

    @Test
    fun noMoreThanThreeSubagentsStart() = runBlocking {
        val provider = ScriptedProvider(textTurn("a"))
        val reports = runner(mapOf("scout" to provider)).launch(List(4) { SubagentTask("scout", "Find") }, context)

        assertTrue(reports.all { it.text.contains("at most 3") })
        assertTrue(recorder.starts.isEmpty())
    }
}

class RecordingSubagents : SubagentRecorder {
    val starts = mutableListOf<SubagentStart>()
    val finishedSteps = mutableListOf<Pair<ToolCall, SubagentStepStatus>>()
    val outcomes = mutableListOf<SubagentOutcome>()
    var modelCalls = 0

    override suspend fun subagentStarted(start: SubagentStart) {
        synchronized(this) { starts += start }
    }

    override suspend fun stepStarted(subagentId: String, stepCall: ToolCall) = Unit

    override suspend fun stepFinished(subagentId: String, stepCall: ToolCall, output: ToolOutput, status: SubagentStepStatus) {
        synchronized(this) { finishedSteps += stepCall to status }
    }

    override suspend fun modelCallFinished(subagentId: String, modelKey: String, usage: Usage, costUsd: Double?) {
        synchronized(this) { modelCalls += 1 }
    }

    override suspend fun textWritten(subagentId: String, text: String) = Unit

    val askCosts = mutableListOf<Double>()

    override suspend fun askCostAdded(subagentId: String, costUsd: Double) {
        synchronized(this) { askCosts += costUsd }
    }

    override suspend fun subagentFinished(subagentId: String, outcome: SubagentOutcome, answerText: String) {
        synchronized(this) { outcomes += outcome }
    }
}
