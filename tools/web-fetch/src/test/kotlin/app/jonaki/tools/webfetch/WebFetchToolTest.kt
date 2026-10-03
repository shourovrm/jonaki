package app.jonaki.tools.webfetch

import app.jonaki.core.toolapi.ToolContext
import java.io.File
import java.nio.file.Files
import kotlin.time.Duration
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WebFetchToolTest {
    private lateinit var server: MockWebServer
    private val threadFolder: File = Files.createTempDirectory("thread").toFile()
    private val context = ToolContext(threadFolder, OkHttpClient())
    private val tool = WebFetchTool()

    private val paragraph = "Dhaka's first underground metro line runs from the airport to Kamalapur. " +
        "Utility relocation finished in May 2025 and tunnel boring is expected to start next year. "

    private val articlePage = """
        <html><head><title>MRT Line 1 progress | News</title><script>var tracking = 1;</script></head>
        <body>
          <nav><a href="/">Home</a> <a href="/sports">Sports</a></nav>
          <article>
            <h1>MRT Line 1 progress</h1>
            <p>${paragraph.repeat(3)}</p>
            <h2>Next steps</h2>
            <ul><li>Tunnel boring</li><li>Station works</li></ul>
            <p>Read the <a href="https://example.com/report.pdf">project report</a> for details. ${paragraph}</p>
          </article>
          <footer>Copyright</footer>
        </body></html>
    """.trimIndent()

    private fun arguments(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun returnsTheArticleWithStructureAndWithoutScripts() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody(articlePage))
        val url = server.url("/news/mrt").toString()

        val output = tool.run(arguments("""{"url":"$url"}"""), context)

        assertFalse(output.text, output.isError)
        assertTrue(output.text, output.text.startsWith("# MRT Line 1 progress"))
        assertTrue(output.text.contains("Source: $url"))
        assertTrue(output.text, output.text.contains("## Next steps"))
        assertTrue(output.text.contains("- Tunnel boring"))
        assertTrue(output.text.contains("project report (https://example.com/report.pdf)"))
        assertFalse(output.text.contains("tracking"))
        assertTrue(server.takeRequest().getHeader("User-Agent")!!.contains("Jonaki"))
    }

    @Test
    fun longPagesAreCutAndSavedToTheThreadFolder() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody(articlePage))
        val url = server.url("/news/mrt").toString()

        val output = tool.run(arguments("""{"url":"$url","max_length":500}"""), context)

        assertFalse(output.isError)
        assertTrue(output.text, output.text.contains("Output truncated"))
        val spillFolder = File(threadFolder, "work/tool-output")
        assertEquals(1, spillFolder.listFiles()!!.count { it.name.startsWith("web_fetch") })
    }

    @Test
    fun plainTextIsReturnedAsIs() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/plain").setBody("line one\nline two\n"))

        val output = tool.run(arguments("""{"url":"${server.url("/notes.txt")}"}"""), context)

        assertTrue(output.text.endsWith("line one\nline two"))
    }

    @Test
    fun pdfIsRefusedWithAReason() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/pdf").setBody("%PDF-1.7"))

        val output = tool.run(arguments("""{"url":"${server.url("/report.pdf")}"}"""), context)

        assertTrue(output.isError)
        assertTrue(output.text.contains("application/pdf"))
    }

    @Test
    fun missingPageIsAnError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))

        val output = tool.run(arguments("""{"url":"${server.url("/gone")}"}"""), context)

        assertTrue(output.isError)
        assertTrue(output.text.contains("HTTP 404"))
    }

    @Test
    fun nonHttpAddressIsAnError() = runBlocking {
        val output = tool.run(arguments("""{"url":"file:///etc/passwd"}"""), context)

        assertTrue(output.isError)
        assertTrue(output.text.contains("not an http or https address"))
    }

    @Test
    fun javascriptOnlyPageIsAnError() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html><body><div id=root></div><script>app()</script></body></html>"))

        val output = tool.run(arguments("""{"url":"${server.url("/app")}"}"""), context)

        assertTrue(output.isError)
        assertTrue(output.text.contains("JavaScript"))
    }

    /** Hands back [html] as the rendered page, or fails with [failure]; records the addresses asked for. */
    private class FakePageRenderer(
        private val html: String = "",
        private val failure: String? = null,
    ) : PageRenderer {
        val renderedUrls = mutableListOf<String>()
        var lastTimeLimit: Duration? = null

        override suspend fun render(url: String, timeLimit: Duration): RenderedPage {
            renderedUrls += url
            lastTimeLimit = timeLimit
            if (failure != null) throw PageRenderException(failure)
            return RenderedPage(finalUrl = "$url#rendered", title = "Rendered title", html = html)
        }
    }

    private val appShell = "<html><head><title>Shop</title></head><body><div id=root></div><script src=/app.js></script></body></html>"

    private val renderedShop = """
        <html><head><title>Laptops | Shop</title></head><body><main>
          <h1>Laptops</h1>
          <ul><li>Asus Vivobook 15, 16 GB, Tk 72,000</li><li>Lenovo IdeaPad Slim 3, 8 GB, Tk 58,500</li></ul>
        </main></body></html>
    """.trimIndent()

    @Test
    fun appShellIsRenderedAutomatically() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody(appShell))
        val renderer = FakePageRenderer(html = renderedShop)
        val url = server.url("/laptops").toString()

        val output = WebFetchTool(renderer).run(arguments("""{"url":"$url"}"""), context)

        assertFalse(output.text, output.isError)
        assertEquals(listOf(url), renderer.renderedUrls)
        assertTrue(output.text, output.text.contains("- Asus Vivobook 15, 16 GB, Tk 72,000"))
        assertTrue(output.text, output.text.contains("Source: $url#rendered (rendered)"))
    }

    @Test
    fun ordinaryArticleIsNotRendered() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody(articlePage))
        val renderer = FakePageRenderer(html = renderedShop)

        val output = WebFetchTool(renderer).run(arguments("""{"url":"${server.url("/news/mrt")}"}"""), context)

        assertFalse(output.isError)
        assertTrue(renderer.renderedUrls.isEmpty())
        assertTrue(output.text.startsWith("# MRT Line 1 progress"))
    }

    @Test
    fun renderArgumentSkipsTheDownload() = runBlocking {
        val renderer = FakePageRenderer(html = renderedShop)
        val url = server.url("/laptops").toString()

        val output = WebFetchTool(renderer).run(arguments("""{"url":"$url","render":true}"""), context)

        assertFalse(output.isError)
        assertEquals(listOf(url), renderer.renderedUrls)
        assertEquals(0, server.requestCount)
        assertTrue(output.text, output.text.startsWith("# Laptops"))
    }

    @Test
    fun renderArgumentAcceptsAString() = runBlocking {
        val renderer = FakePageRenderer(html = renderedShop)

        WebFetchTool(renderer).run(arguments("""{"url":"${server.url("/laptops")}","render":"true"}"""), context)

        assertEquals(1, renderer.renderedUrls.size)
    }

    @Test
    fun renderIsGivenATimeLimitShorterThanTheTools() = runBlocking {
        val renderer = FakePageRenderer(html = renderedShop)
        val tool = WebFetchTool(renderer)

        tool.run(arguments("""{"url":"${server.url("/laptops")}","render":true}"""), context)

        assertTrue(renderer.lastTimeLimit!! < tool.timeLimit)
    }

    @Test
    fun longRenderedPageIsCutAndSaved() = runBlocking {
        val longPage = "<html><body><article><h1>Prices</h1><p>${paragraph.repeat(20)}</p></article></body></html>"
        val renderer = FakePageRenderer(html = longPage)

        val output = WebFetchTool(renderer).run(arguments("""{"url":"${server.url("/prices")}","render":true,"max_length":500}"""), context)

        assertFalse(output.isError)
        assertTrue(output.text, output.text.contains("Output truncated"))
    }

    @Test
    fun failedRenderOfAnEmptyShellIsAnErrorThatSaysWhy() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody(appShell))
        val renderer = FakePageRenderer(failure = "the page did not load within 25s")

        val output = WebFetchTool(renderer).run(arguments("""{"url":"${server.url("/laptops")}"}"""), context)

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("needs JavaScript"))
        assertTrue(output.text, output.text.contains("did not load within 25s"))
        assertTrue(output.text, output.text.contains("web_search"))
    }

    @Test
    fun failedRenderKeepsTheDownloadedTextWithANotice() = runBlocking {
        val warningPage = "<html><head><title>Bank</title></head><body><p>Please enable JavaScript to use online banking.</p></body></html>"
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody(warningPage))
        val renderer = FakePageRenderer(failure = "net::ERR_NAME_NOT_RESOLVED")

        val output = WebFetchTool(renderer).run(arguments("""{"url":"${server.url("/bank")}"}"""), context)

        assertFalse(output.isError)
        assertEquals(1, renderer.renderedUrls.size)
        assertTrue(output.text, output.text.contains("Please enable JavaScript to use online banking."))
        assertTrue(output.text, output.text.contains("Running the page's JavaScript failed: net::ERR_NAME_NOT_RESOLVED"))
    }

    @Test
    fun failedRequestedRenderIsAnError() = runBlocking {
        val renderer = FakePageRenderer(failure = "the WebView renderer crashed")

        val output = WebFetchTool(renderer).run(arguments("""{"url":"${server.url("/x")}","render":true}"""), context)

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("could not render"))
        assertTrue(output.text, output.text.contains("without render"))
    }

    @Test
    fun renderedPageWithoutTextIsAnError() = runBlocking {
        val renderer = FakePageRenderer(html = "<html><body><div id=root></div></body></html>")

        val output = WebFetchTool(renderer).run(arguments("""{"url":"${server.url("/x")}","render":true}"""), context)

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("even after running its JavaScript"))
    }

    @Test
    fun renderWithoutARendererIsAnError() = runBlocking {
        val output = tool.run(arguments("""{"url":"${server.url("/x")}","render":true}"""), context)

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("without render"))
    }

    @Test
    fun renderArgumentIsOfferedOnlyWithARenderer() {
        val withRenderer = WebFetchTool(FakePageRenderer()).parameterSchema["properties"]!!.jsonObject
        val withoutRenderer = tool.parameterSchema["properties"]!!.jsonObject

        assertTrue("render" in withRenderer)
        assertNull(withoutRenderer["render"])
    }
}
