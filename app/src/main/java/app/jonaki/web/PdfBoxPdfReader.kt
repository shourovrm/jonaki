package app.jonaki.web

import app.jonaki.tools.readdocument.PdfPageReader
import app.jonaki.tools.webfetch.PdfPages
import app.jonaki.tools.webfetch.PdfReadException
import app.jonaki.tools.webfetch.PdfReader
import java.io.File
import java.io.IOException

/** Lets web_fetch read a PDF link with the reader read_document has (D-185). */
class PdfBoxPdfReader : PdfReader {
    override fun read(file: File, firstPage: Int, pageLimit: Int): PdfPages {
        val pages = try {
            PdfPageReader.read(file, firstPage, pageLimit)
        } catch (unreadable: IOException) {
            throw PdfReadException(unreadable.message ?: "it is not a PDF this app can read")
        }
        return PdfPages(pages.totalPages, pages.texts)
    }
}
