package app.jonaki.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstructionsSectionTest {
    private val tutor = PromptPersona(name = "Tutor", instructions = "Explain like a patient teacher.")

    @Test
    fun nothingSetMeansNoSection() {
        val section = InstructionsSection.build(
            answerStyle = AnswerStyle.NORMAL,
            generalInstructions = "",
            persona = null,
            threadInstructions = "",
        )

        assertEquals("", section)
    }

    @Test
    fun blankTextsCountAsNotSet() {
        val section = InstructionsSection.build(AnswerStyle.NORMAL, "  \n ", null, "\n")

        assertEquals("", section)
    }

    @Test
    fun partsComeInOrderStyleGeneralPersonaThreadSoTheLaterOneWins() {
        val section = InstructionsSection.build(
            answerStyle = AnswerStyle.CONCISE,
            generalInstructions = "Answer in Bangla.\n",
            persona = tutor,
            threadInstructions = "  Use metric units.",
        )

        assertEquals(
            "User instructions (written by the user; where two parts disagree, the later part wins):\n\n" +
                "Answer style: concise. Lead with the answer and use as few words as it needs; leave out " +
                "background, caveats and summaries unless the user asks.\n\n" +
                "General:\nAnswer in Bangla.\n\n" +
                "Persona \"Tutor\" (speak in its voice and follow its instructions; your tools and rules stay the same):\n" +
                "Explain like a patient teacher.\n\n" +
                "This thread:\nUse metric units.",
            section,
        )
    }

    /** Most specific last: general, project, persona, thread (D-118). */
    @Test
    fun theProjectComesAfterGeneralAndBeforePersonaAndThread() {
        val section = InstructionsSection.build(
            answerStyle = AnswerStyle.NORMAL,
            generalInstructions = "Answer in Bangla.",
            persona = tutor,
            threadInstructions = "Use metric units.",
            projectPart = ProjectSection.build("Thesis", "British spelling."),
        )

        val generalAt = section.indexOf("General:")
        val projectAt = section.indexOf("Project \"Thesis\" instructions:\nBritish spelling.")
        val personaAt = section.indexOf("Persona \"Tutor\"")
        val threadAt = section.indexOf("This thread:")
        assertTrue(generalAt in 0 until projectAt)
        assertTrue(projectAt < personaAt)
        assertTrue(personaAt < threadAt)
    }

    @Test
    fun aProjectAloneMakesASection() {
        val section = InstructionsSection.build(AnswerStyle.NORMAL, "", null, "", ProjectSection.build("Thesis", "Cite sources."))

        assertEquals(
            "User instructions (written by the user; where two parts disagree, the later part wins):\n\n" +
                "Project \"Thesis\" instructions:\nCite sources.",
            section,
        )
    }

    @Test
    fun normalStyleAddsNoLine() {
        val section = InstructionsSection.build(AnswerStyle.NORMAL, "Answer in Bangla.", null, "")

        assertFalse(section.contains("Answer style"))
        assertTrue(section.endsWith("General:\nAnswer in Bangla."))
    }

    @Test
    fun detailedStyleAloneMakesASection() {
        val section = InstructionsSection.build(AnswerStyle.DETAILED, "", null, "")

        assertTrue(section.contains("Answer style: detailed."))
        assertFalse(section.contains("General:"))
    }

    @Test
    fun aPersonaWithoutInstructionsStillNamesTheVoice() {
        val section = InstructionsSection.build(AnswerStyle.NORMAL, "", PromptPersona("Pirate\nCaptain", ""), "")

        assertTrue(section.contains("Persona \"Pirate Captain\""))
    }

    @Test
    fun theSameSettingsGiveTheSameBytes() {
        val first = InstructionsSection.build(AnswerStyle.DETAILED, "A", tutor, "B")
        val second = InstructionsSection.build(AnswerStyle.DETAILED, "A", tutor.copy(), "B")

        assertEquals(first, second)
    }

    @Test
    fun instructionsGoAfterToolsAndBeforeSkillsAndMemory() {
        val builder = PromptBuilder("Base.")
        val instructions = InstructionsSection.build(AnswerStyle.NORMAL, "Answer in Bangla.", null, "")

        val prompt = builder.systemPrompt(
            activeTools = listOf(FakeTool("read_file")),
            memorySection = "Memory: x",
            skillSection = "Skills: y",
            instructionsSection = instructions,
        )

        val toolsAt = prompt.indexOf("Tools:")
        val instructionsAt = prompt.indexOf("User instructions")
        val skillsAt = prompt.indexOf("Skills: y")
        assertTrue(toolsAt in 0 until instructionsAt)
        assertTrue(instructionsAt < skillsAt)
        assertTrue(skillsAt < prompt.indexOf("Memory: x"))
    }

    @Test
    fun noInstructionsLeaveThePromptAsBefore() {
        val builder = PromptBuilder("Base.")
        val tools = listOf(FakeTool("read_file"))

        assertEquals(
            builder.systemPrompt(tools, "Memory: x", "Skills: y"),
            builder.systemPrompt(tools, "Memory: x", "Skills: y", instructionsSection = ""),
        )
    }
}
