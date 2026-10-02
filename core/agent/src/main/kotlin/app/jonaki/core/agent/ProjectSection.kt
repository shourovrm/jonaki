package app.jonaki.core.agent

/**
 * Builds the project part of the system prompt (D-PRJ-1): the instructions
 * the user wrote for the project a thread belongs to. It changes only when
 * the user edits them or moves the thread, so the prompt cache holds (D-005).
 */
object ProjectSection {
    fun build(projectName: String, instructions: String): String {
        val trimmed = instructions.trim()
        if (trimmed.isEmpty()) {
            return ""
        }
        return "Project \"$projectName\" instructions:\n$trimmed"
    }
}
