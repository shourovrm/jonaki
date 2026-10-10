package app.jonaki.tools.webfetch

import app.jonaki.core.toolapi.OutsideSources
import app.jonaki.core.toolapi.Capability
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import app.jonaki.core.toolapi.await
import app.jonaki.core.toolapi.booleanArgument
import app.jonaki.core.toolapi.intArgument
import java.io.File
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import okio.ByteString.Companion.encodeUtf8

/**
 * Downloads one web page and returns its main text. Pages longer than
 * max_length are saved whole to the thread folder and the model gets the
 * start plus the read_file call that continues it (D-005, D-011).
 *
 * With a [pageRenderer], a page that needs JavaScript is loaded again in a
 * browser engine and read from the HTML its scripts built (D-131): when the
 * model asks with render=true, or when the plain download looks like an
 * empty app shell or a "please enable JavaScript" page.
 *
 * With [pdfSupport], an address that answers with a PDF is read too (D-185):
 * the file goes to the app's private cache, never to the thread folder, and
 * the model gets the text of a few pages and the call that continues them.
 */
class WebFetchTool(
    private val pageRenderer: PageRenderer? = null,
    private val pdfSupport: PdfSupport? = null,
) : Tool {
    override val name: String = "web_fetch"

    override val promptLine: String = if (pdfSupport == null) {
        "web_fetch: read the main text of a web page"
    } else {
        "web_fetch: read the main text of a web page or of a PDF link"
    }

    override val guidelines: List<String> = listOf(
        if (pdfSupport == null) {
            "web_fetch reads HTML and plain-text pages only; it cannot read PDFs, images or videos."
        } else {
            "web_fetch reads HTML pages, plain text and PDFs; it cannot read images or videos. " +
                "A PDF comes a few pages at a time; continue with the offset the notice names."
        },
        "For YouTube videos use youtube_summarize instead.",
    )

    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("url") {
                put("type", "string")
                put("description", "Full http or https address of the page")
            }
            putJsonObject("max_length") {
                put("type", "integer")
                put("minimum", MIN_LENGTH)
                put("maximum", MAX_LENGTH)
                put("description", "Characters to return, default $DEFAULT_LENGTH; the rest is saved to a file")
            }
            if (pageRenderer != null) {
                putJsonObject("render") {
                    put("type", "boolean")
                    put("description", "Run page JavaScript first; auto for empty pages")
                }
            }
            if (pdfSupport != null) {
                putJsonObject("offset") {
                    put("type", "integer")
                    put("description", "For a PDF: first page to show, counting from 1. Default 1.")
                }
                putJsonObject("limit") {
                    put("type", "integer")
                    put("description", "For a PDF: most pages to show. Default $DEFAULT_PAGE_LIMIT.")
                }
            }
        }
        putJsonArray("required") { add("url") }
    }

    override val sideEffect: SideEffect = SideEffect.READ_ONLY

    override fun contactedAddressOf(arguments: JsonObject): String? =
        (arguments["url"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { address -> address.isNotEmpty() }

    override fun outsideContentSourceOf(arguments: JsonObject): String =
        OutsideSources.hostOf((arguments["url"] as? JsonPrimitive)?.contentOrNull)
    override val requiredCapabilities: Set<Capability> = emptySet()
    /** A plain download (about 20 s at worst) plus a render ([RENDER_TIME_LIMIT]) must fit; so must a PDF download. */
    override val timeLimit: Duration = 60.seconds

    override suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput {
        val urlText = (arguments["url"] as? JsonPrimitive)?.contentOrNull?.trim()
        if (urlText.isNullOrEmpty()) {
            return ToolOutput.error("argument url is missing", "Call web_fetch again with a url.")
        }
        val url = urlText.toHttpUrlOrNull()
        if (url == null) {
            return ToolOutput.error("\"$urlText\" is not an http or https address", "Pass a full address starting with https://.")
        }
        val maxLength = ((arguments["max_length"] as? JsonPrimitive)?.intOrNull ?: DEFAULT_LENGTH).coerceIn(MIN_LENGTH, MAX_LENGTH)
        val renderRequested = arguments.booleanArgument("render") == true
        val pdfWindow = PdfWindow(
            firstPage = (arguments.intArgument("offset") ?: 1).coerceAtLeast(1),
            pageLimit = (arguments.intArgument("limit") ?: DEFAULT_PAGE_LIMIT).coerceIn(1, MAX_PAGE_LIMIT),
            maxLength = maxLength,
        )

        val cachedPdf = pdfSupport?.cache?.find(url.toString())
        val pageText = if (pdfSupport != null && cachedPdf != null) {
            // The earlier call for this address was a PDF; later pages come from the copy.
            readPdf(cachedPdf, url.toString(), pdfWindow, pdfSupport)
        } else if (renderRequested) {
            renderOnRequest(url.toString())
        } else {
            download(url.toString(), context, pdfWindow)
        }
        if (pageText.isError) return pageText
        return ToolOutput.success(context.outputLimiter.limit(pageText.text, maxLength, name))
    }

    private suspend fun download(url: String, context: ToolContext, pdfWindow: PdfWindow): ToolOutput {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,application/pdf;q=0.8,*/*;q=0.5")
            .build()
        val response = try {
            context.httpClient.newCall(request).await()
        } catch (networkError: IOException) {
            return ToolOutput.error("could not load $url (${networkError.message})", "Check the address, or search for another source.")
        }
        return response.use { readPage(it, url, pdfWindow) }
    }

    private suspend fun readPage(response: Response, url: String, pdfWindow: PdfWindow): ToolOutput {
        if (!response.isSuccessful) {
            return ToolOutput.error(
                "$url answered HTTP ${response.code}",
                "The page may be gone or blocked; try another result or web_search for a copy.",
            )
        }
        val body = response.body ?: return ToolOutput.error("$url sent an empty answer", "Try another source.")
        val mediaType = body.contentType()
        val subtype = "${mediaType?.type}/${mediaType?.subtype}"
        val isHtml = mediaType == null || subtype in HTML_TYPES
        val isText = mediaType?.type == "text" || subtype in TEXT_TYPES
        if (pdfSupport != null && isPdf(response, subtype)) {
            return downloadPdf(response, url, pdfWindow, pdfSupport)
        }
        if (!isHtml && !isText) {
            return ToolOutput.error("$url is $subtype, not a web page", "web_fetch reads HTML and text pages only.")
        }

        val bytes = withContext(Dispatchers.IO) { readAtMost(response, MAX_DOWNLOAD_BYTES) }
        val charset = mediaType?.charset() ?: Charsets.UTF_8
        val content = String(bytes, charset)
        val finalUrl = response.request.url.toString()

        if (!isHtml) return ToolOutput.success("Source: $finalUrl\n\n${content.trim()}")
        val page = PageTextExtractor.extract(finalUrl, content)
        if (pageRenderer != null && RenderDecision.needsRendering(page, content)) {
            return renderAfterDownload(url, page, finalUrl, pageRenderer)
        }
        if (page.text.isBlank()) {
            return ToolOutput.error(
                "$url has no readable text; it may need JavaScript to show its content",
                "Try web_search for another source on the same topic.",
            )
        }
        return ToolOutput.success(formatPage(page, finalUrl))
    }

    /** Some servers send a PDF as a plain download ("application/octet-stream"), so the first bytes are looked at too. */
    private suspend fun isPdf(response: Response, subtype: String): Boolean {
        if (subtype == PDF_TYPE) return true
        return withContext(Dispatchers.IO) {
            try {
                response.body!!.source().rangeEquals(0, PDF_FIRST_BYTES)
            } catch (networkError: IOException) {
                false
            }
        }
    }

    private suspend fun downloadPdf(response: Response, url: String, pdfWindow: PdfWindow, support: PdfSupport): ToolOutput {
        val tooLarge = ToolOutput.error(
            "the PDF at $url is larger than ${MAX_PDF_BYTES / (1024 * 1024)} MB",
            "web_fetch cannot read it; try web_search for a page that covers the same content.",
        )
        if (response.body!!.contentLength() > MAX_PDF_BYTES) return tooLarge
        val copy = try {
            withContext(Dispatchers.IO) { support.cache.save(url, response.body!!.source(), MAX_PDF_BYTES) }
        } catch (overLimit: PdfTooLargeException) {
            return tooLarge
        } catch (networkError: IOException) {
            return ToolOutput.error("could not download the PDF at $url (${networkError.message})", "Try again, or search for another source.")
        }
        return readPdf(copy, url, pdfWindow, support)
    }

    private suspend fun readPdf(copy: File, url: String, pdfWindow: PdfWindow, support: PdfSupport): ToolOutput {
        val pages = try {
            withContext(Dispatchers.IO) { support.reader.read(copy, pdfWindow.firstPage, pdfWindow.pageLimit) }
        } catch (unreadable: PdfReadException) {
            // A copy that cannot be read would fail the same way for an hour.
            support.cache.remove(url)
            return ToolOutput.error("could not read the PDF at $url (${unreadable.message})", "Try web_search for another source.")
        }
        if (pages.totalPages == 0) {
            return ToolOutput.error("the PDF at $url has no pages", "Try web_search for another source.")
        }
        if (pdfWindow.firstPage > pages.totalPages) {
            return ToolOutput.error(
                "offset ${pdfWindow.firstPage} is past the end; the PDF at $url has ${pages.totalPages} pages",
                "Use an offset between 1 and ${pages.totalPages}.",
            )
        }
        return ToolOutput.success(PdfPageText.fitting(url, pages, pdfWindow.firstPage, pdfWindow.maxLength))
    }

    private suspend fun renderOnRequest(url: String): ToolOutput {
        if (pageRenderer == null) {
            return ToolOutput.error("this app cannot run a page's JavaScript here", "Call web_fetch without render.")
        }
        return try {
            renderPage(url, pageRenderer)
        } catch (renderError: PageRenderException) {
            ToolOutput.error(
                "could not render $url (${renderError.message})",
                "Call web_fetch without render, or web_search for another source.",
            )
        }
    }

    /**
     * The plain download looked empty, so the page is rendered. If rendering
     * fails, whatever text the download had is still better than nothing,
     * and the model learns why it is thin.
     */
    private suspend fun renderAfterDownload(
        url: String,
        downloadedPage: ExtractedPage,
        finalUrl: String,
        renderer: PageRenderer,
    ): ToolOutput {
        try {
            return renderPage(url, renderer)
        } catch (renderError: PageRenderException) {
            if (downloadedPage.text.isBlank()) {
                return ToolOutput.error(
                    "$url needs JavaScript to show its content, and running it failed (${renderError.message})",
                    "Try web_search for another source on the same topic.",
                )
            }
            val notice = "(Running the page's JavaScript failed: ${renderError.message}. This is the page without it.)"
            return ToolOutput.success(formatPage(downloadedPage, finalUrl) + "\n\n" + notice)
        }
    }

    /** Throws [PageRenderException] when the renderer fails. */
    private suspend fun renderPage(url: String, renderer: PageRenderer): ToolOutput {
        val rendered = renderer.render(url, RENDER_TIME_LIMIT)
        val page = PageTextExtractor.extract(rendered.finalUrl, rendered.html)
        if (page.text.isBlank()) {
            return ToolOutput.error(
                "$url has no readable text even after running its JavaScript",
                "Try web_search for another source on the same topic.",
            )
        }
        val title = page.title.ifBlank { rendered.title }
        return ToolOutput.success(formatPage(page.copy(title = title), "${rendered.finalUrl} (rendered)"))
    }

    private fun formatPage(page: ExtractedPage, source: String): String = "# ${page.title}\nSource: $source\n\n${page.text}"

    /** Stops after [limit] bytes so that a huge page cannot exhaust the phone's memory. */
    private fun readAtMost(response: Response, limit: Long): ByteArray {
        val source = response.body!!.source()
        source.request(limit)
        val available = minOf(source.buffer.size, limit)
        return source.buffer.readByteArray(available)
    }

    /** The part of a PDF one call asks for; [maxLength] is in characters. */
    private class PdfWindow(val firstPage: Int, val pageLimit: Int, val maxLength: Int)

    private companion object {
        const val DEFAULT_PAGE_LIMIT = 20
        const val MAX_PAGE_LIMIT = 100
        /** The size read_document takes too (D-046). */
        const val MAX_PDF_BYTES = 25L * 1024 * 1024
        const val PDF_TYPE = "application/pdf"
        val PDF_FIRST_BYTES = "%PDF-".encodeUtf8()
        const val DEFAULT_LENGTH = 10_000
        const val MIN_LENGTH = 500
        const val MAX_LENGTH = 100_000
        const val MAX_DOWNLOAD_BYTES = 5L * 1024 * 1024
        val RENDER_TIME_LIMIT = 25.seconds
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Jonaki/0.1"
        val HTML_TYPES = setOf("text/html", "application/xhtml+xml")
        val TEXT_TYPES = setOf("application/json", "application/xml", "application/rss+xml", "application/atom+xml")
    }
}
