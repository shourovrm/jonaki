package app.jonaki.tools.webfetch

/** Lays out the pages of a PDF as the text web_fetch returns. */
internal object PdfPageText {
    /**
     * As many whole pages as fit in [maxLength] characters, and at least one,
     * with the call that continues after the last page shown. Whole pages,
     * because the rest then needs no file: the next call reads on from the
     * cached PDF.
     */
    fun fitting(url: String, pages: PdfPages, firstPage: Int, maxLength: Int): String {
        for (pageCount in pages.texts.size downTo 2) {
            val text = describe(url, pages, firstPage, pageCount)
            if (text.length <= maxLength) return text
        }
        return describe(url, pages, firstPage, pageCount = 1)
    }

    private fun describe(url: String, pages: PdfPages, firstPage: Int, pageCount: Int): String {
        val output = StringBuilder()
        output.append("Source: $url\nPDF, ${pages.totalPages} ${if (pages.totalPages == 1) "page" else "pages"}.")
        pages.texts.take(pageCount).forEachIndexed { index, text ->
            output.append("\n\n--- Page ${firstPage + index} ---\n")
            output.append(text.ifEmpty { "[No text on this page; it may be a scan or a picture.]" })
        }
        val lastShown = firstPage + pageCount - 1
        if (lastShown < pages.totalPages) {
            output.append(
                "\n\n[Showing pages $firstPage-$lastShown of ${pages.totalPages}. " +
                    "Use web_fetch url=\"$url\" offset=${lastShown + 1} to continue.]",
            )
        }
        return output.toString()
    }
}
