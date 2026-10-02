package app.jonaki.core.skills

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * Turns a file the user picked into a skill's files (D-041): a zip (as
 * Claude's skill downloads are) keeps the folder that holds SKILL.md; any
 * other file is taken as the SKILL.md itself.
 */
object SkillArchive {
    private val ZIP_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private const val TOO_LARGE = "the skill is larger than 2 MB"

    fun filesOf(bytes: ByteArray): SkillFilesResult {
        if (!isZip(bytes)) {
            if (bytes.size > SkillDownloader.MAX_TOTAL_BYTES) {
                return SkillFilesResult.Failed(TOO_LARGE)
            }
            return SkillFilesResult.Files(mapOf(SkillLibrary.SKILL_FILE to bytes))
        }
        val entries = try {
            readEntries(bytes) ?: return SkillFilesResult.Failed(TOO_LARGE)
        } catch (broken: ZipException) {
            return SkillFilesResult.Failed("the zip file is damaged")
        }
        // The shallowest SKILL.md marks the skill's folder.
        val skillFilePath = entries.keys
            .filter { path -> path == SkillLibrary.SKILL_FILE || path.endsWith("/" + SkillLibrary.SKILL_FILE) }
            .minByOrNull { path -> path.count { character -> character == '/' } }
            ?: return SkillFilesResult.Failed("no SKILL.md found")
        val folderPrefix = skillFilePath.removeSuffix(SkillLibrary.SKILL_FILE)
        val files = entries
            .filterKeys { path -> path.startsWith(folderPrefix) }
            .mapKeys { (path, _) -> path.removePrefix(folderPrefix) }
        return SkillFilesResult.Files(files)
    }

    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= ZIP_SIGNATURE.size && bytes.copyOfRange(0, ZIP_SIGNATURE.size).contentEquals(ZIP_SIGNATURE)

    /** Every file entry by path, or null when the unpacked size passes the limit (a zip can unpack far larger). */
    private fun readEntries(bytes: ByteArray): Map<String, ByteArray>? {
        val entries = mutableMapOf<String, ByteArray>()
        var totalBytes = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || isMacMetadata(entry.name)) {
                    continue
                }
                val content = readLimited(zip, SkillDownloader.MAX_TOTAL_BYTES - totalBytes) ?: return null
                totalBytes += content.size
                entries[entry.name] = content
            }
        }
        return entries
    }

    /** InputStream.readNBytes needs Android 13, so the loop is written out. */
    private fun readLimited(zip: ZipInputStream, budgetBytes: Int): ByteArray? {
        val content = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val count = zip.read(buffer)
            if (count < 0) {
                return content.toByteArray()
            }
            content.write(buffer, 0, count)
            if (content.size() > budgetBytes) {
                return null
            }
        }
    }

    /** macOS adds __MACOSX/ copies and ._ files to zips it makes. */
    private fun isMacMetadata(path: String): Boolean =
        path.startsWith("__MACOSX/") || path.substringAfterLast('/').startsWith("._")
}
