package app.jonaki.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.pdf.PdfRenderer as FrameworkPdfRenderer
import android.os.ParcelFileDescriptor
import android.print.PdfPrintJob
import android.print.PrintAttributes
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import app.jonaki.feature.artifact.ArtifactRequests
import app.jonaki.feature.artifact.ArtifactWebViewClient
import app.jonaki.tools.exportpdf.PdfPageSize
import app.jonaki.tools.exportpdf.PdfRenderException
import app.jonaki.tools.exportpdf.PdfRenderer
import app.jonaki.tools.exportpdf.RenderedPdf
import java.io.File
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Makes the PDF for export_pdf: loads the artifact in a WebView that is never
 * shown, with the artifact viewer's wall (scripts run, requests answered
 * from the thread's artifacts/ folder only, no network), waits until its
 * scripts have drawn, then lets the WebView's print engine write the PDF
 * with no print dialog. The text stays selectable, because the print engine
 * writes real PDF text, not a picture of the screen.
 */
class WebViewPdfRenderer(private val context: Context) : PdfRenderer {
    /** One PDF at a time; a long report takes tens of megabytes while it is laid out. */
    private val onePdfAtATime = Mutex()

    override suspend fun render(
        threadFolder: File,
        htmlPath: String,
        outputFile: File,
        pageSize: PdfPageSize,
        timeLimit: Duration,
    ): RenderedPdf =
        onePdfAtATime.withLock {
            withContext(Dispatchers.Main) {
                renderOnMainThread(threadFolder, htmlPath, outputFile, pageSize, timeLimit)
            }
        }

    private suspend fun renderOnMainThread(
        threadFolder: File,
        htmlPath: String,
        outputFile: File,
        pageSize: PdfPageSize,
        timeLimit: Duration,
    ): RenderedPdf {
        val webView = createWebView(pageSize)
        val client = PdfPageClient(ArtifactRequests(threadFolder))
        webView.webViewClient = client
        try {
            webView.loadUrl(ArtifactRequests.urlOf(htmlPath))
            val pageCount = withTimeoutOrNull(timeLimit) {
                client.pageFinished.await()
                client.failure?.let { reason -> throw PdfRenderException(reason) }
                waitForSteadyPage(webView, client)
                client.failure?.let { reason -> throw PdfRenderException(reason) }
                printToFile(webView, outputFile, pageSize)
            }
            if (pageCount == null) {
                throw PdfRenderException("the page did not finish within $timeLimit; it may load too slowly or run a script that never ends")
            }
            // pageCount is the reported count, or -1 when the print engine gave none.
            return RenderedPdf(pageCount.takeIf { count -> count > 0 } ?: countPages(outputFile))
        } finally {
            webView.stopLoading()
            webView.destroy()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(pageSize: PdfPageSize): WebView {
        val webView = try {
            WebView(context.applicationContext)
        } catch (missingWebView: RuntimeException) {
            // Thrown while the system WebView is missing, disabled or being updated.
            throw PdfRenderException("the phone's WebView is not available (${missingWebView.message})")
        }
        // The same settings as the artifact viewer: scripts for charts, no file access, no bridge into the app.
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setGeolocationEnabled(false)
        settings.mediaPlaybackRequiresUserGesture = true
        webView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        // The page is laid out as wide as the paper, so that charts drawn by scripts fit the printed page.
        val density = context.resources.displayMetrics.density
        val widthPixels = (pageSize.printableWidthCssPixels * density).toInt()
        val heightPixels = (widthPixels * pageSize.heightToWidth).toInt()
        webView.measure(
            View.MeasureSpec.makeMeasureSpec(widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPixels, View.MeasureSpec.EXACTLY),
        )
        webView.layout(0, 0, widthPixels, heightPixels)
        return webView
    }

    /**
     * Scripts draw charts after onPageFinished and Chart.js animates for
     * about a second, so the page counts as ready once its text length, its
     * number of canvases and SVGs and its height have stayed the same for
     * [STEADY_FOR] and at least [MINIMUM_WAIT] has passed.
     */
    private suspend fun waitForSteadyPage(webView: WebView, client: PdfPageClient) {
        var previousFingerprint = ""
        var steadyChecks = 0
        var checks = 0
        val checksForSteady = (STEADY_FOR / SETTLE_STEP).toInt()
        val minimumChecks = (MINIMUM_WAIT / SETTLE_STEP).toInt()
        while (client.failure == null) {
            delay(SETTLE_STEP)
            checks += 1
            val fingerprint = webView.evaluateToText(PAGE_FINGERPRINT_SCRIPT)
            steadyChecks = if (fingerprint == previousFingerprint) steadyChecks + 1 else 0
            previousFingerprint = fingerprint
            if (steadyChecks >= checksForSteady && checks >= minimumChecks) {
                return
            }
        }
    }

    /** Returns the page count the print engine reported, or -1. */
    private suspend fun printToFile(webView: WebView, outputFile: File, pageSize: PdfPageSize): Int =
        suspendCancellableCoroutine { continuation ->
            val job = PdfPrintJob(
                adapter = webView.createPrintDocumentAdapter(outputFile.nameWithoutExtension),
                attributes = printAttributesFor(pageSize),
                outputFile = outputFile,
            ) { pageCount, failure ->
                if (!continuation.isActive) {
                    return@PdfPrintJob
                }
                if (failure == null) {
                    continuation.resume(pageCount ?: -1)
                } else {
                    continuation.resumeWith(Result.failure(PdfRenderException(failure)))
                }
            }
            continuation.invokeOnCancellation { job.cancel() }
            job.start()
        }

    private fun printAttributesFor(pageSize: PdfPageSize): PrintAttributes {
        val mediaSize = when (pageSize) {
            PdfPageSize.A4 -> PrintAttributes.MediaSize.ISO_A4
            PdfPageSize.LETTER -> PrintAttributes.MediaSize.NA_LETTER
            // 16 by 9 inches in thousandths of an inch, the size the slides skill declares in @page.
            PdfPageSize.SLIDES -> PrintAttributes.MediaSize("jonaki_slides", "Slides", 16_000, 9_000)
        }
        val margins = if (pageSize == PdfPageSize.SLIDES) {
            PrintAttributes.Margins.NO_MARGINS
        } else {
            PrintAttributes.Margins(PAPER_MARGIN_MILS, PAPER_MARGIN_MILS, PAPER_MARGIN_MILS, PAPER_MARGIN_MILS)
        }
        return PrintAttributes.Builder()
            .setMediaSize(mediaSize)
            .setResolution(PrintAttributes.Resolution("pdf", "PDF", PRINT_DPI, PRINT_DPI))
            .setMinMargins(margins)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build()
    }

    /** The count from the finished file, with the framework's own PDF reader; null if the file cannot be read. */
    private fun countPages(pdfFile: File): Int? =
        try {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                FrameworkPdfRenderer(descriptor).use { reader -> reader.pageCount }
            }
        } catch (unreadable: java.io.IOException) {
            null
        } catch (unreadable: SecurityException) {
            null
        }

    private val PdfPageSize.printableWidthCssPixels: Int
        get() = when (this) {
            PdfPageSize.A4 -> A4_PRINTABLE_WIDTH
            PdfPageSize.LETTER -> LETTER_PRINTABLE_WIDTH
            PdfPageSize.SLIDES -> SLIDES_WIDTH
        }

    private val PdfPageSize.heightToWidth: Double
        get() = if (this == PdfPageSize.SLIDES) 9.0 / 16.0 else 1.4

    private companion object {
        val SETTLE_STEP = 250.milliseconds
        val STEADY_FOR = 1_000.milliseconds
        val MINIMUM_WAIT = 2_000.milliseconds

        const val PAPER_MARGIN_MILS = 400
        const val PRINT_DPI = 300

        // Paper width minus both margins, in CSS pixels (96 per inch): 7.47 in, 7.7 in and 16 in.
        const val A4_PRINTABLE_WIDTH = 717
        const val LETTER_PRINTABLE_WIDTH = 739
        const val SLIDES_WIDTH = 1536

        const val PAGE_FINGERPRINT_SCRIPT =
            "document.body ? [document.body.innerText.length, document.querySelectorAll('canvas,svg').length, " +
                "document.documentElement.scrollHeight].join('|') : ''"
    }
}

/**
 * The artifact viewer's request wall, plus a record of why the page failed.
 * No navigation is followed, so a link or redirect cannot take the export
 * off the artifact.
 */
private class PdfPageClient(requests: ArtifactRequests) : ArtifactWebViewClient(requests) {
    val pageFinished = CompletableDeferred<Unit>()

    /** Why the page could not be printed; null while all is well. */
    var failure: String? = null
        private set

    override fun onPageFinished(view: WebView, url: String) {
        pageFinished.complete(Unit)
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) {
            fail("the page did not load (${error.description})")
        }
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
        if (request.isForMainFrame && response.statusCode >= 400) {
            fail("the page answered HTTP ${response.statusCode}; only files in artifacts/ can be exported")
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

    /** Returning true keeps the app alive when Android stops the renderer; the call reports the failure. */
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        if (detail.didCrash()) {
            fail("the WebView renderer crashed")
        } else {
            fail("Android stopped the WebView renderer, probably to free memory")
        }
        return true
    }

    private fun fail(reason: String) {
        if (failure == null) failure = reason
        pageFinished.complete(Unit)
    }
}
