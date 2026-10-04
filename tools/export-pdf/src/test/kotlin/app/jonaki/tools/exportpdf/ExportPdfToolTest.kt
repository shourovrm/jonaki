package app.jonaki.tools.exportpdf

import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import java.io.File
import java.nio.file.Files
import kotlin.time.Duration
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportPdfToolTest {
    /** Writes [pdfBytes] to the output file, or throws [failure], and records what it was asked. */
    private class FakeRenderer : PdfRenderer {
        var pdfBytes: ByteArray = ByteArray(2_048)
        var pageCount: Int? = 3
        var failure: PdfRenderException? = null
        val htmlPaths = mutableListOf<String>()
        val pageSizes = mutableListOf<PdfPageSize>()
        var lastTimeLimit: Duration? = null
        var lastOutputFile: File? = null

        override suspend fun render(
            threadFolder: File,
            htmlPath: String,
            outputFile: File,
            pageSize: PdfPageSize,
            timeLimit: Duration,
        ): RenderedPdf {
            htmlPaths += htmlPath
            pageSizes += pageSize
            lastTimeLimit = timeLimit
            lastOutputFile = outputFile
            failure?.let { throw it }
            outputFile.writeBytes(pdfBytes)
            return RenderedPdf(pageCount)
        }
    }

    private val threadFolder = Files.createTempDirectory("thread").toFile()
    private val renderer = FakeRenderer()
    private val tool = ExportPdfTool(renderer)
    private val context = ToolContext(threadFolder, OkHttpClient())

    init {
        File(threadFolder, "artifacts").mkdirs()
        File(threadFolder, "artifacts/report.html").writeText("<html><body>Hello</body></html>")
        File(threadFolder, "work").mkdirs()
        File(threadFolder, "work/notes.txt").writeText("notes")
        File(threadFolder, "work/page.html").writeText("<html></html>")
    }

    private fun call(vararg arguments: Pair<String, String>): ToolOutput = runBlocking {
        tool.run(JsonObject(arguments.associate { (key, value) -> key to JsonPrimitive(value) }), context)
    }

    @Test
    fun changesOnlyTheThreadFolderAndHasAShorterRenderLimitThanItsOwn() {
        assertEquals(SideEffect.CHANGES_THREAD_FOLDER, tool.sideEffect)
        assertEquals("export_pdf", tool.name)
        call("path" to "artifacts/report.html")
        assertTrue(renderer.lastTimeLimit!! < tool.timeLimit)
    }

    @Test
    fun theOutputDefaultsToTheHtmlNameWithPdfBesideIt() {
        val output = call("path" to "artifacts/report.html")
        assertFalse(output.text, output.isError)
        assertTrue(File(threadFolder, "artifacts/report.pdf").isFile)
        assertEquals(2_048L, File(threadFolder, "artifacts/report.pdf").length())
    }

    @Test
    fun anOutputPathIsUsedAndItsFoldersAreCreated() {
        val output = call("path" to "artifacts/report.html", "output" to "exports/q3/report.pdf")
        assertFalse(output.text, output.isError)
        assertTrue(File(threadFolder, "exports/q3/report.pdf").isFile)
        assertFalse(File(threadFolder, "artifacts/report.pdf").exists())
    }

    @Test
    fun successTextNamesThePathPagesSizeAndHowToSaveIt() {
        val output = call("path" to "artifacts/report.html")
        assertEquals(
            "Created artifacts/report.pdf (3 pages, 2 KB). " +
                "Use share_file with action downloads to save it to Downloads, or action share to send it.",
            output.text,
        )
    }

    @Test
    fun oneNamedPageIsSingularAndAnUnknownCountIsLeftOut() {
        renderer.pageCount = 1
        assertTrue(call("path" to "artifacts/report.html").text.contains("(1 page, "))
        renderer.pageCount = null
        val output = call("path" to "artifacts/report.html")
        assertTrue(output.text.contains("(2 KB)"))
    }

    @Test
    fun pageDefaultsToA4AndPassesTheChosenPaper() {
        call("path" to "artifacts/report.html")
        call("path" to "artifacts/report.html", "page" to "letter")
        call("path" to "artifacts/report.html", "page" to "slides")
        assertEquals(listOf(PdfPageSize.A4, PdfPageSize.LETTER, PdfPageSize.SLIDES), renderer.pageSizes)
    }

    @Test
    fun anUnknownPageIsRefusedWithTheChoices() {
        val output = call("path" to "artifacts/report.html", "page" to "a3")
        assertTrue(output.isError)
        assertTrue(output.text.contains("a4, letter, slides"))
        assertTrue(renderer.htmlPaths.isEmpty())
    }

    @Test
    fun aMissingPathIsAnError() {
        val output = call()
        assertTrue(output.isError)
        assertTrue(output.text.contains("path"))
    }

    @Test
    fun aPathOutsideTheThreadFolderIsRefusedBeforeRendering() {
        val htmlOutside = call("path" to "../other-thread/artifacts/report.html")
        assertTrue(htmlOutside.isError)
        assertTrue(htmlOutside.text.contains("outside the thread folder"))
        val pdfOutside = call("path" to "artifacts/report.html", "output" to "../stolen.pdf")
        assertTrue(pdfOutside.isError)
        assertTrue(pdfOutside.text.contains("outside the thread folder"))
        assertTrue(renderer.htmlPaths.isEmpty())
    }

    @Test
    fun aMissingFileAFolderAndANonHtmlFileFailLoudly() {
        val missing = call("path" to "artifacts/nothing.html")
        assertTrue(missing.isError)
        assertTrue(missing.text.contains("does not exist"))
        val folder = call("path" to "artifacts")
        assertTrue(folder.isError)
        assertTrue(folder.text.contains("is a folder"))
        val notHtml = call("path" to "work/notes.txt")
        assertTrue(notHtml.isError)
        assertTrue(notHtml.text.contains("not an HTML file"))
        assertTrue(renderer.htmlPaths.isEmpty())
    }

    @Test
    fun anHtmlFileOutsideArtifactsIsRefusedBecauseThePageLoaderCannotServeIt() {
        val output = call("path" to "work/page.html")
        assertTrue(output.isError)
        assertTrue(output.text.contains("artifacts"))
        assertTrue(renderer.htmlPaths.isEmpty())
    }

    @Test
    fun theOutputMustBeAPdfFileName() {
        val output = call("path" to "artifacts/report.html", "output" to "artifacts/report.txt")
        assertTrue(output.isError)
        assertTrue(output.text.contains(".pdf"))
    }

    @Test
    fun aRendererFailureIsAnErrorThatSaysWhatFailedAndLeavesNothingBehind() {
        renderer.failure = PdfRenderException("the page did not finish loading within 50s")
        val output = call("path" to "artifacts/report.html")
        assertTrue(output.isError)
        assertTrue(output.text.contains("the page did not finish loading within 50s"))
        assertTrue(output.text.contains("Print button"))
        assertFalse(File(threadFolder, "artifacts/report.pdf").exists())
        assertFalse(renderer.lastOutputFile!!.exists())
    }

    @Test
    fun aFailedRunKeepsAnEarlierPdf() {
        File(threadFolder, "artifacts/report.pdf").writeText("old")
        renderer.failure = PdfRenderException("timed out")
        call("path" to "artifacts/report.html")
        assertEquals("old", File(threadFolder, "artifacts/report.pdf").readText())
    }

    @Test
    fun anEmptyPdfIsAnErrorNotASuccess() {
        renderer.pdfBytes = ByteArray(0)
        val output = call("path" to "artifacts/report.html")
        assertTrue(output.isError)
        assertTrue(output.text.contains("empty"))
        assertFalse(File(threadFolder, "artifacts/report.pdf").exists())
    }

    @Test
    fun aSecondExportReplacesTheFirst() {
        call("path" to "artifacts/report.html")
        renderer.pdfBytes = ByteArray(10)
        val output = call("path" to "artifacts/report.html")
        assertFalse(output.isError)
        assertEquals(10L, File(threadFolder, "artifacts/report.pdf").length())
    }
}
