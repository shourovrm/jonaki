package app.jonaki.core.skills

import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** One skill folder as the skills screen and the prompt need it. */
data class SkillEntry(
    /** The folder's name, which a valid SKILL.md repeats in its front matter. */
    val name: String,
    /** Empty when [problem] is set. */
    val description: String,
    /** Why SKILL.md cannot be used, for example "description is missing"; null when it is fine. */
    val problem: String?,
    /** Shipped with the app (assets/skills/). */
    val isBuiltIn: Boolean,
    /** A built-in skill whose files differ from what the app installed; updates skip it. */
    val isEdited: Boolean,
)

/** A skill shipped in the app's assets: its folder name and every file in it by relative path. */
data class BuiltInSkill(
    val name: String,
    val files: Map<String, ByteArray>,
)

sealed interface InstallResult {
    data class Installed(val name: String) : InstallResult

    data class AlreadyExists(val name: String) : InstallResult

    data class Invalid(val reason: String) : InstallResult
}

sealed interface SaveResult {
    data object Saved : SaveResult

    data class Invalid(val reason: String) : SaveResult
}

/**
 * The skill library (D-008): one folder per skill under [folder], each with
 * a SKILL.md and any files it names. The model reads it through read_file
 * (read-only, D-037); only this class writes to it.
 *
 * [stateFile] lies outside [folder] and records, for each built-in skill,
 * the hash of the files the app installed and whether the user deleted it,
 * so that an app update replaces a built-in skill only while the user has
 * not changed it (D-038).
 */
class SkillLibrary(val folder: File, private val stateFile: File) {

    /** Every skill folder, sorted by name; folders whose name starts with "." are work in progress. */
    @Synchronized
    fun list(): List<SkillEntry> {
        val state = readState()
        val skillFolders = folder.listFiles { file -> file.isDirectory && !file.name.startsWith(".") }.orEmpty()
        return skillFolders.sortedBy { skillFolder -> skillFolder.name }.map { skillFolder ->
            entryOf(skillFolder, state[skillFolder.name])
        }
    }

    /** The SKILL.md text, or null when the skill does not exist. */
    @Synchronized
    fun readText(name: String): String? {
        val skillFile = skillFileOf(name) ?: return null
        return if (skillFile.isFile) skillFile.readText() else null
    }

    /** Replaces SKILL.md with the user's edited text, after checking its front matter. */
    @Synchronized
    fun saveText(name: String, text: String): SaveResult {
        val skillFile = skillFileOf(name)
        if (skillFile == null || !skillFile.parentFile.isDirectory) {
            return SaveResult.Invalid("the skill no longer exists")
        }
        when (val result = SkillFrontMatter.parse(text)) {
            is FrontMatterResult.Invalid -> return SaveResult.Invalid(result.reason)
            is FrontMatterResult.Parsed -> if (result.frontMatter.name != name) {
                return SaveResult.Invalid("the name must stay $name")
            }
        }
        skillFile.writeText(text)
        return SaveResult.Saved
    }

    @Synchronized
    fun delete(name: String) {
        val skillFolder = skillFolderOf(name) ?: return
        skillFolder.deleteRecursively()
        val state = readState()
        val record = state[name] ?: return
        writeState(state + (name to record.copy(deleted = true)))
    }

    /**
     * Installs a skill from its files (relative path to content). The name
     * comes from SKILL.md's front matter. An existing skill of that name is
     * kept unless [replace] is true.
     */
    @Synchronized
    fun install(files: Map<String, ByteArray>, replace: Boolean): InstallResult {
        val skillMarkdown = files[SKILL_FILE] ?: return InstallResult.Invalid("no SKILL.md found")
        val frontMatter = when (val result = SkillFrontMatter.parse(skillMarkdown.decodeToString())) {
            is FrontMatterResult.Invalid -> return InstallResult.Invalid(result.reason)
            is FrontMatterResult.Parsed -> result.frontMatter
        }
        val badPath = files.keys.firstOrNull { path -> !isSafeRelativePath(path) }
        if (badPath != null) {
            return InstallResult.Invalid("the file path $badPath is not allowed")
        }
        val target = File(folder, frontMatter.name)
        if (target.exists() && !replace) {
            return InstallResult.AlreadyExists(frontMatter.name)
        }
        writeFolder(frontMatter.name, files)
        return InstallResult.Installed(frontMatter.name)
    }

    /**
     * Called on every app start. A built-in skill is installed when missing,
     * updated when its folder still holds what the app installed last time,
     * and left alone when the user edited it, replaced it by an import or
     * deleted it. A skill the app no longer ships stays in the library as the
     * user's own skill.
     */
    @Synchronized
    fun installBuiltIns(builtIns: List<BuiltInSkill>) {
        val shippedNames = builtIns.map { builtIn -> builtIn.name }.toSet()
        val state = readState().filterKeys { name -> name in shippedNames }.toMutableMap()
        for (builtIn in builtIns) {
            val shippedHash = hashOf(builtIn.files)
            val record = state[builtIn.name]
            if (record?.deleted == true) {
                continue
            }
            val skillFolder = File(folder, builtIn.name)
            if (!skillFolder.isDirectory) {
                writeFolder(builtIn.name, builtIn.files)
                state[builtIn.name] = BuiltInRecord(shippedHash, deleted = false)
                continue
            }
            val currentHash = hashOf(filesIn(skillFolder))
            val unchangedSinceInstall = record != null && currentHash == record.installedHash
            if (currentHash != shippedHash && unchangedSinceInstall) {
                writeFolder(builtIn.name, builtIn.files)
                state[builtIn.name] = BuiltInRecord(shippedHash, deleted = false)
            } else if (record == null || currentHash == shippedHash) {
                // An import under a built-in name counts as an edit of the shipped skill.
                state[builtIn.name] = BuiltInRecord(shippedHash, deleted = false)
            }
        }
        writeState(state)
    }

    /** Replaces a built-in skill, edited or deleted, with the text the app ships. */
    @Synchronized
    fun resetBuiltIn(builtIn: BuiltInSkill) {
        writeFolder(builtIn.name, builtIn.files)
        writeState(readState() + (builtIn.name to BuiltInRecord(hashOf(builtIn.files), deleted = false)))
    }

    /** Names of built-in skills the user deleted, sorted. */
    @Synchronized
    fun deletedBuiltIns(): List<String> =
        readState().filterValues { record -> record.deleted }.keys.sorted()

    /** Brings back every deleted built-in skill. */
    @Synchronized
    fun restoreBuiltIns(builtIns: List<BuiltInSkill>) {
        val deleted = deletedBuiltIns().toSet()
        for (builtIn in builtIns) {
            if (builtIn.name in deleted) {
                resetBuiltIn(builtIn)
            }
        }
    }

    private fun entryOf(skillFolder: File, record: BuiltInRecord?): SkillEntry {
        val isBuiltIn = record != null && !record.deleted
        val isEdited = isBuiltIn && hashOf(filesIn(skillFolder)) != record?.installedHash
        val skillFile = File(skillFolder, SKILL_FILE)
        val result = if (skillFile.isFile) {
            SkillFrontMatter.parse(skillFile.readText())
        } else {
            FrontMatterResult.Invalid("no SKILL.md found")
        }
        return when (result) {
            is FrontMatterResult.Invalid -> SkillEntry(skillFolder.name, "", result.reason, isBuiltIn, isEdited)
            is FrontMatterResult.Parsed -> {
                val nameMismatch = result.frontMatter.name != skillFolder.name
                val problem = if (nameMismatch) "the name in SKILL.md is not ${skillFolder.name}" else null
                SkillEntry(skillFolder.name, result.frontMatter.description, problem, isBuiltIn, isEdited)
            }
        }
    }

    /** Writes into a hidden folder first, so a failed write never leaves half a skill under its name. */
    private fun writeFolder(name: String, files: Map<String, ByteArray>) {
        folder.mkdirs()
        val incoming = File(folder, ".incoming-$name")
        incoming.deleteRecursively()
        for ((relativePath, content) in files) {
            val file = File(incoming, relativePath)
            file.parentFile.mkdirs()
            file.writeBytes(content)
        }
        val target = File(folder, name)
        target.deleteRecursively()
        check(incoming.renameTo(target)) { "could not move the skill $name into place" }
    }

    private fun skillFolderOf(name: String): File? =
        if (SkillFrontMatter.isValidName(name)) File(folder, name) else null

    private fun skillFileOf(name: String): File? = skillFolderOf(name)?.let { skillFolder -> File(skillFolder, SKILL_FILE) }

    private fun filesIn(skillFolder: File): Map<String, ByteArray> =
        skillFolder.walkTopDown().filter { file -> file.isFile }.associate { file ->
            file.relativeTo(skillFolder).invariantSeparatorsPath to file.readBytes()
        }

    private fun readState(): Map<String, BuiltInRecord> {
        if (!stateFile.isFile) {
            return emptyMap()
        }
        val root = runCatching { Json.parseToJsonElement(stateFile.readText()).jsonObject }.getOrNull()
            ?: return emptyMap()
        return root.mapNotNull { (name, value) ->
            val fields = value as? JsonObject ?: return@mapNotNull null
            val hash = fields["installedHash"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val deleted = fields["deleted"]?.jsonPrimitive?.booleanOrNull ?: false
            name to BuiltInRecord(hash, deleted)
        }.toMap()
    }

    private fun writeState(state: Map<String, BuiltInRecord>) {
        val json = buildJsonObject {
            for ((name, record) in state.toSortedMap()) {
                put(
                    name,
                    buildJsonObject {
                        put("installedHash", record.installedHash)
                        put("deleted", record.deleted)
                    },
                )
            }
        }
        stateFile.parentFile?.mkdirs()
        stateFile.writeText(json.toString())
    }

    private data class BuiltInRecord(val installedHash: String, val deleted: Boolean)

    companion object {
        const val SKILL_FILE = "SKILL.md"

        /** A path such as "templates/page.html": no leading slash, no "..", no backslash. */
        fun isSafeRelativePath(path: String): Boolean {
            if (path.isEmpty() || path.startsWith("/") || path.contains('\\')) {
                return false
            }
            return path.split('/').none { segment -> segment.isEmpty() || segment == "." || segment == ".." }
        }

        /** SHA-256 over every file's path and content, in path order. */
        internal fun hashOf(files: Map<String, ByteArray>): String {
            val digest = MessageDigest.getInstance("SHA-256")
            for ((path, content) in files.toSortedMap()) {
                digest.update(path.toByteArray())
                digest.update(0)
                digest.update(content.size.toString().toByteArray())
                digest.update(0)
                digest.update(content)
            }
            return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }
    }
}
