package app.jonaki.tools.webfetch

import java.io.File

/**
 * Reads the text of a PDF file a few pages at a time. The app implements it
 * with the reader read_document uses; this module sees only the interface,
 * as it sees only [PageRenderer], so one tool does not depend on another.
 */
interface PdfReader {
    /**
     * Returns the pages from [firstPage] (counting from 1), at most
     * [pageLimit] of them, or throws [PdfReadException] with a reason the
     * model can read.
     */
    fun read(file: File, firstPage: Int, pageLimit: Int): PdfPages
}

data class PdfPages(
    /** All pages of the PDF, shown or not. */
    val totalPages: Int,
    /** One text per page read, in order; empty for a page without text. */
    val texts: List<String>,
)

class PdfReadException(reason: String) : Exception(reason)

/**
 * What web_fetch needs to read a PDF link (D-185): the reader, and a folder
 * in the app's private cache for the downloaded copies. The folder is not
 * the thread folder, so no tool and no other app can reach a copy by path.
 */
class PdfSupport(val reader: PdfReader, cacheFolder: File) {
    internal val cache = PdfCache(cacheFolder)
}
