package app.jonaki.core.agent

/**
 * Builds the part of the system prompt that tells a project thread about
 * the folder its project's threads share (D-135): that the thread belongs to
 * the project, that "/project/" paths reach the shared folder through the
 * usual file tools, and which files are there. It sits between the skills
 * and the memory, because files change more often than skills and less often
 * than facts. Paths are listed without sizes, so editing a file does not
 * change the prompt; adding or removing one does.
 */
object ProjectFilesSection {
    /** Enough to show what is there; a longer list costs every request of every project thread. */
    const val MAX_LISTED_FILES = 20

    fun build(projectName: String, projectFilePaths: List<String>): String {
        val lines = mutableListOf(
            "Project \"$projectName\": this thread belongs to it. Its threads share the folder /project/; " +
                "read and write files there with the file tools by that path, for example /project/notes.md.",
        )
        if (projectFilePaths.isEmpty()) {
            lines += "Project files: none yet."
            return lines.joinToString("\n")
        }
        val sortedPaths = projectFilePaths.sorted()
        lines += "Project files (${sortedPaths.size}):"
        lines += sortedPaths.take(MAX_LISTED_FILES).map { path -> "- $path" }
        val notListed = sortedPaths.size - MAX_LISTED_FILES
        if (notListed > 0) {
            lines += "- and $notListed more; find_files with path /project lists them."
        }
        return lines.joinToString("\n")
    }
}
