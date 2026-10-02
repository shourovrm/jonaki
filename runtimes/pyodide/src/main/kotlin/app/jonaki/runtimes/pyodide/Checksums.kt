package app.jonaki.runtimes.pyodide

import java.io.File
import java.security.MessageDigest

/** SHA-256 in the lowercase hexadecimal form that pyodide-lock.json and [PinnedFile] use. */
object Checksums {
    fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return hex(digest.digest())
    }

    fun sha256Of(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    fun hex(digest: ByteArray): String = digest.joinToString("") { byte -> "%02x".format(byte) }

    private const val BUFFER_BYTES = 64 * 1024
}
