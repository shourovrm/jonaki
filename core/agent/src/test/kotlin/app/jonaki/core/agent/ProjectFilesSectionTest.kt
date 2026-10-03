package app.jonaki.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectFilesSectionTest {
    @Test
    fun anEmptyFolderSaysSoAndStillExplainsTheProjectPath() {
        val section = ProjectFilesSection.build("Thesis", emptyList())

        assertTrue(section.startsWith("Project \"Thesis\": this thread belongs to it."))
        assertTrue(section.contains("/project/"))
        assertTrue(section.endsWith("Project files: none yet."))
    }

    @Test
    fun filesAreListedInPathOrderWhateverOrderTheyCameIn() {
        val section = ProjectFilesSection.build("Thesis", listOf("/project/b.md", "/project/a.csv"))

        assertTrue(section.endsWith("Project files (2):\n- /project/a.csv\n- /project/b.md"))
    }

    @Test
    fun aLongListStopsAtTwentyAndCountsTheRest() {
        val paths = (1..25).map { number -> "/project/file-%02d.txt".format(number) }

        val section = ProjectFilesSection.build("Thesis", paths)

        assertTrue(section.contains("- /project/file-20.txt"))
        assertTrue(!section.contains("file-21"))
        assertTrue(section.endsWith("- and 5 more; find_files with path /project lists them."))
    }

    @Test
    fun theSectionGoesBetweenSkillsAndMemory() {
        val prompt = PromptBuilder("Base.").systemPrompt(
            activeTools = emptyList(),
            memorySection = "Memory:\n- [1] likes tea",
            skillSection = "Skills:\n- report",
            projectFilesSection = ProjectFilesSection.build("Thesis", emptyList()),
        )

        val skillsAt = prompt.indexOf("Skills:")
        val projectAt = prompt.indexOf("Project \"Thesis\"")
        assertTrue(skillsAt < projectAt)
        assertTrue(projectAt < prompt.indexOf("Memory:"))
        assertEquals(1, Regex("Project \"Thesis\"").findAll(prompt).count())
    }
}
