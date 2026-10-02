package app.jonaki.core.agent

/** One enabled skill as the prompt lists it. */
data class PromptSkill(
    val name: String,
    val description: String,
    /** Where read_file finds the skill's SKILL.md, for example /skills/report/SKILL.md. */
    val path: String,
)

/**
 * Builds the skills part of the system prompt (D-014, D-039): one line per
 * enabled skill with its name, description and path. The model loads a
 * skill's full text with read_file only when a task needs it, so a skill
 * costs one line of prompt until it is used. Lines are sorted by name so
 * the bytes stay the same between runs while the skills do (D-005).
 */
object SkillSection {
    private const val HEADER = "Skills (read a skill's SKILL.md with read_file before a task that matches its " +
        "description, then follow it; paths inside a skill are relative to its folder):"

    fun build(skills: List<PromptSkill>): String {
        if (skills.isEmpty()) {
            return ""
        }
        val lines = mutableListOf(HEADER)
        for (skill in skills.sortedBy { skill -> skill.name }) {
            // A line break inside a description would break the list.
            val description = skill.description.replace('\n', ' ')
            lines += "- ${skill.name}: $description (${skill.path})"
        }
        return lines.joinToString("\n")
    }
}
