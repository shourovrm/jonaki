package app.jonaki.tools.delegate

import app.jonaki.core.toolapi.SubagentLauncher
import app.jonaki.core.toolapi.SubagentModelInfo
import app.jonaki.core.toolapi.SubagentReport
import app.jonaki.core.toolapi.SubagentTask
import app.jonaki.core.toolapi.SubagentTypeInfo
import app.jonaki.core.toolapi.ToolContext
import java.nio.file.Files
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

    class FakeLauncher : SubagentLauncher {
        val launched = mutableListOf<List<SubagentTask>>()

        override val agentTypes = listOf("researcher", "scout", "writer", "worker").map { SubagentTypeInfo(it, "does $it work") }
        override val models = listOf(
            SubagentModelInfo("openrouter:z-ai/glm-5.3-flash", "GLM 5.3 Flash"),
            SubagentModelInfo("gemini:gemini-3-flash", "Gemini 3 Flash"),
        )
        override val extraToolNames = listOf("read_file", "write_file")

        override suspend fun launch(tasks: List<SubagentTask>, context: ToolContext): List<SubagentReport> {
            launched += tasks
            return tasks.mapIndexed { index, task ->
                val label = if (tasks.size > 1) "${task.agentType} ${index + 1}" else task.agentType
                SubagentReport(label, "${task.agentType} answered ${task.task}")
            }
        }
    }
}
