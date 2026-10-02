package app.jonaki.skills

import app.jonaki.core.agent.PromptSkill
import app.jonaki.core.skills.SkillEntry
import app.jonaki.core.toolapi.SkillLibraryPaths

/**
 * A thread's skill switches (D-040). The thread stores the names it switched
 * off, comma-separated, so a skill added to the library is on everywhere.
 */
object ThreadSkills {
    fun disabledNames(disabledSkills: String): Set<String> =
        disabledSkills.split(",").filter { name -> name.isNotBlank() }.toSet()

    /** The new comma-separated value after the user flips one skill. */
    fun withSkill(name: String, enabled: Boolean, disabledSkills: String): String {
        val names = disabledNames(disabledSkills).toMutableSet()
        if (enabled) {
            names -= name
        } else {
            names += name
        }
        return names.sorted().joinToString(",")
    }

    /** Skills for the system prompt: usable ones (no problem) the thread has not switched off. */
    fun forPrompt(entries: List<SkillEntry>, disabledSkills: String): List<PromptSkill> {
        val disabled = disabledNames(disabledSkills)
        return entries
            .filter { entry -> entry.problem == null && entry.name !in disabled }
            .map { entry ->
                PromptSkill(name = entry.name, description = entry.description, path = SkillLibraryPaths.skillFilePath(entry.name))
            }
    }
}
