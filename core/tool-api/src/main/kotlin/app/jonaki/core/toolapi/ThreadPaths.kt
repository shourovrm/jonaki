package app.jonaki.core.toolapi

import java.io.File

/**
 * Turns paths the model writes into files inside one thread folder, or,
 * for paths under "/project", inside the folder the threads of the thread's
 * project share (D-135). Every file tool goes through this, so no tool can
 * read or write outside those two folders (D-004: no sandbox, only app
 * folders). [projectFolder] is null for a thread without a project, and
 * then "/project" paths are refused like any other absolute path.
 */
class ThreadPaths(threadFolder: File, projectFolder: File? = null) {
    private val root: File = threadFolder.canonicalFile
    private val projectRoot: File? = projectFolder?.canonicalFile

    /** The file the path names, or null when it would leave the thread or project folder. */
    fun resolve(path: String): File? {
        if (isProjectPath(path)) {
            val projectRoot = projectRoot ?: return null
            return inside(projectRoot, File(projectRoot, path.removePrefix(PROJECT_ROOT).trimStart('/')))
        }
        val asGiven = File(path)
        val candidate = if (asGiven.isAbsolute) asGiven else File(root, path)
        return inside(root, candidate)
    }

    /** The path as the model should see it: relative to the thread folder, or under "/project". */
    fun relativePath(file: File): String {
        val canonical = file.canonicalFile
        val projectRoot = projectRoot
        if (projectRoot != null && isInside(projectRoot, canonical)) {
            val insideProject = canonical.relativeTo(projectRoot).invariantSeparatorsPath
            return if (insideProject.isEmpty()) PROJECT_ROOT else "$PROJECT_ROOT/$insideProject"
        }
        return canonical.relativeTo(root).invariantSeparatorsPath
    }

    /** True when the path names the project folder or a file in it. */
    fun isInProject(file: File): Boolean {
        val projectRoot = projectRoot ?: return false
        return isInside(projectRoot, file.canonicalFile)
    }

    private fun inside(folder: File, candidate: File): File? {
        // canonicalFile removes ".." and follows symbolic links, so both escapes are caught.
        val canonical = candidate.canonicalFile
        return if (isInside(folder, canonical)) canonical else null
    }

    private fun isInside(folder: File, canonical: File): Boolean =
        canonical == folder || canonical.path.startsWith(folder.path + File.separator)

    companion object {
        const val PROJECT_ROOT = "/project"

        fun isProjectPath(path: String): Boolean = path == PROJECT_ROOT || path.startsWith("$PROJECT_ROOT/")
    }
}
