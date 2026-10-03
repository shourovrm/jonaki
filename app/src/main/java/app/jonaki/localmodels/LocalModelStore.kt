package app.jonaki.localmodels

import java.io.File

/**
 * The downloaded model files (D-133). Finished files live in
 * noBackupFilesDir/models as `<file>.gguf`, which the local-model provider
 * lists, with the file name as the model id. Unfinished downloads wait in
 * a sibling folder as `<file>.part`, so the provider never sees half a
 * file. noBackupFilesDir is never backed up, and the manifest turns backup
 * off as well.
 */
class LocalModelStore(noBackupFilesDir: File) {
    val modelsFolder = File(noBackupFilesDir, "models")
    private val partialFolder = File(noBackupFilesDir, "models-downloading")

    /** Every finished model, by name. */
    fun downloaded(): List<File> {
        val files = modelsFolder.listFiles { file -> file.isFile && file.name.endsWith(GGUF_SUFFIX) } ?: return emptyList()
        return files.sortedBy { file -> file.name.lowercase() }
    }

    fun isDownloaded(fileName: String): Boolean = modelFile(fileName).isFile

    fun modelFile(fileName: String): File = File(modelsFolder, checkedName(fileName))

    fun partFile(fileName: String): File = File(partialFolder, checkedName(fileName) + PART_SUFFIX)

    /** Bytes already fetched by an earlier attempt; 0 when none. */
    fun partialBytes(fileName: String): Long = partFile(fileName).length()

    fun delete(fileName: String) {
        modelFile(fileName).delete()
    }

    fun deletePartial(fileName: String) {
        partFile(fileName).delete()
    }

    /** A Hugging Face path may hold folders; only the last part is kept, so nothing lands outside the folder. */
    private fun checkedName(fileName: String): String {
        val name = File(fileName).name
        require(name.isNotEmpty() && name != "." && name != "..") { "not a file name: $fileName" }
        return name
    }

    private companion object {
        const val GGUF_SUFFIX = ".gguf"
        const val PART_SUFFIX = ".part"
    }
}
