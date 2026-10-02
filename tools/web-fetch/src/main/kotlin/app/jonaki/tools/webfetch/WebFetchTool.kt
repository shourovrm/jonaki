package app.jonaki.tools.webfetch

import app.jonaki.core.toolapi.Capability
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import app.jonaki.core.toolapi.await
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

/**
 * Downloads one web page and returns its main text. Pages longer than
 * max_length are saved whole to the thread folder and the model gets the
 * start plus the read_file call that continues it (D-005, D-011).
 */
class WebFetchTool : Tool {
    override val name: String = "web_fetch"

    override val promptLine: String = "web_fetch: read the main text of a web page"

    override val guidelines: List<String> = listOf(
        "web_fetch reads HTML and plain-text pages only; it cannot read PDFs, images or videos.",
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
        }
        putJsonArray("required") { add("url") }
    }

    override val sideEffect: SideEffect = SideEffect.READ_ONLY
    override val requiredCapabilities: Set<Capability> = emptySet()
    override val timeLimit: Duration = 30.seconds

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

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.5")
            .build()
        val response = try {
            context.httpClient.newCall(request).await()
        } catch (networkError: IOException) {
            return ToolOutput.error("could not load $url (${networkError.message})", "Check the address, or search for another source.")
        }
        val pageText = response.use { readPage(it, url.toString()) }
        if (pageText.isError) return pageText
        return ToolOutput.success(context.outputLimiter.limit(pageText.text, maxLength, name))
    }

    private suspend fun readPage(response: Response, url: String): ToolOutput {
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
        if (!isHtml && !isText) {
            return ToolOutput.error("$url is $subtype, not a web page", "web_fetch reads HTML and text pages only.")
        }

        val bytes = withContext(Dispatchers.IO) { readAtMost(response, MAX_DOWNLOAD_BYTES) }
        val charset = mediaType?.charset() ?: Charsets.UTF_8
        val content = String(bytes, charset)
        val finalUrl = response.request.url.toString()

        if (!isHtml) return ToolOutput.success("Source: $finalUrl\n\n${content.trim()}")
        val page = PageTextExtractor.extract(finalUrl, content)
        if (page.text.isBlank()) {
            return ToolOutput.error(
                "$url has no readable text; it may need JavaScript to show its content",
                "Try web_search for another source on the same topic.",
            )
        }
        return ToolOutput.success("# ${page.title}\nSource: $finalUrl\n\n${page.text}")
    }

    /** Stops after [limit] bytes so that a huge page cannot exhaust the phone's memory. */
    private fun readAtMost(response: Response, limit: Long): ByteArray {
        val source = response.body!!.source()
        source.request(limit)
        val available = minOf(source.buffer.size, limit)
        return source.buffer.readByteArray(available)
    }

    private companion object {
        const val DEFAULT_LENGTH = 10_000
        const val MIN_LENGTH = 500
        const val MAX_LENGTH = 100_000
        const val MAX_DOWNLOAD_BYTES = 5L * 1024 * 1024
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Jonaki/0.1"
        val HTML_TYPES = setOf("text/html", "application/xhtml+xml")
        val TEXT_TYPES = setOf("application/json", "application/xml", "application/rss+xml", "application/atom+xml")
    }
}
