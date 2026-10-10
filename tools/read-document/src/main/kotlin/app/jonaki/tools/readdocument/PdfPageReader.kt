package app.jonaki.tools.readdocument

import java.io.File
import java.io.IOException

/**
 * The PDF reader of read_document for a caller outside this tool: the app
 * hands it to web_fetch, which reads PDF links with it (D-185).
 */
object PdfPageReader {
    /**
     * The text of the pages from [firstPage] (counting from 1), at most
     * [pageLimit] of them. Throws [IOException] with a reason the model can
     * read when the file is locked or is not a PDF.
     */
    fun read(file: File, firstPage: Int, pageLimit: Int): PdfPageTexts {
        val window = try {
            PdfText.read(file, firstPage, pageLimit, fileLabel = "it")
        } catch (unreadable: UnreadableDocumentException) {
            throw IOException(unreadable.whatFailed)
        }
        return PdfPageTexts(window.totalUnits, window.sections.map { section -> section.text })
    }
}

class PdfPageTexts(
    /** All pages of the PDF, read or not. */
    val totalPages: Int,
    /** One text per page read, in order; empty for a page without text. */
    val texts: List<String>,
)
