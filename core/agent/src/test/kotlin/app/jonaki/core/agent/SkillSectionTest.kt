package app.jonaki.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillSectionTest {
    private fun skill(name: String, description: String) =
        PromptSkill(name = name, description = description, path = "/skills/$name/SKILL.md")

    @Test
    fun noSkillsMeansNoSection() {
        assertEquals("", SkillSection.build(emptyList()))
    }

    @Test
    fun listsEachSkillSortedByNameWithItsPathAndTellsHowToLoadIt() {
        val section = SkillSection.build(
            listOf(skill("slides", "Make a slide deck."), skill("report", "Write a report\nas an HTML page.")),
        )

        assertEquals(
            "Skills (read a skill's SKILL.md with read_file before a task that matches its description, " +
                "then follow it; paths inside a skill are relative to its folder):\n" +
                "- report: Write a report as an HTML page. (/skills/report/SKILL.md)\n" +
                "- slides: Make a slide deck. (/skills/slides/SKILL.md)",
            section,
        )
    }

    @Test
    fun skillsGoBetweenToolsAndMemory() {
        val builder = PromptBuilder("Base.")
        val skills = SkillSection.build(listOf(skill("report", "Write a report.")))

        val prompt = builder.systemPrompt(listOf(FakeTool("read_file")), memorySection = "Memory: x", skillSection = skills)

        val toolsAt = prompt.indexOf("Tools:")
        val skillsAt = prompt.indexOf("Skills (")
        val memoryAt = prompt.indexOf("Memory: x")
        assertTrue(toolsAt in 0 until skillsAt)
        assertTrue(skillsAt < memoryAt)
        assertEquals(prompt, builder.systemPrompt(listOf(FakeTool("read_file")), "Memory: x", skills))
    }
}
