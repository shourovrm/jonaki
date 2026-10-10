package app.jonaki.tools.webfetch

import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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

class WebFetchPdfTest {
    private lateinit var server: MockWebServer
    private val threadFolder: File = Files.createTempDirectory("thread").toFile()
    private val cacheFolder: File = Files.createTempDirectory("pdf-cache").toFile()
    private val context = ToolContext(threadFolder, OkHttpClient())
    private val tool = WebFetchTool(pdfSupport = PdfSupport(FormFeedPdfReader, cacheFolder))

    /** Stands in for a real PDF reader: a page ends at a form feed, and a file holding "locked" cannot be read. */
    private object FormFeedPdfReader : PdfReader {
        override fun read(file: File, firstPage: Int, pageLimit: Int): PdfPages {
            val content = file.readText().removePrefix("%PDF-")
            if (content == "locked") throw PdfReadException("it is locked with a password")
            val pages = content.split('\u000C')
            return PdfPages(pages.size, pages.drop(firstPage - 1).take(pageLimit))
        }
    }

    private fun pdfOf(vararg pages: String): String = "%PDF-" + pages.joinToString("\u000C")

    private fun pdfResponse(body: String, contentType: String = "application/pdf"): MockResponse =
        MockResponse().setHeader("Content-Type", contentType).setBody(body)

    private fun fetch(json: String): ToolOutput = runBlocking {
        tool.run(Json.parseToJsonElement(json).jsonObject, context)
    }

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
    fun aPdfLinkComesAsPagesAndLeavesNothingInTheThreadFolder() {
        server.enqueue(pdfResponse(pdfOf("Budget summary", "", "Annex")))
        val url = server.url("/report.pdf").toString()

        val output = fetch("""{"url":"$url"}""")

        assertFalse(output.text, output.isError)
        assertTrue(output.text, output.text.startsWith("Source: $url\nPDF, 3 pages."))
        assertTrue(output.text.contains("--- Page 1 ---\nBudget summary"))
        assertTrue(output.text.contains("--- Page 2 ---\n[No text on this page; it may be a scan or a picture.]"))
        assertTrue(output.text.endsWith("--- Page 3 ---\nAnnex"))
        assertEquals(emptyList<String>(), threadFolder.walk().filter(File::isFile).map(File::getName).toList())
    }

    @Test
    fun laterPagesComeFromTheCopyWithoutASecondDownload() {
        server.enqueue(pdfResponse(pdfOf("one", "two", "three")))
        val url = server.url("/report.pdf").toString()

        val first = fetch("""{"url":"$url","limit":2}""")
        val second = fetch("""{"url":"$url","offset":3}""")

        assertTrue(first.text, first.text.endsWith("[Showing pages 1-2 of 3. Use web_fetch url=\"$url\" offset=3 to continue.]"))
        assertTrue(second.text, second.text.endsWith("--- Page 3 ---\nthree"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun onlyWholePagesThatFitMaxLengthAreShown() {
        val longPage = "word ".repeat(80).trim()
        server.enqueue(pdfResponse(pdfOf(longPage, longPage, longPage)))
        val url = server.url("/long.pdf").toString()

        val output = fetch("""{"url":"$url","max_length":700}""")

        assertTrue(output.text, output.text.contains("--- Page 1 ---"))
        assertFalse(output.text, output.text.contains("--- Page 2 ---"))
        assertTrue(output.text, output.text.endsWith("[Showing pages 1-1 of 3. Use web_fetch url=\"$url\" offset=2 to continue.]"))
        assertEquals(emptyList<File>(), threadFolder.walk().filter(File::isFile).toList())
    }

    @Test
    fun aPdfSentAsAPlainDownloadIsReadByItsFirstBytes() {
        server.enqueue(pdfResponse(pdfOf("only page"), contentType = "application/octet-stream"))

        val output = fetch("""{"url":"${server.url("/download?id=7")}"}""")

        assertFalse(output.text, output.isError)
        assertTrue(output.text, output.text.contains("PDF, 1 page.\n\n--- Page 1 ---\nonly page"))
    }

    @Test
    fun anotherDownloadIsStillRefused() {
        server.enqueue(pdfResponse("PK\u0003\u0004 archive", contentType = "application/zip"))
        val url = server.url("/files.zip").toString()

        val output = fetch("""{"url":"$url"}""")

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("$url is application/zip, not a web page"))
    }

    @Test
    fun aPdfThatCannotBeReadSaysWhyAndIsNotKept() {
        server.enqueue(pdfResponse(pdfOf("locked")))
        val url = server.url("/locked.pdf").toString()

        val output = fetch("""{"url":"$url"}""")

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("could not read the PDF at $url (it is locked with a password)"))
        assertEquals(emptyList<File>(), cacheFolder.listFiles().orEmpty().toList())
    }

    @Test
    fun anOffsetPastTheLastPageNamesTheRange() {
        server.enqueue(pdfResponse(pdfOf("one", "two")))
        val url = server.url("/short.pdf").toString()

        val output = fetch("""{"url":"$url","offset":5}""")

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("offset 5 is past the end; the PDF at $url has 2 pages"))
    }

    @Test
    fun withoutAReaderAPdfLinkIsRefusedAsBefore() {
        server.enqueue(pdfResponse(pdfOf("one")))
        val url = server.url("/report.pdf").toString()

        val output = runBlocking { WebFetchTool().run(Json.parseToJsonElement("""{"url":"$url"}""").jsonObject, context) }

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("is application/pdf, not a web page"))
        assertFalse("offset" in WebFetchTool().parameterSchema.toString())
        assertTrue("offset" in tool.parameterSchema.toString())
    }
}
