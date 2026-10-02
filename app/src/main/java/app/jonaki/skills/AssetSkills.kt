package app.jonaki.skills

import android.content.res.AssetManager
import app.jonaki.core.skills.BuiltInSkill

/**
 * The built-in skills shipped as assets: every folder under assets/skills/
 * is one skill, named after the folder, with all files below it (M5 step 4).
 */
object AssetSkills {
    private const val ROOT = "skills"

    fun load(assets: AssetManager): List<BuiltInSkill> {
        // A plain file directly under skills/ is not a skill.
        val folderNames = assets.list(ROOT).orEmpty().sorted().filter { name ->
            assets.list("$ROOT/$name").orEmpty().isNotEmpty()
        }
        return folderNames.map { name ->
            BuiltInSkill(name = name, files = filesUnder(assets, "$ROOT/$name", relativeTo = "$ROOT/$name/"))
        }
    }

    /** AssetManager has no "is folder" call; a path that lists children is a folder (empty folders are not packaged). */
    private fun filesUnder(assets: AssetManager, path: String, relativeTo: String): Map<String, ByteArray> {
        val children = assets.list(path).orEmpty()
        if (children.isEmpty()) {
            val content = assets.open(path).use { stream -> stream.readBytes() }
            return mapOf(path.removePrefix(relativeTo) to content)
        }
        val files = mutableMapOf<String, ByteArray>()
        for (child in children) {
            files += filesUnder(assets, "$path/$child", relativeTo)
        }
        return files
    }
}
