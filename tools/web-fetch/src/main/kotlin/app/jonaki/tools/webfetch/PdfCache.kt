package app.jonaki.tools.webfetch

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import okio.BufferedSource
import okio.buffer
import okio.sink

/**
 * Downloaded PDFs, kept for a short time so that a call for later pages of
 * the same address does not download the file again. A copy is named after
 * a hash of its address, never after anything the server sent.
 */
internal class PdfCache(
    private val folder: File,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    /** The copy of [url] when there is one younger than [MAX_AGE_MILLIS]. */
    fun find(url: String): File? {
        val copy = fileOf(url)
        if (!copy.isFile) return null
        val age = currentTimeMillis() - copy.lastModified()
        return copy.takeIf { age in 0..MAX_AGE_MILLIS }
    }

    /**
     * Writes [source] as the copy of [url] and returns it. Throws
     * [PdfTooLargeException] when the PDF is longer than [byteLimit], and
     * leaves no partial copy behind then or when the download breaks.
     */
    fun save(url: String, source: BufferedSource, byteLimit: Long): File {
        folder.mkdirs()
        val partial = File.createTempFile("download", PARTIAL_ENDING, folder)
        try {
            partial.sink().buffer().use { sink ->
                var written = 0L
                while (true) {
                    val read = source.read(sink.buffer, CHUNK_BYTES)
                    if (read == -1L) break
                    written += read
                    if (written > byteLimit) throw PdfTooLargeException()
                    sink.emitCompleteSegments()
                }
            }
            val copy = fileOf(url)
            if (!partial.renameTo(copy)) throw IOException("the downloaded PDF could not be kept")
            copy.setLastModified(currentTimeMillis())
            dropOldCopies()
            return copy
        } finally {
            partial.delete()
        }
    }

    fun remove(url: String) {
        fileOf(url).delete()
    }

    private fun fileOf(url: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        val name = hash.joinToString("") { byte -> "%02x".format(byte) }
        return File(folder, name + COPY_ENDING)
    }

    /** The cache holds at most [MAX_COPIES] PDFs, so it cannot grow past a few of the largest allowed. */
    private fun dropOldCopies() {
        val copies = folder.listFiles { file -> file.name.endsWith(COPY_ENDING) }.orEmpty()
        copies.sortedByDescending { copy -> copy.lastModified() }
            .drop(MAX_COPIES)
            .forEach { copy -> copy.delete() }
    }

    private companion object {
        const val COPY_ENDING = ".pdf"
        const val PARTIAL_ENDING = ".part"
        const val MAX_COPIES = 4
        const val MAX_AGE_MILLIS = 60L * 60 * 1000
        const val CHUNK_BYTES = 64L * 1024
    }
}

internal class PdfTooLargeException : IOException("the PDF is too large")
