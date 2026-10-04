package android.print

import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Writes what a print adapter lays out into a PDF file, with no print
 * dialog. This file sits in the package android.print on purpose: the
 * constructors of PrintDocumentAdapter.LayoutResultCallback and
 * WriteResultCallback are package-private, so only a class of that package
 * can subclass them, and without them nothing can call the adapter's
 * onLayout and onWrite (the standard way to print a WebView without the
 * system dialog). Keep it small; the rendering logic stays in app.jonaki.web.
 *
 * Call [start] and [cancel] on the main thread. [onFinished] runs on the
 * main thread too, once: with the page count the layout reported (null when
 * it did not) and a null failure, or with the failure text.
 */
class PdfPrintJob(
    private val adapter: PrintDocumentAdapter,
    private val attributes: PrintAttributes,
    private val outputFile: File,
    private val onFinished: (pageCount: Int?, failure: String?) -> Unit,
) {
    private val cancellationSignal = CancellationSignal()
    private var pageCount: Int? = null
    private var hasFinished = false

    fun start() {
        adapter.onStart()
        adapter.onLayout(null, attributes, cancellationSignal, LayoutCallback(), Bundle())
    }

    fun cancel() {
        cancellationSignal.cancel()
        finish(failure = "the print job was cancelled")
    }

    private fun write() {
        val destination = try {
            ParcelFileDescriptor.open(
                outputFile,
                ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE,
            )
        } catch (cannotOpen: java.io.IOException) {
            finish(failure = "the PDF file could not be created (${cannotOpen.message})")
            return
        }
        adapter.onWrite(arrayOf(PageRange.ALL_PAGES), destination, cancellationSignal, WriteCallback(destination))
    }

    private fun finish(failure: String?) {
        if (hasFinished) {
            return
        }
        hasFinished = true
        adapter.onFinish()
        onFinished(pageCount, failure)
    }

    private inner class LayoutCallback : PrintDocumentAdapter.LayoutResultCallback() {
        override fun onLayoutFinished(info: PrintDocumentInfo, changed: Boolean) {
            pageCount = info.pageCount.takeIf { count -> count > 0 }
            write()
        }

        override fun onLayoutFailed(error: CharSequence?) {
            finish(failure = "the page could not be laid out (${error ?: "no reason given"})")
        }

        override fun onLayoutCancelled() {
            finish(failure = "the page layout was cancelled")
        }
    }

    private inner class WriteCallback(private val destination: ParcelFileDescriptor) :
        PrintDocumentAdapter.WriteResultCallback() {
        override fun onWriteFinished(pages: Array<out PageRange>?) {
            destination.close()
            finish(failure = null)
        }

        override fun onWriteFailed(error: CharSequence?) {
            destination.close()
            finish(failure = "the PDF could not be written (${error ?: "no reason given"})")
        }

        override fun onWriteCancelled() {
            destination.close()
            finish(failure = "the PDF writing was cancelled")
        }
    }
}
