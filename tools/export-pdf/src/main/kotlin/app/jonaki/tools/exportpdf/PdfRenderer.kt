package app.jonaki.tools.exportpdf

import java.io.File
import kotlin.time.Duration

/**
 * Turns an HTML file into a PDF with the phone's WebView print engine, with
 * no print dialog. The app implements it; this module sees only the
 * interface, as web_fetch sees only PageRenderer.
 */
interface PdfRenderer {
    /**
     * Loads [htmlPath] (relative to [threadFolder]) with its scripts run and
     * no network, and writes the PDF to [outputFile]. Throws
     * [PdfRenderException] with a reason the model can read, and gives up
     * after [timeLimit].
     */
    suspend fun render(
        threadFolder: File,
        htmlPath: String,
        outputFile: File,
        pageSize: PdfPageSize,
        timeLimit: Duration,
    ): RenderedPdf
}

data class RenderedPdf(
    /** Null when the print engine did not report it. */
    val pageCount: Int?,
)

/** The paper the pages are laid out for. */
enum class PdfPageSize(val argument: String) {
    A4("a4"),
    LETTER("letter"),

    /** 16:9 landscape with no margins, one slide per page. */
    SLIDES("slides"),
}

class PdfRenderException(reason: String) : Exception(reason)
