package app.jonaki.runtimes.pyodide

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.InputStream

/**
 * A WebView that is never shown, made for one Python run. It must be made
 * and used on the main thread; it works from the foreground service while
 * the app is in the background, because it needs no window.
 */
internal object PythonWebView {
    // The harness gets one JavaScript interface, JonakiBridge, and nothing else from the app.
    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    fun create(context: Context, requests: PyodideRequests, bridge: PythonBridge): WebView {
        val webView = WebView(context.applicationContext)
        webView.settings.javaScriptEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.setGeolocationEnabled(false)
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        // Safe Browsing would send the page's address to Google; the page is the app's own.
        webView.settings.safeBrowsingEnabled = false
        webView.addJavascriptInterface(bridge, PythonBridge.NAME)
        webView.webViewClient = PythonWebViewClient(requests, bridge)
        webView.loadUrl(PyodideRequests.HARNESS_URL)
        return webView
    }

    /** Stops a program that is still running; terminate() ends even an endless loop in the worker. */
    fun stopProgram(webView: WebView) {
        webView.evaluateJavascript("stopRun()", null)
    }
}

private class PythonWebViewClient(
    private val requests: PyodideRequests,
    private val bridge: PythonBridge,
) : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
        when (val answer = requests.resolve(request.url.toString())) {
            is PyodideResponse.Resource -> response(answer.mimeType, harnessResource(answer.name))
            is PyodideResponse.File -> response(answer.mimeType, FileInputStream(answer.file))
            PyodideResponse.Blocked -> WebResourceResponse(
                "text/plain",
                "utf-8",
                FORBIDDEN,
                "Forbidden",
                policyHeaders,
                ByteArrayInputStream(ByteArray(0)),
            )
        }

    private fun response(mimeType: String, body: InputStream): WebResourceResponse =
        WebResourceResponse(mimeType, "utf-8", OK, "OK", policyHeaders, body)

    private fun harnessResource(name: String): InputStream =
        checkNotNull(PythonWebView::class.java.getResourceAsStream("$HARNESS_RESOURCES/$name")) {
            "$name is missing from the APK"
        }

    /** The page never leaves the harness. */
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

    /**
     * Without this, a renderer killed for memory would end the whole app.
     * Returning true keeps the app alive; the run reports the failure.
     */
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        val reason = if (detail.didCrash()) {
            "the WebView renderer crashed"
        } else {
            "Android stopped the WebView renderer, probably to free memory"
        }
        bridge.fail(reason)
        return true
    }

    private companion object {
        const val OK = 200
        const val FORBIDDEN = 403

        // Absolute, because R8 moves classes to other packages in release builds.
        const val HARNESS_RESOURCES = "/app/jonaki/runtimes/pyodide/harness"

        val policyHeaders = mapOf("Content-Security-Policy" to PyodideRequests.CONTENT_POLICY)
    }
}
