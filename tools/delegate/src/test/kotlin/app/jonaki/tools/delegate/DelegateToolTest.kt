package app.jonaki.tools.delegate

import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.SubagentLauncher
import app.jonaki.core.toolapi.SubagentBudget
import app.jonaki.core.toolapi.SubagentLimitSettings
import app.jonaki.core.toolapi.SubagentModelInfo
import app.jonaki.core.toolapi.SubagentReport
import app.jonaki.core.toolapi.SubagentTask
import app.jonaki.core.toolapi.SubagentTypeInfo
import app.jonaki.core.toolapi.ToolContext
import java.nio.file.Files
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DelegateToolTest {
    private val launcher = FakeLauncher()
    private val tool = DelegateTool(launcher)
    private val context = ToolContext(Files.createTempDirectory("thread").toFile(), OkHttpClient()).forCall("call-1")

    private fun arguments(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    @Test
    fun oneTaskReturnsTheSubagentsAnswer() = runBlocking {
        val output = tool.run(arguments("""{"agent":"researcher","task":"Find rain data"}"""), context)

        assertFalse(output.isError)
        assertEquals("researcher answered Find rain data", output.text)
        assertEquals(listOf(SubagentTask("researcher", "Find rain data")), launcher.launched.single())
    }

    @Test
    fun severalTasksRunTogetherAndEachAnswerIsHeaded() = runBlocking {
        val output = tool.run(
            arguments("""{"tasks":[{"agent":"researcher","task":"Laptop A"},{"agent":"scout","task":"Laptop B","extra_tools":["write_file"]}]}"""),
            context,
        )

        assertEquals(2, launcher.launched.single().size)
        assertEquals(listOf("write_file"), launcher.launched.single()[1].extraTools)
        assertTrue(output.text.contains("## researcher 1\nresearcher answered Laptop A"))
        assertTrue(output.text.contains("## scout 2\nscout answered Laptop B"))
    }

    @Test
    fun moreThanThreeTasksAreRefused() = runBlocking {
        val task = """{"agent":"scout","task":"x"}"""
        val output = tool.run(arguments("""{"tasks":[$task,$task,$task,$task]}"""), context)

        assertTrue(output.isError)
        assertTrue(output.text.contains("at most 3"))
        assertTrue(launcher.launched.isEmpty())
        assertTrue(tool.parameterSchema.toString().contains("\"maxItems\":3"))
    }

    @Test
    fun threeTasksRun() = runBlocking {
        val task = """{"agent":"scout","task":"x"}"""
        val output = tool.run(arguments("""{"tasks":[$task,$task,$task]}"""), context)

        assertFalse(output.isError)
        assertEquals(3, launcher.launched.single().size)
    }

    @Test
    fun anUnknownTypeNamesTheTypes() = runBlocking {
        val output = tool.run(arguments("""{"agent":"poet","task":"x"}"""), context)

        assertTrue(output.isError)
        assertTrue(output.text.contains("researcher, scout, writer, worker"))
    }

    @Test
    fun aModelIsFoundByKeyIdOrName() = runBlocking {
        tool.run(arguments("""{"agent":"scout","task":"a","model":"openrouter:z-ai/glm-5.3-flash"}"""), context)
        tool.run(arguments("""{"agent":"scout","task":"b","model":"z-ai/glm-5.3-flash"}"""), context)
        tool.run(arguments("""{"agent":"scout","task":"c","model":"glm 5.3 flash"}"""), context)

        assertEquals(List(3) { "openrouter:z-ai/glm-5.3-flash" }, launcher.launched.map { it.single().modelKey })
    }

    @Test
    fun anUnknownModelListsTheScopedModels() = runBlocking {
        val output = tool.run(arguments("""{"agent":"scout","task":"a","model":"gpt-9"}"""), context)

        assertTrue(output.isError)
        assertTrue(output.text.contains("GLM 5.3 Flash (openrouter:z-ai/glm-5.3-flash)"))
        assertTrue(output.text.contains("Gemini 3 Flash (gemini:gemini-3-flash)"))
        assertTrue(launcher.launched.isEmpty())
    }

    @Test
    fun anExtraToolTheThreadLacksIsRefused() = runBlocking {
        val output = tool.run(arguments("""{"agent":"scout","task":"a","extra_tools":["share_file"]}"""), context)

        assertTrue(output.isError)
        assertTrue(output.text.contains("read_file, write_file"))
    }

    @Test
    fun anEmptyTaskIsRefused() = runBlocking {
        val output = tool.run(arguments("""{"agent":"scout","task":"  "}"""), context)

        assertTrue(output.isError)
    }

    @Test
    fun theSchemaListsTheTypes() {
        assertTrue(tool.parameterSchema.toString().contains("\"enum\":[\"researcher\",\"scout\",\"writer\",\"worker\"]"))
        assertTrue(tool.guidelines.any { it.contains("NO context") })
    }

    /** D-137: two subagents per run start at once; a call that goes beyond waits for the user. */
    @Test
    fun upToTwoSubagentsPerRunStartWithoutAsking() {
        val two = arguments("""{"tasks":[{"agent":"researcher","task":"a"},{"agent":"scout","task":"b"}]}""")
        assertEquals(SideEffect.READ_ONLY, tool.sideEffectOf(two))
    }

    @Test
    fun aThirdSubagentInOneCallNeedsTheUser() {
        val three = arguments("""{"tasks":[{"agent":"researcher","task":"a"},{"agent":"scout","task":"b"},{"agent":"writer","task":"c"}]}""")
        assertEquals(SideEffect.NEEDS_USER, tool.sideEffectOf(three))
    }

    @Test
    fun aLaterCallCountsTheSubagentsAlreadyStartedInTheRun() {
        launcher.started = 2
        val one = arguments("""{"agent":"researcher","task":"a"}""")
        assertEquals(SideEffect.NEEDS_USER, tool.sideEffectOf(one))
    }

    /** The user's limits from Settings > Subagents replace the defaults (D-138). */
    @Test
    fun theUsersAutomaticLimitDecidesWhenACallAsks() {
        val strict = DelegateTool(FakeLauncher(SubagentLimitSettings(startedWithoutAsking = 0)))
        val generous = DelegateTool(FakeLauncher(SubagentLimitSettings(startedWithoutAsking = 4, perCall = 4)))
        val one = arguments("""{"agent":"researcher","task":"a"}""")
        val three = arguments("""{"tasks":[{"agent":"researcher","task":"a"},{"agent":"scout","task":"b"},{"agent":"writer","task":"c"}]}""")

        assertEquals(SideEffect.NEEDS_USER, strict.sideEffectOf(one))
        assertEquals(SideEffect.READ_ONLY, generous.sideEffectOf(three))
        assertTrue(generous.guidelines.any { it.startsWith("Up to 4 subagents per user message") })
    }

    @Test
    fun noSubagentStartsWithoutAskingWhenTheLimitIsZero() {
        val strict = DelegateTool(FakeLauncher(SubagentLimitSettings(startedWithoutAsking = 0)))

        assertTrue(strict.guidelines.any { it.startsWith("Every delegate call waits for the user's approval") })
    }

    @Test
    fun theUsersPerCallLimitShapesTheSchemaAndTheRefusal() = runBlocking {
        val fiveLauncher = FakeLauncher(SubagentLimitSettings(perCall = 5))
        val five = DelegateTool(fiveLauncher)
        val task = """{"agent":"scout","task":"x"}"""

        val ran = five.run(arguments("""{"tasks":[$task,$task,$task,$task,$task]}"""), context)
        val refused = five.run(arguments("""{"tasks":[$task,$task,$task,$task,$task,$task]}"""), context)

        assertFalse(ran.isError)
        assertTrue(refused.text.contains("at most 5"))
        assertTrue(five.parameterSchema.toString().contains("\"maxItems\":5"))
        assertTrue(five.promptLine.contains("up to 5 in parallel"))
        assertEquals(1, fiveLauncher.launched.size)
    }

    @Test
    fun theTimeLimitIsOneMinuteMoreThanTheLongestSubagentLimit() {
        val slowWriter = SubagentLimitSettings(budgets = mapOf("writer" to SubagentBudget(10, 10, 20)))

        assertEquals(21.minutes, DelegateTool(FakeLauncher(slowWriter)).timeLimit)
        // The researcher's default of 15 minutes is the longest of the defaults.
        assertEquals(16.minutes, DelegateTool(FakeLauncher()).timeLimit)
    }

    @Test
    fun aCustomTypesMinutesCountForTheTimeLimit() {
        val settings = SubagentLimitSettings(budgets = mapOf("price-checker" to SubagentBudget(10, 10, 30)))
        val launcher = FakeLauncher(settings, extraTypes = listOf(SubagentTypeInfo("price-checker", "Checks prices.")))

        assertEquals(31.minutes, DelegateTool(launcher).timeLimit)
    }

    @Test
    fun theGuidelinesStateEachTypesStepBudgetAndWhatAStepIs() {
        val guideline = tool.guidelines.single { it.startsWith("Each subagent has a budget of tool steps") }

        assertTrue(guideline.contains("researcher 20, scout 10, writer 10, worker 10"))
        assertTrue(guideline.contains("One step is one tool call"))
        assertTrue(guideline.contains("one narrow question"))
        assertTrue(guideline.contains("Never guess links"))
    }

    @Test
    fun theStepBudgetsInTheGuidelinesAreTheUsersAndCoverCustomTypes() {
        val settings = SubagentLimitSettings(
            budgets = mapOf("scout" to SubagentBudget(5, 10, 10), "price-checker" to SubagentBudget(7, 10, 10)),
        )
        val launcher = FakeLauncher(settings, extraTypes = listOf(SubagentTypeInfo("price-checker", "Checks prices.")))

        val guideline = DelegateTool(launcher).guidelines.single { it.startsWith("Each subagent has a budget") }

        assertTrue(guideline.contains("researcher 20, scout 5, writer 10, worker 10, price-checker 7"))
    }

    /** The prompt cache needs the same bytes on every request of a run (D-005). */
    @Test
    fun theGuidelinesDoNotChangeWhenTheRunsCountChanges() {
        val before = tool.guidelines
        launcher.started = 4

        assertEquals(before, tool.guidelines)
    }

    @Test
    fun theGuidelinesTellTheCapPerMessage() {
        assertTrue(tool.guidelines.any { it.startsWith("Start at most 5 subagents per user message") })
        val cap = DelegateTool(FakeLauncher(SubagentLimitSettings(maxPerMessage = 8)))
        assertTrue(cap.guidelines.any { it.startsWith("Start at most 8 subagents per user message") })
    }

    @Test
    fun aCallThatGoesOverTheCapNeedsTheUserEvenWhenTheAutomaticLimitIsAboveIt() {
        val limits = SubagentLimitSettings(startedWithoutAsking = 8, perCall = 3, maxPerMessage = 5)
        val launcher = FakeLauncher(limits)
        val tool = DelegateTool(launcher)
        val two = arguments("""{"tasks":[{"agent":"scout","task":"a"},{"agent":"scout","task":"b"}]}""")

        launcher.started = 3
        assertEquals(SideEffect.READ_ONLY, tool.sideEffectOf(two))
        launcher.started = 4
        assertEquals(SideEffect.NEEDS_USER, tool.sideEffectOf(two))
    }

    @Test
    fun aCustomTypeIsListedBesideTheBuiltInOnes() = runBlocking {
        val launcher = FakeLauncher(extraTypes = listOf(SubagentTypeInfo("price-checker", "Checks laptop prices in Dhaka shops.")))
        val tool = DelegateTool(launcher)

        val output = tool.run(arguments("""{"agent":"price-checker","task":"Find the X1 price"}"""), context)

        assertFalse(output.isError)
        assertEquals("price-checker", launcher.launched.single().single().agentType)
        assertTrue(tool.parameterSchema.toString().contains("\"worker\",\"price-checker\"]"))
        assertTrue(tool.guidelines.any { it.contains("price-checker: Checks laptop prices in Dhaka shops.") })
        assertTrue(tool.promptLine.contains("worker, price-checker"))
    }

    class FakeLauncher(
        override val limitSettings: SubagentLimitSettings = SubagentLimitSettings(),
        extraTypes: List<SubagentTypeInfo> = emptyList(),
    ) : SubagentLauncher {
        val launched = mutableListOf<List<SubagentTask>>()

        override val agentTypes =
            listOf("researcher", "scout", "writer", "worker").map { SubagentTypeInfo(it, "does $it work") } + extraTypes
        override val models = listOf(
            SubagentModelInfo("openrouter:z-ai/glm-5.3-flash", "GLM 5.3 Flash"),
            SubagentModelInfo("gemini:gemini-3-flash", "Gemini 3 Flash"),
        )
        override val extraToolNames = listOf("read_file", "write_file")
        var started = 0
        override val startedThisRun: Int get() = started

        override suspend fun launch(tasks: List<SubagentTask>, context: ToolContext): List<SubagentReport> {
            launched += tasks
            return tasks.mapIndexed { index, task ->
                val label = if (tasks.size > 1) "${task.agentType} ${index + 1}" else task.agentType
                SubagentReport(label, "${task.agentType} answered ${task.task}")
            }
        }
    }

    @Test
    fun theAnswersAreOutsideContentAndTheGuidelinesNameTheBlockersRoute() {
        assertEquals("subagent answers", tool.outsideContentSourceOf(JsonObject(emptyMap())))
        assertTrue(tool.guidelines.any { line -> line.contains("never share files") && line.contains("Blockers") })
    }
}
