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
            val notice = "$NOTICE_START showed ${visibleText.length} of ${text.length} characters. " +
                "Full output saved to $spillPath; search it with search_files path=\"$spillPath\".]"
            return "$visibleText\n\n$notice"
        }

        val visibleText = text.substring(0, lastNewlineInLimit)
        val visibleLineCount = visibleText.lines().size
        val totalLineCount = text.lines().size
        val notice = "$NOTICE_START showed lines 1-$visibleLineCount of $totalLineCount " +
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

        private const val NOTICE_START = "[Output truncated:"
        private const val NOTICE_SEPARATOR = "\n\n$NOTICE_START"
        private val spillPathInNotice = Regex("""Full output saved to (\S+?)[.;](\s|$)""")

        /** The part of a [limit]ed text that was kept, without the notice; the text itself when nothing was cut. */
        fun visiblePartOf(limitedText: String): String {
            val noticeIndex = limitedText.lastIndexOf(NOTICE_SEPARATOR)
            return if (noticeIndex < 0) limitedText else limitedText.substring(0, noticeIndex)
        }

        /** Where [limit] saved the whole text, relative to the thread folder; null when nothing was cut. */
        fun spillPathOf(limitedText: String): String? {
            val noticeIndex = limitedText.lastIndexOf(NOTICE_SEPARATOR)
            if (noticeIndex < 0) {
                return null
            }
            val notice = limitedText.substring(noticeIndex)
            return spillPathInNotice.find(notice)?.groupValues?.get(1)
        }
    }
}
