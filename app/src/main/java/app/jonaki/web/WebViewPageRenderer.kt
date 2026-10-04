package app.jonaki.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import app.jonaki.tools.webfetch.PageRenderException
import app.jonaki.tools.webfetch.PageRenderer
import app.jonaki.tools.webfetch.RenderedPage
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Renders a page for web_fetch in a WebView that is never shown (D-131). A
 * fresh WebView is made for each page and destroyed afterwards, and the
 * cookies and storage it left are deleted, so that no site can recognise
 * the next call. It works from the foreground service while the app is in
 * the background, as the Python WebView does, because it needs no window.
 */
class WebViewPageRenderer(private val context: Context) : PageRenderer {
    /**
     * One page at a time. Cookies and storage are shared by every WebView in
     * the app, so clearing them after one page would break a second page
     * that is still loading.
     */
    private val onePageAtATime = Mutex()

    override suspend fun render(url: String, timeLimit: Duration): RenderedPage =
        onePageAtATime.withLock {
            withContext(Dispatchers.Main) {
                renderOnMainThread(url, timeLimit)
            }
        }

    private suspend fun renderOnMainThread(url: String, timeLimit: Duration): RenderedPage {
        val webView = createWebView()
        val client = RenderClient()
        webView.webViewClient = client
        try {
            webView.loadUrl(url)
            withTimeoutOrNull(timeLimit - CAPTURE_ALLOWANCE) {
                client.pageFinished.await()
                waitForSteadyText(webView, client)
            }
            client.failure?.let { reason -> throw PageRenderException(reason) }
            // A page that is still loading trackers when time runs out usually has its text already.
            val html = withTimeoutOrNull(CAPTURE_ALLOWANCE) { webView.evaluateToText(OUTER_HTML_SCRIPT) }
                ?: throw PageRenderException("the page did not answer within $timeLimit")
            client.failure?.let { reason -> throw PageRenderException(reason) }
            return RenderedPage(
                finalUrl = webView.url ?: url,
                title = webView.title.orEmpty(),
                html = html,
            )
        } finally {
            destroy(webView)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        val webView = try {
            WebView(context.applicationContext)
        } catch (missingWebView: RuntimeException) {
            // Thrown while the system WebView is missing, disabled or being updated.
            throw PageRenderException("the phone's WebView is not available (${missingWebView.message})")
        }
        val settings = webView.settings
        settings.javaScriptEnabled = true
        // Many web apps stop with an error without localStorage; it is deleted after each page.
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setGeolocationEnabled(false)
        settings.mediaPlaybackRequiresUserGesture = true
        // Only the text is read, so images would cost data and time for nothing.
        settings.blockNetworkImage = true
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        webView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        // A phone-sized viewport, so that layouts that depend on the width build their content.
        webView.measure(
            View.MeasureSpec.makeMeasureSpec(VIEWPORT_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(VIEWPORT_HEIGHT, View.MeasureSpec.EXACTLY),
        )
        webView.layout(0, 0, VIEWPORT_WIDTH, VIEWPORT_HEIGHT)
        return webView
    }

    /**
     * Scripts often fill the page after onPageFinished, and many first show an
     * empty list ("0 results") while their data request is out. The text
     * counts as ready once it has kept the same length for [STEADY_FOR] and at
     * least [MINIMUM_WAIT] has passed; a check every [SETTLE_STEP] measures it.
     * The phone check of 2026-10-03 caught hn.algolia.com and the Pokédex in
     * that empty state with a 0.5 s rule.
     */
    private suspend fun waitForSteadyText(webView: WebView, client: RenderClient) {
        var previousLength = -1
        var steadyChecks = 0
        var checks = 0
        val checksForSteady = (STEADY_FOR / SETTLE_STEP).toInt()
        val minimumChecks = (MINIMUM_WAIT / SETTLE_STEP).toInt()
        while (client.failure == null) {
            delay(SETTLE_STEP)
            checks += 1
            val length = webView.evaluateToText(TEXT_LENGTH_SCRIPT).toIntOrNull() ?: 0
            steadyChecks = if (length > 0 && length == previousLength) steadyChecks + 1 else 0
            previousLength = length
            if (steadyChecks >= checksForSteady && checks >= minimumChecks) {
                return
            }
        }
    }

    private fun destroy(webView: WebView) {
        webView.stopLoading()
        webView.clearCache(true)
        webView.destroy()
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
    }

    private companion object {
        val SETTLE_STEP = 500.milliseconds
        val STEADY_FOR = 1_500.milliseconds
        val MINIMUM_WAIT = 2_500.milliseconds
        val CAPTURE_ALLOWANCE = 3.seconds
        const val VIEWPORT_WIDTH = 1080
        const val VIEWPORT_HEIGHT = 2400

        const val TEXT_LENGTH_SCRIPT = "document.body ? document.body.innerText.length : 0"

        // Capped like web_fetch's plain download, so that a huge page cannot exhaust the phone's memory.
        const val OUTER_HTML_SCRIPT =
            "document.documentElement ? document.documentElement.outerHTML.slice(0, 5242880) : ''"
    }
}

/** Watches one page load and records why it failed, if it did. */
private class RenderClient : WebViewClient() {
    val pageFinished = CompletableDeferred<Unit>()

    /** Why the page could not be read; null while all is well. */
    var failure: String? = null
        private set

    private var mainFrameStatus: Int? = null

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        mainFrameStatus = null
    }

    override fun onPageFinished(view: WebView, url: String) {
        mainFrameStatus?.let { status -> fail("the page answered HTTP $status") }
        pageFinished.complete(Unit)
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) {
            fail("the page did not load (${error.description})")
        }
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
        if (request.isForMainFrame && response.statusCode >= 400) {
            mainFrameStatus = response.statusCode
        }
    }

    /** Redirects to other web pages are followed; intent:, market: and other schemes are not. */
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val scheme = request.url.scheme?.lowercase()
        return scheme != "http" && scheme != "https"
    }

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
