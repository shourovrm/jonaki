package app.jonaki.settings

import app.jonaki.core.toolapi.Capability
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelToolListTest {
    @Test
    fun theDefaultIsTheFiveToolsOfTheDesign() {
        assertEquals(setOf("web_search", "web_fetch", "phone", "read_file", "read_document"), LocalModelToolList.DEFAULT)
    }

    @Test
    fun defaultOptionalAndNeverDoNotOverlap() {
        assertTrue(LocalModelToolList.DEFAULT.intersect(LocalModelToolList.OPTIONAL).isEmpty())
        assertTrue(LocalModelToolList.CHOOSABLE.intersect(LocalModelToolList.NEVER).isEmpty())
    }

    @Test
    fun everyListedNameIsARealToolName() {
        val toolNames = ToolGroup.entries.flatMap { group -> group.toolNames }.toSet()
        val listed = LocalModelToolList.CHOOSABLE + LocalModelToolList.NEVER
        assertEquals(emptyList<String>(), listed.filter { name -> name !in toolNames })
    }

    @Test
    fun offeredDropsWhatIsNeverOfferedAndUnknownNames() {
        val saved = setOf("read_file", "memory", "run_code", "delegate", "a_tool_from_an_old_version")
        assertEquals(setOf("read_file", "memory"), LocalModelToolList.offered(saved))
    }

    @Test
    fun switchingAddsAndRemovesChoosableToolsOnly() {
        val withMemory = LocalModelToolList.withTool(LocalModelToolList.DEFAULT, "memory", enabled = true)
        assertEquals(LocalModelToolList.DEFAULT + "memory", withMemory)
        assertEquals(LocalModelToolList.DEFAULT - "phone", LocalModelToolList.withTool(LocalModelToolList.DEFAULT, "phone", enabled = false))
        assertEquals(LocalModelToolList.DEFAULT, LocalModelToolList.withTool(LocalModelToolList.DEFAULT, "run_code", enabled = true))
    }

    @Test
    fun tokenCostIsPromptLineGuidelinesAndSchemaOverFour() {
        val schema = buildJsonObject { put("type", "object") } // {"type":"object"} is 17 characters
        val tool = TextOnlyTool(promptLine = "a".repeat(40), guidelines = listOf("b".repeat(20), "c".repeat(3)), parameterSchema = schema)
        assertEquals((40 + 23 + 17) / 4, LocalModelToolList.tokenCost(tool))
    }

    private class TextOnlyTool(
        override val promptLine: String,
        override val guidelines: List<String>,
        override val parameterSchema: JsonObject,
    ) : Tool {
        override val name = "text_only"
        override val sideEffect = SideEffect.READ_ONLY
        override val requiredCapabilities = emptySet<Capability>()
        override val timeLimit: Duration = 1.seconds

        override suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput = ToolOutput.success("")
    }
}
