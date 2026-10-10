package app.jonaki.tools.readdocument

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.IOException

/** Reads the text layer of a PDF, a window of pages at a time, with PdfBox-Android (D-051). */
internal object PdfText {
    /** PDFs up to 25 MB come in (D-046); beyond this much memory PdfBox works in a temporary file. */
    private const val MAIN_MEMORY_BYTES = 32L * 1024 * 1024

    /** [fileLabel] is how an error names the file to the model. */
    fun read(file: File, firstPage: Int, pageLimit: Int, fileLabel: String = file.name): DocumentWindow {
        val document = open(file, fileLabel)
        document.use {
            val totalPages = document.numberOfPages
            val lastPage = minOf(totalPages, firstPage + pageLimit - 1)
            val stripper = PDFTextStripper()
            val sections = mutableListOf<Section>()
            for (pageNumber in firstPage..lastPage) {
                stripper.startPage = pageNumber
                stripper.endPage = pageNumber
                sections += Section(pageNumber, "Page $pageNumber", stripper.getText(document).trim())
            }
            return DocumentWindow("PDF", "page", totalPages, sections)
        }
    }

    private fun open(file: File, fileLabel: String): PDDocument {
        try {
            return PDDocument.load(file, MemoryUsageSetting.setupMixed(MAIN_MEMORY_BYTES))
        } catch (locked: InvalidPasswordException) {
            throw UnreadableDocumentException(
                "$fileLabel is locked with a password",
                "Ask the user for a copy without a password.",
            )
        } catch (broken: IOException) {
            throw UnreadableDocumentException(
                "$fileLabel could not be read as a PDF (${broken.message})",
                "The file may be damaged; ask the user for another copy.",
            )
        }
    }
}
