package app.jonaki.tools.proposeskill

import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProposeSkillToolTest {
    private class FakeSink(private val outcome: SkillProposalOutcome? = null) : SkillProposalSink {
        override val maxBodyLength = 6_000
        override val maxWaiting = 5
        val calls = mutableListOf<List<String?>>()

        override suspend fun propose(name: String, description: String, body: String, replaces: String?): SkillProposalOutcome {
            calls += listOf(name, description, body, replaces)
            return outcome ?: SkillProposalOutcome.Saved(name = replaces ?: name, replaces = replaces)
        }
    }

    private val sink = FakeSink()
    private val tool = ProposeSkillTool(sink)
    private val context = ToolContext(Files.createTempDirectory("thread").toFile(), OkHttpClient())

    private fun call(vararg arguments: Pair<String, String>, sink: FakeSink = this.sink): ToolOutput =
        runBlocking {
            ProposeSkillTool(sink).run(JsonObject(arguments.associate { (key, value) -> key to JsonPrimitive(value) }), context)
        }

    private val complete = arrayOf("name" to "bangla-letter", "description" to "Write a formal Bangla letter.", "body" to "Steps.")

    @Test
    fun changesOnlyTheAppsOwnRecordsSoNoCardIsShown() {
        assertEquals(SideEffect.CHANGES_APP_DATA, tool.sideEffect)
        assertEquals("propose_skill", tool.name)
    }

    @Test
    fun aSavedNewSkillTellsTheModelToSayItInOneSentenceAndWhereToReview() {
        val output = call(*complete)

        assertFalse(output.text, output.isError)
        assertTrue(output.text, output.text.contains("bangla-letter"))
        assertTrue(output.text, output.text.contains("one sentence"))
        assertTrue(output.text, output.text.contains("Skills"))
        assertEquals(listOf(listOf("bangla-letter", "Write a formal Bangla letter.", "Steps.", null)), sink.calls)
    }

    @Test
    fun aSavedChangeNamesTheSkillItChanges() {
        val output = call(*complete, "replaces" to "report")

        assertTrue(output.text, output.text.contains("change to the skill \"report\""))
        assertEquals("report", sink.calls.single()[3])
    }

    @Test
    fun aBlankReplacesCountsAsANewSkill() {
        call(*complete, "replaces" to "  ")

        assertEquals(null, sink.calls.single()[3])
    }

    @Test
    fun eachMissingArgumentIsNamedInTheError() {
        for (missing in listOf("name", "description", "body")) {
            val arguments = complete.filter { (key, _) -> key != missing }.toTypedArray()

            val output = call(*arguments)

            assertTrue(output.text, output.isError)
            assertTrue(output.text, output.text.contains("argument $missing is missing"))
        }
        assertTrue(sink.calls.isEmpty())
    }

    @Test
    fun blankArgumentsCountAsMissing() {
        val output = call("name" to "bangla-letter", "description" to "  ", "body" to "Steps.")

        assertTrue(output.text, output.isError)
        assertTrue(output.text, output.text.contains("argument description is missing"))
    }

    @Test
    fun aRefusalFromTheStoreIsAnErrorWithItsAdvice() {
        val refusing = FakeSink(SkillProposalOutcome.Refused("5 proposals are already waiting", "Wait until the user has reviewed them."))

        val output = call(*complete, sink = refusing)

        assertTrue(output.isError)
        assertEquals("Error: 5 proposals are already waiting. Wait until the user has reviewed them.", output.text)
    }

    @Test
    fun theLimitsInThePromptComeFromTheStore() {
        val guidelines = tool.guidelines.joinToString(" ")

        assertTrue(guidelines, guidelines.contains("6,000 characters"))
        assertTrue(guidelines, guidelines.contains("one proposal per task"))
        assertTrue(guidelines, guidelines.contains("replaces"))
    }

    @Test
    fun theSchemaRequiresNameDescriptionAndBodyOnly() {
        val required = tool.parameterSchema["required"].toString()

        assertEquals("[\"name\",\"description\",\"body\"]", required)
        assertTrue(tool.parameterSchema["properties"].toString().contains("replaces"))
    }
}
