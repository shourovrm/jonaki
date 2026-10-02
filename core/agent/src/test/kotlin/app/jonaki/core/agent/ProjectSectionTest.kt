package app.jonaki.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectSectionTest {
    @Test
    fun blankInstructionsGiveNoSection() {
        assertEquals("", ProjectSection.build("Thesis", "  \n "))
    }

    @Test
    fun sectionNamesTheProjectAndHoldsItsInstructions() {
        val section = ProjectSection.build("Thesis", "Cite sources with year.\nBritish spelling.\n")

        assertEquals("Project \"Thesis\" instructions:\nCite sources with year.\nBritish spelling.", section)
    }

    @Test
    fun projectSectionGoesBeforeSkillsAndMemory() {
        val builder = PromptBuilder("Base.")
        val prompt = builder.systemPrompt(
            activeTools = emptyList(),
            memorySection = "Memory:\n- [1] likes tea",
            skillSection = "Skills:\n- report",
            projectSection = ProjectSection.build("Thesis", "British spelling."),
        )

        val projectAt = prompt.indexOf("Project \"Thesis\"")
        assertTrue(projectAt > prompt.indexOf("Base."))
        assertTrue(projectAt < prompt.indexOf("Skills:"))
        assertTrue(prompt.indexOf("Skills:") < prompt.indexOf("Memory:"))
    }
}
