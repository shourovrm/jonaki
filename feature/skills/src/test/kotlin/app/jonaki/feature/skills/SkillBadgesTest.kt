package app.jonaki.feature.skills

import org.junit.Assert.assertEquals
import org.junit.Test

class SkillBadgesTest {
    private val row = SkillRowUi(
        name = "report",
        description = "Write a report.",
        problem = null,
        isBuiltIn = false,
        isEdited = false,
        enabledInThread = true,
    )

    @Test
    fun importedSkillHasNoBadge() {
        assertEquals(emptyList<SkillBadge>(), SkillBadges.of(row))
    }

    @Test
    fun editedBuiltInShowsBoth() {
        assertEquals(listOf(SkillBadge.BUILT_IN, SkillBadge.EDITED), SkillBadges.of(row.copy(isBuiltIn = true, isEdited = true)))
    }
}
