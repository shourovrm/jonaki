package app.jonaki.tools.webfetch

import app.jonaki.core.toolapi.ToolContext
import java.io.File
import java.nio.file.Files
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
}
