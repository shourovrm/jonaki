package app.jonaki.tools.generatevideo

import app.jonaki.core.toolapi.IncomingFiles
import java.io.File
import java.util.Locale

/** Names and places for the videos generate_video saves in a thread's videos/ folder. */
object VideoFiles {
    const val FOLDER = "videos"

    /** The jobs that were started and not yet collected, so that a Stop or a restart does not lose their ids. */
    const val PENDING_FILE = "pending-jobs.json"

    private const val MAX_BASE_NAME_LENGTH = 60
    private const val PROMPT_WORDS_IN_NAME = 6
    private const val FALLBACK_NAME = "video"
    private val knownExtensions = setOf(".mp4", ".webm", ".mov", ".m4v")

    /** How many bytes of the start of the file are enough to tell its type. */
    const val HEADER_BYTES = 16

    /**
     * The file ending for a video: from the media type the service named,
     * else from the first bytes of the file. Null when it is not a video, for
     * example when a link answered with a web page.
     */
    fun extensionFor(mediaType: String, header: ByteArray): String? {
        val type = mediaType.substringBefore(';').trim().lowercase(Locale.ROOT)
        return when (type) {
            "video/mp4" -> "mp4"
            "video/webm" -> "webm"
            "video/quicktime" -> "mov"
            else -> extensionFromHeader(header)
        }
    }

    private fun extensionFromHeader(header: ByteArray): String? = when {
        // An MP4 or MOV file starts with a box whose type "ftyp" is at bytes 4 to 7.
        header.size >= 8 && String(header, 4, 4, Charsets.US_ASCII) == "ftyp" -> "mp4"
        // WebM (Matroska) starts with the EBML marker.
        header.size >= 4 && header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() && header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte() -> "webm"
        else -> null
    }

    /** A file name without folder and ending: the requested name, else the first words of the prompt. */
    fun baseName(requested: String?, prompt: String?): String {
        val fromRequest = requested?.let(::fromRequestedName).orEmpty()
        if (fromRequest.isNotEmpty()) {
            return fromRequest
        }
        val fromPrompt = prompt?.let(::fromPrompt).orEmpty()
        return fromPrompt.ifEmpty { FALLBACK_NAME }
    }

    /** "boat" in [folder] as "boat.mp4", else "boat (2).mp4", "boat (3).mp4" and so on; nothing is overwritten. */
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
