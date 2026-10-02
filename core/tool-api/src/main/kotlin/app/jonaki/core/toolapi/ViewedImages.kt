package app.jonaki.core.toolapi

/**
 * An image the model can be shown: an image file, or one page of a PDF
 * rendered as an image. [reference] is the form written into messages, for
 * example "inbox/photo.jpg" or "docs/scan.pdf#page=3".
 */
data class ImageSource(
    /** Relative to the thread folder. */
    val path: String,
    /** Counting from 1; null for an image file. */
    val pdfPage: Int? = null,
) {
    val reference: String
        get() = if (pdfPage == null) path else "$path$PAGE_MARK$pdfPage"

    companion object {
        private const val PAGE_MARK = "#page="

        fun fromReference(reference: String): ImageSource {
            val markIndex = reference.lastIndexOf(PAGE_MARK)
            if (markIndex < 0) {
                return ImageSource(reference)
            }
            val page = reference.substring(markIndex + PAGE_MARK.length).toIntOrNull()
                ?: return ImageSource(reference)
            return ImageSource(reference.substring(0, markIndex), page)
        }
    }
}

/**
 * The plain-text contract between view_image and the agent loop (D-050).
 * Tool results are text in both wire formats, so view_image answers with a
 * fixed first line naming the image, and before each request the loop adds
 * the image itself as a user message after the tool results.
 */
object ViewedImages {
    const val TOOL_NAME = "view_image"

    /** File endings decoded as images; HEIC only where the phone can decode it. */
    val IMAGE_EXTENSIONS: Set<String> = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif")

    private const val RESULT_PREFIX = "Image shown: "

    fun isImagePath(path: String): Boolean =
        path.substringAfterLast('.', missingDelimiterValue = "").lowercase() in IMAGE_EXTENSIONS

    fun resultText(source: ImageSource): String =
        RESULT_PREFIX + source.reference + "\nThe image follows in the next message."

    /** The image a view_image result names; null for any other text. */
    fun sourceIn(resultText: String): ImageSource? {
        if (!resultText.startsWith(RESULT_PREFIX)) {
            return null
        }
        val reference = resultText.lineSequence().first().removePrefix(RESULT_PREFIX).trim()
        if (reference.isEmpty()) {
            return null
        }
        return ImageSource.fromReference(reference)
    }
}
