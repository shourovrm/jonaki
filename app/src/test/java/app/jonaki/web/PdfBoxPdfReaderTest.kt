package app.jonaki.web

import app.jonaki.core.toolapi.ToolContext
import app.jonaki.tools.webfetch.PdfSupport
import app.jonaki.tools.webfetch.WebFetchTool
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * web_fetch with the reader the app gives it, on a real PDF file. Only the
 * locked file is tried here: reading page text needs PdfBox's glyph tables,
 * which only the tests of tools/read-document have on their classpath.
 */
class PdfBoxPdfReaderTest {
    private val threadFolder: File = Files.createTempDirectory("thread").toFile()
    private val cacheFolder: File = Files.createTempDirectory("pdf-cache").toFile()
    private val tool = WebFetchTool(pdfSupport = PdfSupport(PdfBoxPdfReader(), cacheFolder))

    private fun fetchPdf(fixtureName: String) = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val pdfBytes = File("../testdata/documents", fixtureName).readBytes()
            server.enqueue(MockResponse().setHeader("Content-Type", "application/pdf").setBody(Buffer().write(pdfBytes)))
            val arguments = Json.parseToJsonElement("""{"url":"${server.url("/$fixtureName")}"}""").jsonObject
            tool.run(arguments, ToolContext(threadFolder, OkHttpClient()))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun aLockedPdfLinkSaysSo() {
        val output = fetchPdf("locked.pdf")

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("(it is locked with a password)"))
        assertEquals(emptyList<File>(), threadFolder.walk().filter(File::isFile).toList())
        assertEquals(emptyList<File>(), cacheFolder.listFiles().orEmpty().toList())
    }
}
