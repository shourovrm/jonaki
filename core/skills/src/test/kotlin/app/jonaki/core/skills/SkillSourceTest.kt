package app.jonaki.core.skills

import org.junit.Assert.assertEquals
import org.junit.Test

class SkillSourceTest {
    @Test
    fun githubTreeUrlIsAFolderWithBranchAndPath() {
        assertEquals(
            SkillSource.GitHubFolder("anthropics", "skills", ref = "main", path = "skills/pdf"),
            SkillSource.parse("https://github.com/anthropics/skills/tree/main/skills/pdf"),
        )
        assertEquals(
            SkillSource.GitHubFolder("anthropics", "skills", ref = "main", path = "skills/pdf"),
            SkillSource.parse("  github.com/anthropics/skills/tree/main/skills/pdf/  "),
        )
    }

    @Test
    fun githubRepoUrlIsItsRootFolderOnTheDefaultBranch() {
        assertEquals(
            SkillSource.GitHubFolder("owner", "my-skill", ref = null, path = ""),
            SkillSource.parse("https://github.com/owner/my-skill"),
        )
        assertEquals(
            SkillSource.GitHubFolder("owner", "my-skill", ref = null, path = ""),
            SkillSource.parse("https://github.com/owner/my-skill.git"),
        )
    }

    @Test
    fun githubLinkToASkillMdImportsItsWholeFolder() {
        assertEquals(
            SkillSource.GitHubFolder("o", "r", ref = "v2", path = "skills/report"),
            SkillSource.parse("https://github.com/o/r/blob/v2/skills/report/SKILL.md"),
        )
        assertEquals(
            SkillSource.GitHubFolder("o", "r", ref = "main", path = "report"),
            SkillSource.parse("https://raw.githubusercontent.com/o/r/main/report/SKILL.md"),
        )
    }

    @Test
    fun otherLinksAreASingleSkillMd() {
        assertEquals(
            SkillSource.SingleFile("https://example.com/skills/letter.md"),
            SkillSource.parse("https://example.com/skills/letter.md"),
        )
        assertEquals(
            SkillSource.SingleFile("https://raw.githubusercontent.com/o/r/main/notes.md"),
            SkillSource.parse("https://raw.githubusercontent.com/o/r/main/notes.md"),
        )
    }

    @Test
    fun notALinkIsRefused() {
        assertEquals(SkillSource.NotALink, SkillSource.parse("report skill"))
        assertEquals(SkillSource.NotALink, SkillSource.parse("ftp://example.com/SKILL.md"))
        assertEquals(SkillSource.NotALink, SkillSource.parse("https://github.com/owner"))
    }
}
