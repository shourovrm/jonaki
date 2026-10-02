package app.jonaki.skills

import app.jonaki.core.agent.PromptSkill
import app.jonaki.core.skills.SkillEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadSkillsTest {
    private fun entry(name: String, problem: String? = null) = SkillEntry(
        name = name,
        description = if (problem == null) "Does $name." else "",
        problem = problem,
        isBuiltIn = false,
        isEdited = false,
    )

    @Test
    fun promptListsUsableSkillsThatAreNotSwitchedOff() {
        val entries = listOf(entry("report"), entry("slides"), entry("broken", problem = "name is missing"))

        val skills = ThreadSkills.forPrompt(entries, disabledSkills = "slides")

        assertEquals(listOf(PromptSkill("report", "Does report.", "/skills/report/SKILL.md")), skills)
    }

    @Test
    fun switchingAddsAndRemovesANameInSortedOrder() {
        val off = ThreadSkills.withSkill("report", enabled = false, disabledSkills = "slides")
        val backOn = ThreadSkills.withSkill("slides", enabled = true, disabledSkills = off)

        assertEquals("report,slides", off)
        assertEquals("report", backOn)
        assertEquals(setOf("report"), ThreadSkills.disabledNames(backOn))
        assertEquals(emptySet<String>(), ThreadSkills.disabledNames(""))
    }
}
