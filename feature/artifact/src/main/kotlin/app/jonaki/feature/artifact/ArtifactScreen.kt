package app.jonaki.feature.artifact

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.jonaki.core.ui.JonakiIcons
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream

/** One artifact and its saved versions; [versionNumbers] oldest first. */
@Immutable
data class ArtifactUiState(
    val threadFolder: File,
    /** Relative to the thread folder, for example artifacts/report.html. */
    val path: String,
    val versionNumbers: List<Int>,
    val fileExists: Boolean,
)

/**
 * Shows an HTML artifact in a WebView that cannot reach the network or the
 * phone's files (D-018, D-047). Scripts run, so decks and Chart.js charts
 * work, but the page has no bridge into the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtifactScreen(state: ArtifactUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    // Null shows the current file; a number shows that saved version.
    var shownVersion by remember(state.path) { mutableStateOf<Int?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val title = state.path.substringAfterLast('/')
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.artifact_back))
                    }
                },
                actions = {
                    if (state.versionNumbers.size > 1) {
                        VersionPicker(state.versionNumbers, shownVersion, onSelect = { shownVersion = it })
                    }
                    val printJobName = title.removeSuffix(".html")
                    IconButton(
                        onClick = { webView?.let { view -> print(view, printJobName) } },
                        enabled = state.fileExists,
                    ) {
                        Icon(JonakiIcons.Print, contentDescription = stringResource(R.string.artifact_print))
                    }
                },
            )
        },
    ) { padding ->
        if (!state.fileExists) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.artifact_missing), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        val shownPath = pathOf(state.path, shownVersion)
        val requests = remember(state.threadFolder) { ArtifactRequests(state.threadFolder) }
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory = { context -> lockedDownWebView(context, requests).also { view -> webView = view } },
            update = { view ->
                val url = ArtifactRequests.urlOf(shownPath)
                if (view.url != url) {
                    view.loadUrl(url)
                }
            },
            onRelease = { view -> view.destroy() },
        )
    }
}

@Composable
private fun VersionPicker(versionNumbers: List<Int>, shownVersion: Int?, onSelect: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = if (shownVersion == null) {
        stringResource(R.string.artifact_current)
    } else {
        stringResource(R.string.artifact_version, shownVersion)
    }
    Box {
        TextButton(onClick = { open = true }) {
            Text(label)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.artifact_versions))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.artifact_current)) }, onClick = {
                onSelect(null)
                open = false
            })
            for (number in versionNumbers.asReversed()) {
                DropdownMenuItem(text = { Text(stringResource(R.string.artifact_version, number)) }, onClick = {
                    onSelect(number)
                    open = false
                })
            }
        }
    }
}

/** artifacts/report.html, or its saved copy artifacts/.versions/report/v2.html. */
internal fun pathOf(artifactPath: String, version: Int?): String {
    if (version == null) {
        return artifactPath
    }
    val insideArtifacts = artifactPath.removePrefix("artifacts/").removeSuffix(".html")
    return "artifacts/.versions/$insideArtifacts/v$version.html"
}

// JavaScript is needed for decks and charts; the page gets no JavaScript interface into the app.
@SuppressLint("SetJavaScriptEnabled")
private fun lockedDownWebView(context: Context, requests: ArtifactRequests): WebView =
    WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setGeolocationEnabled(false)
        settings.mediaPlaybackRequiresUserGesture = true
        webViewClient = ArtifactWebViewClient(requests)
    }

private class ArtifactWebViewClient(private val requests: ArtifactRequests) : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
        when (val answer = requests.resolve(request.url.toString())) {
            is ArtifactResponse.File -> WebResourceResponse(
                answer.mimeType,
                "utf-8",
                200,
                "OK",
                answer.headers,
                FileInputStream(answer.file),
            )
            ArtifactResponse.ChartLibrary -> WebResourceResponse(
                "text/javascript",
                "utf-8",
                view.context.assets.open(CHART_LIBRARY_ASSET),
            )
            ArtifactResponse.Blocked -> WebResourceResponse(
                "text/plain",
                "utf-8",
                403,
                "Forbidden",
                emptyMap(),
                ByteArrayInputStream(ByteArray(0)),
            )
        }

    /** A tapped web link leaves the viewer for the browser, so the page itself stays offline. */
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url
        if (url.host == ArtifactRequests.HOST) {
            return false
        }
        if (url.scheme == "http" || url.scheme == "https") {
            val browser = Intent(Intent.ACTION_VIEW, Uri.parse(url.toString())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { view.context.startActivity(browser) }
        }
        return true
    }

    private companion object {
        const val CHART_LIBRARY_ASSET = "jonaki-lib/chart.umd.min.js"
    }
}

/** Android's print dialog, which also offers "Save as PDF". */
private fun print(webView: WebView, jobName: String) {
    val printManager = webView.context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    printManager.print(jobName, webView.createPrintDocumentAdapter(jobName), PrintAttributes.Builder().build())
}
