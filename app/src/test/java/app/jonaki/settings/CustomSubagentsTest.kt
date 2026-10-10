package app.jonaki.settings

import app.jonaki.core.agent.AgentTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomSubagentsTest {
    private val priceChecker = CustomSubagent(
        name = "price-checker",
        description = "Checks laptop prices in Dhaka shops, with \"quotes\", commas and a ; too.",
        instructions = "Answer with a table.\nOne row per shop.",
        tools = listOf("web_search", "web_fetch"),
        modelKey = "openrouter:z-ai/glm-5.3-flash",
    )
    private val summarizer = CustomSubagent("summarizer", "Summarises files.", "", listOf("read_file"), modelKey = null)

    @Test
    fun theListSurvivesSavingAndReading() {
        val text = CustomSubagents.toText(listOf(priceChecker, summarizer))

        assertEquals(listOf(priceChecker, summarizer), CustomSubagents.fromText(text))
    }

    @Test
    fun brokenOrClashingEntriesAreLeftOut() {
        val text = """[{"name":"researcher","description":"x","instructions":"","tools":[]},""" +
            """{"name":"Bad Name","description":"x","instructions":"","tools":[]},""" +
            """{"name":"no-description","description":" ","instructions":"","tools":[]},""" +
            """{"name":"twice","description":"first","instructions":"","tools":[]},""" +
            """{"name":"twice","description":"second","instructions":"","tools":[]}]"""

        val read = CustomSubagents.fromText(text)

        assertEquals(listOf("twice"), read.map { it.name })
        assertEquals("first", read.single().description)
        assertEquals(emptyList<CustomSubagent>(), CustomSubagents.fromText("not json"))
    }

    @Test
    fun theToolChoicesNeverIncludeDelegateOrMemory() {
        assertFalse("delegate" in CustomSubagents.CHOOSABLE_TOOLS)
        assertFalse("memory" in CustomSubagents.CHOOSABLE_TOOLS)
        assertTrue(CustomSubagents.CHOOSABLE_TOOLS.containsAll(listOf("read_file", "web_search", "artifact", "run_code")))
    }

    @Test
    fun aCustomTypeRunsWithItsToolsAndText() {
        val type = CustomSubagents.agentTypesOf(listOf(priceChecker)).single()

        assertEquals("price-checker", type.name)
        assertEquals(setOf("web_search", "web_fetch"), type.defaultTools)
        assertEquals("Answer with a table.\nOne row per shop.", type.instructions)
        assertFalse(type.usesEveryThreadTool)
    }

    @Test
    fun aCustomTypesModelIsUsedWhileItIsScoped() {
        val choices = CustomSubagents.modelChoicesOf(listOf(priceChecker, summarizer))
        fun chosen(agentType: String, scoped: List<String>) = SubagentModelChoice.choose(
            agentType = agentType,
            requestedModelKey = null,
            savedChoices = choices,
            scopedModelKeys = scoped,
            threadModelKey = "deepseek:deepseek-v4",
            backgroundModelKey = "gemini:gemini-3-flash",
        )

        assertEquals("openrouter:z-ai/glm-5.3-flash", chosen("price-checker", listOf("openrouter:z-ai/glm-5.3-flash")))
        // Removed from the user's models: back to the thread's model, as for the built-in types.
        assertEquals("deepseek:deepseek-v4", chosen("price-checker", emptyList()))
        assertEquals("deepseek:deepseek-v4", chosen("summarizer", listOf("openrouter:z-ai/glm-5.3-flash")))
    }

    @Test
    fun savingAnEditedOneKeepsItsPlaceEvenWhenRenamed() {
        val renamed = summarizer.copy(name = "digest")

        val list = CustomSubagents.saved(listOf(summarizer, priceChecker), renamed, previousName = "summarizer")
        val added = CustomSubagents.saved(list, summarizer, previousName = null)

        assertEquals(listOf("digest", "price-checker"), list.map { it.name })
        assertEquals(listOf("digest", "price-checker", "summarizer"), added.map { it.name })
    }

    @Test
    fun theBuiltInNamesAreTaken() {
        assertEquals(AgentTypes.ALL.map { it.name }.toSet(), CustomSubagents.BUILT_IN_NAMES)
    }

    @Test
    fun aModelChoiceChangesOnlyTheNamedSubagent() {
        val first = CustomSubagent("price-checker", "Checks prices.", "Compare prices.", listOf("web_search"), modelKey = null)
        val second = CustomSubagent("summary-writer", "Writes summaries.", "Summarise.", listOf("read_file"), modelKey = "openrouter:a/b")

        val changed = CustomSubagents.withModel(listOf(first, second), "price-checker", "openrouter:c/d")

        assertEquals(listOf(first.copy(modelKey = "openrouter:c/d"), second), changed)
        assertEquals(listOf(first, second.copy(modelKey = null)), CustomSubagents.withModel(listOf(first, second), "summary-writer", null))
    }
}
