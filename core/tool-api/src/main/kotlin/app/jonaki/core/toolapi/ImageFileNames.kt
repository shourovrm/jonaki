package app.jonaki.core.toolapi

import java.io.File
import java.util.Locale

/**
 * Names and places for the pictures that generate_image and
 * generate_vector_image save in a thread's images/ folder. Both tools make
 * file names the same way, so the rules live here once.
 */
object ImageFileNames {
    const val FOLDER = "images"

    private const val MAX_BASE_NAME_LENGTH = 60
    private const val PROMPT_WORDS_IN_NAME = 6
    private const val FALLBACK_NAME = "image"
    private val knownExtensions = setOf(".png", ".jpg", ".jpeg", ".webp", ".svg")

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
