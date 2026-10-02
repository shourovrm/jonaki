package app.jonaki.core.toolapi

import java.io.File

/**
 * Turns paths the model writes into files inside one thread folder. Every
 * file tool goes through this, so no tool can read or write outside the
 * thread (D-004: no sandbox, only app folders).
 */
class ThreadPaths(threadFolder: File) {
    private val root: File = threadFolder.canonicalFile

    /** The file the path names, or null when it would leave the thread folder. */
    fun resolve(path: String): File? {
        val asGiven = File(path)
        val candidate = if (asGiven.isAbsolute) asGiven else File(root, path)
        // canonicalFile removes ".." and follows symbolic links, so both escapes are caught.
        val canonical = candidate.canonicalFile
        val isInside = canonical == root || canonical.path.startsWith(root.path + File.separator)
        if (!isInside) {
            return null
        }
        return canonical
    }

    /** The path as the model should see it, relative to the thread folder. */
    fun relativePath(file: File): String =
        file.canonicalFile.relativeTo(root).invariantSeparatorsPath
}
