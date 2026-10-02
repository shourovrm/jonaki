package app.jonaki.core.toolapi

import java.io.File

/**
 * Turns "/skills/<name>/..." paths, as the system prompt lists them, into
 * files inside the skill library (D-037). Only read_file uses this, so the
 * model can read skills but never write them. "/skills" can never name a
 * file in a thread folder, because [ThreadPaths] refuses absolute paths
 * outside the thread.
 */
class SkillLibraryPaths(skillLibraryFolder: File) {
    private val root: File = skillLibraryFolder.canonicalFile

    /** The file the path names, or null when it is not a skill path or would leave the library. */
    fun resolve(path: String): File? {
        if (!isSkillPath(path)) {
            return null
        }
        val insideLibrary = path.removePrefix(ROOT).trimStart('/')
        // canonicalFile removes ".." and follows symbolic links, so both escapes are caught.
        val canonical = File(root, insideLibrary).canonicalFile
        val isInside = canonical == root || canonical.path.startsWith(root.path + File.separator)
        return if (isInside) canonical else null
    }

    companion object {
        const val ROOT = "/skills"

        fun isSkillPath(path: String): Boolean = path == ROOT || path.startsWith("$ROOT/")

        /** The path the system prompt gives for a skill's SKILL.md. */
        fun skillFilePath(skillName: String): String = "$ROOT/$skillName/SKILL.md"
    }
}
