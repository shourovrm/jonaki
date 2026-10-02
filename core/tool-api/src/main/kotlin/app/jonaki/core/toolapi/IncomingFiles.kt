package app.jonaki.core.toolapi

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale

/** A file was bigger than the import limit; the partly written copy is already deleted. */
class FileTooLargeException(val limitBytes: Long) : IOException("File is over the limit of $limitBytes bytes")

/**
 * Rules for files that come into a thread's inbox/ from outside the app: the
 * share sheet, the attach button and the linked folder (D-017). The app and
 * the share_file tool both use them, so every way in names and limits files
 * the same way.
 */
object IncomingFiles {
    /** 25 MB: a long PDF or a year of spreadsheet rows, while a phone's storage and the model's patience stay safe. */
    const val MAX_IMPORT_BYTES: Long = 25L * 1024 * 1024

    const val MAX_NAME_LENGTH = 120

    private const val FALLBACK_NAME = "file"

    /** A name that is one file, never a path: separators and control characters become "_". */
    fun safeName(displayName: String?): String {
        val replaced = displayName.orEmpty()
            .map { character -> if (character == '/' || character == '\\' || character.isISOControl()) '_' else character }
            .joinToString("")
            .trim()
        if (replaced.isEmpty() || replaced == "." || replaced == "..") {
            return FALLBACK_NAME
        }
        return shortened(replaced)
    }

    /** The file [name] would get in [folder]: the name itself, else "name (2).ext", "name (3).ext" and so on. */
    fun freeFileIn(folder: File, name: String): File {
        val first = File(folder, name)
        if (!first.exists()) {
            return first
        }
        val (base, extension) = splitExtension(name)
        var number = 2
        while (true) {
            val candidate = File(folder, "$base ($number)$extension")
            if (!candidate.exists()) {
                return candidate
            }
            number++
        }
    }

    /**
     * Copies [input] into [target] and returns the byte count. Content
     * providers often report no size, so the limit is checked while copying.
     */
    fun copyWithLimit(input: InputStream, target: File, maxBytes: Long = MAX_IMPORT_BYTES): Long {
        var copied = 0L
        try {
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) {
                        break
                    }
                    copied += count
                    if (copied > maxBytes) {
                        throw FileTooLargeException(maxBytes)
                    }
                    output.write(buffer, 0, count)
                }
            }
        } catch (exception: IOException) {
            target.delete()
            throw exception
        }
        return copied
    }

    /** "25 MB", "1.5 MB", "12 KB" or "900 bytes", for errors and listings. */
    fun describeSize(bytes: Long): String {
        val kilobyte = 1024.0
        val megabyte = kilobyte * 1024
        return when {
            bytes >= megabyte -> withoutTrailingZero(bytes / megabyte) + " MB"
            bytes >= kilobyte -> withoutTrailingZero(bytes / kilobyte) + " KB"
            else -> "$bytes bytes"
        }
    }

    private fun withoutTrailingZero(value: Double): String =
        String.format(Locale.ENGLISH, "%.1f", value).removeSuffix(".0")

    /** "report.pdf" to ("report", ".pdf"); a leading dot (".env") is part of the name. */
    private fun splitExtension(name: String): Pair<String, String> {
        val dot = name.lastIndexOf('.')
        if (dot <= 0) {
            return name to ""
        }
        return name.substring(0, dot) to name.substring(dot)
    }

    private fun shortened(name: String): String {
        if (name.length <= MAX_NAME_LENGTH) {
            return name
        }
        val (base, extension) = splitExtension(name)
        val keptExtension = if (extension.length < MAX_NAME_LENGTH / 2) extension else ""
        return base.take(MAX_NAME_LENGTH - keptExtension.length) + keptExtension
    }
}
