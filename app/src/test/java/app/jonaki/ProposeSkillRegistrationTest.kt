package app.jonaki

import app.jonaki.core.agent.AgentTypes
import app.jonaki.core.skills.SkillProposals
import app.jonaki.settings.LocalModelToolList
import app.jonaki.skills.LibrarySkillProposalSink
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where propose_skill is and is not offered. */
class ProposeSkillRegistrationTest {
    private val sink = LibrarySkillProposalSink(SkillProposals(Files.createTempDirectory("proposals").toFile()))

    private val withoutSink = ToolServices(
        searchBackends = emptyList(),
        videoSummarizer = null,
        webAccessEnabled = false,
        memoryStore = null,
    )

    private fun names(services: ToolServices) = ToolRegistry.tools(services).map { tool -> tool.name }

    @Test
    fun theToolIsOfferedOnlyWhenTheAppGivesItAProposalSink() {
        assertFalse("propose_skill" in names(withoutSink))
        assertTrue("propose_skill" in names(withoutSink.copy(skillProposals = sink)))
    }

    @Test
    fun theToolStaysOutOfALocalModelsSmallToolList() {
        val services = withoutSink.copy(
            skillProposals = sink,
            onlyTools = LocalModelToolList.offered(LocalModelToolList.DEFAULT + LocalModelToolList.OPTIONAL),
        )

        assertFalse("propose_skill" in names(services))
    }

    @Test
    fun noSubagentIsGivenTheTool() {
        assertTrue("propose_skill" in AgentTypes.NEVER_GIVEN)
    }

    @Test
    fun theToolAddsAboutAThousandCharactersOfPromptText() {
        val tool = ToolRegistry.tools(withoutSink.copy(skillProposals = sink)).first { it.name == "propose_skill" }

        val characters = tool.promptLine.length + tool.guidelines.sumOf { it.length } + tool.parameterSchema.toString().length

        assertEquals(true, characters in 900..1_400)
    }
}
