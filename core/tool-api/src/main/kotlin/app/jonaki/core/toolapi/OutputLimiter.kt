package app.jonaki.core.toolapi

import java.io.File

/**
 * Keeps tool output small enough for the model (D-005). Output over the limit
 * is saved whole to a file in the thread folder; the model gets the first
 * whole lines plus a notice that names the read_file call that continues it.
 */
class OutputLimiter(private val threadFolder: File) {

    fun limit(text: String, maxCharacters: Int, sourceName: String): String {
        if (text.length <= maxCharacters) {
            return text
        }
        val spillPath = "$SPILL_FOLDER/${saveToNewSpillFile(text, sourceName).name}"
        return firstPartWithNotice(text, maxCharacters, spillPath)
    }

    /**
     * Like [limit], but the whole text goes to [spillPath], relative to the
     * thread folder, so that it sits beside related files (a subagent's
     * answer in its delegation's folder, M7).
     */
    fun limitInto(text: String, maxCharacters: Int, spillPath: String): String {
        if (text.length <= maxCharacters) {
            return text
        }
        val file = File(threadFolder, spillPath)
        file.parentFile?.mkdirs()
        file.writeText(text)
        return firstPartWithNotice(text, maxCharacters, spillPath)
    }

    private fun firstPartWithNotice(text: String, maxCharacters: Int, spillPath: String): String {
        val lastNewlineInLimit = text.lastIndexOf('\n', startIndex = maxCharacters)

        if (lastNewlineInLimit <= 0) {
            // The first line alone is over the limit, so a line offset cannot continue it.
            val visibleText = text.substring(0, maxCharacters)
            val notice = "[Output truncated: showed ${visibleText.length} of ${text.length} characters. " +
                "Full output saved to $spillPath; search it with search_files path=\"$spillPath\".]"
            return "$visibleText\n\n$notice"
        }

        val visibleText = text.substring(0, lastNewlineInLimit)
        val visibleLineCount = visibleText.lines().size
        val totalLineCount = text.lines().size
        val notice = "[Output truncated: showed lines 1-$visibleLineCount of $totalLineCount " +
            "(${visibleText.length} of ${text.length} characters). Full output saved to $spillPath. " +
            "Use read_file path=\"$spillPath\" offset=${visibleLineCount + 1} to continue.]"
        return "$visibleText\n\n$notice"
    }

    private fun saveToNewSpillFile(text: String, sourceName: String): File {
        val folder = File(threadFolder, SPILL_FOLDER)
        folder.mkdirs()
        var number = 1
        var file = File(folder, "$sourceName-$number.txt")
        while (file.exists()) {
            number += 1
            file = File(folder, "$sourceName-$number.txt")
        }
        file.writeText(text)
        return file
    }

    companion object {
        /** Relative to the thread folder. */
        const val SPILL_FOLDER = "work/tool-output"
    }
}
