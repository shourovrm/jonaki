package app.jonaki.feature.chat

import app.jonaki.core.agent.AttachmentLine
import app.jonaki.core.toolapi.ViewedImages

/**
 * Splits a stored user message into what the screen shows. The stored text
 * keeps the whole attachment line, because the model reads the paths from it
 * and the prompt cache needs the text unchanged (D-005, D-042); the screen
 * shows the images as thumbnails instead of as path text.
 */
object MessageAttachments {
    /** [shownText] is the message without its image paths; [imagePaths] are relative to the thread folder. */
    data class Split(val shownText: String, val imagePaths: List<String>)

    fun split(storedText: String): Split {
        val attachedPaths = AttachmentLine.pathsIn(storedText)
        val imagePaths = attachedPaths.filter(ViewedImages::isImagePath)
        if (imagePaths.isEmpty()) {
            return Split(storedText, emptyList())
        }
        val otherPaths = attachedPaths.filterNot(ViewedImages::isImagePath)
        return Split(withoutImageLine(storedText, attachedPaths, otherPaths), imagePaths)
    }

    /** The attachment line is rebuilt from [AttachmentLine] itself, so the format is written in one place. */
    private fun withoutImageLine(storedText: String, attachedPaths: List<String>, otherPaths: List<String>): String {
        val originalLine = AttachmentLine.appendTo("", attachedPaths)
        val lineStart = storedText.lastIndexOf(originalLine)
        if (lineStart < 0) {
            return storedText
        }
        val before = storedText.substring(0, lineStart).trimEnd()
        val after = storedText.substring(lineStart + originalLine.length)
        return AttachmentLine.appendTo(before, otherPaths) + after
    }
}
