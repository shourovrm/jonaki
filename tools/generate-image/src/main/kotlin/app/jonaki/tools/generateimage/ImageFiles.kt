package app.jonaki.tools.generateimage

import app.jonaki.core.toolapi.IncomingFiles
import java.io.File
import java.util.Locale

/** Names and places for the pictures generate_image saves in a thread's images/ folder. */
object ImageFiles {
    const val FOLDER = "images"

    private const val MAX_BASE_NAME_LENGTH = 60
    private const val PROMPT_WORDS_IN_NAME = 6
    private const val FALLBACK_NAME = "image"
    private val knownExtensions = setOf(".png", ".jpg", ".jpeg", ".webp")

    /**
     * The file ending for a picture: from the media type the service named,
     * else from the first bytes of the file. Null when it is not a picture
     * type the chat can show.
     */
    fun extensionFor(mediaType: String, bytes: ByteArray): String? {
        val type = mediaType.substringBefore(';').trim().lowercase(Locale.ROOT)
        return when (type) {
            "image/png" -> "png"
            "image/jpeg", "image/jpg" -> "jpg"
            "image/webp" -> "webp"
            else -> extensionFromHeader(bytes)
        }
    }

    private fun extensionFromHeader(bytes: ByteArray): String? = when {
        bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "png"
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpg"
        bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "webp"
        else -> null
    }

    /**
     * A file name without folder and ending. The requested name wins; when it
     * leaves nothing usable, the first words of the prompt name the file, so
     * that the folder stays readable.
     */
    fun baseName(requested: String?, prompt: String): String {
        val fromRequest = requested?.let(::fromRequestedName).orEmpty()
        if (fromRequest.isNotEmpty()) {
            return fromRequest
        }
        val fromPrompt = fromPrompt(prompt)
        return fromPrompt.ifEmpty { FALLBACK_NAME }
    }

    /** "cat" in [folder] as "cat.png", else "cat (2).png", "cat (3).png" and so on; nothing is overwritten. */
    fun freeFile(folder: File, baseName: String, extension: String): File =
        IncomingFiles.freeFileIn(folder, "$baseName.$extension")

    private fun fromRequestedName(requested: String): String {
        val withoutFolders = requested.substringAfterLast('/').substringAfterLast('\\').trim()
        val extension = withoutFolders.substringAfterLast('.', missingDelimiterValue = "")
        val withoutExtension = if (".${extension.lowercase(Locale.ROOT)}" in knownExtensions) {
            withoutFolders.dropLast(extension.length + 1)
        } else {
            withoutFolders
        }
        return safeWords(withoutExtension).take(MAX_BASE_NAME_LENGTH).trim('-')
    }

    private fun fromPrompt(prompt: String): String {
        val words = prompt.split(Regex("[^\\p{L}\\p{M}\\p{N}]+")).filter { word -> word.isNotEmpty() }
        return words.take(PROMPT_WORDS_IN_NAME).joinToString("-").lowercase(Locale.ROOT).take(MAX_BASE_NAME_LENGTH).trim('-')
    }

    /** Vowel signs in Bangla and other scripts are marks, not letters, and belong to the word before them. */
    private fun isCombiningMark(character: Char): Boolean {
        val type = Character.getType(character)
        return type == Character.NON_SPACING_MARK.toInt() || type == Character.COMBINING_SPACING_MARK.toInt()
    }

    /** Letters of any script, digits, "-" and "_" stay; every run of anything else becomes one "-". */
    private fun safeWords(text: String): String {
        val replaced = StringBuilder()
        for (character in text) {
            val isKept = character.isLetterOrDigit() || isCombiningMark(character) || character == '-' || character == '_'
            replaced.append(if (isKept) character else '-')
        }
        return replaced.toString().replace(Regex("-{2,}"), "-").trim('-')
    }
}
