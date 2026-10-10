package app.jonaki.feature.chat

import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What loading one SVG file gave. */
private sealed interface SvgLoad {
    data object Loading : SvgLoad

    /** The file is gone, too large or unreadable: the "Image missing" tile. */
    data object Missing : SvgLoad

    class Ready(val html: String) : SvgLoad
}

/**
 * One SVG, drawn by a WebView that can do nothing but draw it (see [SvgPage]
 * and [lockedDownSvgWebView]). It fills the size its caller gives it and is
 * created once for the item that shows it; it is destroyed when the item
 * leaves the list. [onClick], when given, is caught by a layer over the
 * WebView, since a WebView takes touches itself; the full-size view passes
 * none so that pinch zoom reaches the WebView.
 *
 * A WebView per card costs memory while it is on screen. A bitmap cache
 * would be lighter for a long thread, but a WebView cannot be captured
 * reliably without a screen to check on, so this stays the plain version.
 */
@Composable
internal fun SvgView(path: String, allowsZoom: Boolean, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val images = LocalChatImages.current
    val load by produceState<SvgLoad>(SvgLoad.Loading, path, allowsZoom) {
        val text = images?.svgText(path)
        value = if (text == null) {
            SvgLoad.Missing
        } else {
            SvgLoad.Ready(withContext(Dispatchers.Default) { SvgPage.html(text, allowsZoom) })
        }
    }
    val openDescription = stringResource(R.string.chat_image_open)
    val clickModifier = if (onClick == null) Modifier else Modifier.clickable(onClickLabel = openDescription, onClick = onClick)
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        when (val shown = load) {
            is SvgLoad.Ready -> {
                AndroidView(
                    factory = { context -> lockedDownSvgWebView(context, allowsZoom) },
                    update = { view -> showPage(view, shown.html) },
                    onRelease = { view -> releaseWebView(view) },
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().then(clickModifier))
            }
            SvgLoad.Missing -> Text(
                stringResource(R.string.chat_image_missing),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(6.dp),
            )
            SvgLoad.Loading -> CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/** Loads [html] once; the same page again (a recomposition) does nothing. */
private fun showPage(view: View, html: String) {
    val webView = view as? WebView ?: return
    if (webView.tag == html) {
        return
    }
    webView.tag = html
    // A null base address gives the page no origin and no way to name a file or a site.
    webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
}

private fun releaseWebView(view: View) {
    val webView = view as? WebView ?: return
    webView.stopLoading()
    webView.destroy()
}

/**
 * A WebView that has no JavaScript, no file or content access, no storage and
 * no network, and that answers every request except for the page's own data
 * image with an empty refusal. A phone without a WebView gets a plain empty
 * view instead of a crash.
 */
private fun lockedDownSvgWebView(context: Context, allowsZoom: Boolean): View {
    val webView = try {
        WebView(context)
    } catch (missingWebView: RuntimeException) {
        return View(context)
    }
    with(webView.settings) {
        javaScriptEnabled = false
        allowFileAccess = false
        allowContentAccess = false
        // Both are off by default from Android 11, but the app's minSdk is 26.
        allowFileAccessFromFileURLs = false
        allowUniversalAccessFromFileURLs = false
        blockNetworkLoads = true
        domStorageEnabled = false
        setGeolocationEnabled(false)
        setSupportMultipleWindows(false)
        cacheMode = WebSettings.LOAD_NO_CACHE
        mediaPlaybackRequiresUserGesture = true
        saveFormData = false
        setSupportZoom(allowsZoom)
        builtInZoomControls = allowsZoom
        displayZoomControls = false
        useWideViewPort = true
        loadWithOverviewMode = true
    }
    webView.webViewClient = BlockingSvgClient
    webView.setBackgroundColor(AndroidColor.TRANSPARENT)
    webView.isVerticalScrollBarEnabled = false
    webView.isHorizontalScrollBarEnabled = false
    webView.overScrollMode = View.OVER_SCROLL_NEVER
    return webView
}

/** Which addresses the SVG page may load: its own `data:` image and the blank page; nothing else. */
internal object SvgRequestPolicy {
    fun allows(url: String): Boolean {
        val scheme = url.substringBefore(':', missingDelimiterValue = "").lowercase()
        return scheme == "data" || scheme == "about"
    }
}

private object BlockingSvgClient : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        if (SvgRequestPolicy.allows(request.url.toString())) {
            return null
        }
        return WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }

    /** A link inside the picture never opens: the page stays what it is. */
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
}
