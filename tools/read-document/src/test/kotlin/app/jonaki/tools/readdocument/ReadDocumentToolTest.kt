package app.jonaki.tools.readdocument

import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadDocumentToolTest {
    private val threadFolder: File = Files.createTempDirectory("thread").toFile()
    private val context = ToolContext(threadFolder, OkHttpClient())
    private val testdata = File(System.getProperty("jonaki.testdata")!!, "documents")

    private fun read(vararg arguments: Pair<String, Any>): ToolOutput = runBlocking {
        val json = JsonObject(
            arguments.associate { (key, value) ->
                key to if (value is Int) JsonPrimitive(value) else JsonPrimitive(value.toString())
            },
        )
        ReadDocumentTool().run(json, context)
    }

    private fun inbox(name: String): File = File(threadFolder, "inbox/$name").also { file -> file.parentFile.mkdirs() }

    private fun copyFixture(name: String) {
        File(testdata, name).copyTo(inbox(name))
    }

    @Test
    fun pdfPagesComeWithMarkersAndAScanNote() {
        copyFixture("three-pages.pdf")
        val output = read("path" to "inbox/three-pages.pdf")

        assertFalse(output.text, output.isError)
        assertTrue(output.text.startsWith("inbox/three-pages.pdf: PDF, 3 pages."))
        assertTrue(output.text.contains("--- Page 1 ---\nQuarterly report\nSales rose 12 percent."))
        assertTrue(output.text.contains("--- Page 2 ---\nSecond page text"))
        assertTrue(output.text.contains("--- Page 3 ---\n[No text on this page; it may be a scan or a picture."))
        assertTrue(output.text.contains("view_image path=\"inbox/three-pages.pdf\" page=3"))
    }

    @Test
    fun pdfOffsetAndLimitNameTheNextCall() {
        copyFixture("three-pages.pdf")
        val output = read("path" to "inbox/three-pages.pdf", "offset" to 2, "limit" to 1)

        assertFalse(output.text.contains("Page 1 ---"))
        assertTrue(output.text.contains("--- Page 2 ---"))
        assertTrue(output.text.endsWith("[Showing pages 2-2 of 3. Use read_document path=\"inbox/three-pages.pdf\" offset=3 to continue.]"))
        assertTrue(read("path" to "inbox/three-pages.pdf", "offset" to 9).text.contains("offset 9 is past the end"))
    }

    @Test
    fun aLockedPdfSaysSo() {
        copyFixture("locked.pdf")
        val output = read("path" to "inbox/locked.pdf")

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("locked.pdf is locked with a password"))
    }

    @Test
    fun wordKeepsHeadingsListsAndTableRows() {
        OfficeFixtures.docx(inbox("plan.docx"))
        val output = read("path" to "inbox/plan.docx")

        assertFalse(output.text, output.isError)
        val expected = "inbox/plan.docx: Word document.\n\n" +
            "# Plan\nFirst bold\tafter tab\n- খাতা\nName\tPrice\nTea\t40\n\nEnd."
        assertEquals(expected, output.text)
    }

    @Test
    fun excelSheetsAreTabSeparatedWithSharedStrings() {
        OfficeFixtures.xlsx(inbox("sales.xlsx"))
        val output = read("path" to "inbox/sales.xlsx")

        assertFalse(output.text, output.isError)
        assertTrue(output.text.startsWith("inbox/sales.xlsx: Excel workbook, 2 sheets."))
        assertTrue(output.text.contains("--- Sheet 1: Sales ---\nItem\tPrice\n東京\t\t3\nTotal\tTRUE"))
        assertTrue(output.text.contains("--- Sheet 2: Old (hidden) ---\n7"))
    }

    @Test
    fun powerPointFollowsTheSlideOrderAndAddsNotes() {
        OfficeFixtures.pptx(inbox("talk.pptx"))
        val output = read("path" to "inbox/talk.pptx")

        assertFalse(output.text, output.isError)
        assertTrue(output.text.contains("--- Slide 1 ---\nWelcome\nAgenda for today\n\nNotes:\nSay hello first."))
        assertTrue(output.text.contains("--- Slide 2 ---\nResults"))
        assertFalse(output.text.contains("Notes:\nSay hello first.\n1"))
    }

    @Test
    fun oldOfficeFilesAndOtherTypesAreRefusedClearly() {
        inbox("budget.xls").writeBytes(byteArrayOf(1, 2, 3))
        inbox("notes.txt").writeText("hello")
        val oldExcel = read("path" to "inbox/budget.xls")
        val text = read("path" to "inbox/notes.txt")

        assertTrue(oldExcel.isError)
        assertTrue(oldExcel.text.contains("old Office file (.xls)"))
        assertTrue(oldExcel.text.contains("save it as .xlsx or PDF"))
        assertTrue(text.text.contains("Use read_file for text files."))
    }

    @Test
    fun aPasswordProtectedOfficeFileIsNamedAsSuch() {
        val compoundStart = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(), 0, 0,
        )
        inbox("secret.docx").writeBytes(compoundStart)
        val output = read("path" to "inbox/secret.docx")

        assertTrue(output.isError)
        assertTrue(output.text.contains("locked with a password, or is an old Office file"))
    }

    @Test
    fun longOutputIsSavedWhole() {
        val manyRows = (1..6_000).joinToString("") { row -> """<row r="$row"><c r="A$row"><v>${row * 1000}</v></c></row>""" }
        OfficeFixtures.zip(
            inbox("big.xlsx"),
            mapOf(
                "xl/workbook.xml" to """<workbook xmlns:r="r"><sheets><sheet name="Big" r:id="rId1"/></sheets></workbook>""",
                "xl/_rels/workbook.xml.rels" to """<Relationships><Relationship Id="rId1" Type="t/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
                "xl/worksheets/sheet1.xml" to "<worksheet><sheetData>$manyRows</sheetData></worksheet>",
            ),
        )
        val output = read("path" to "inbox/big.xlsx")

        assertTrue(output.text.contains("Full output saved to work/tool-output/read_document-1.txt"))
        assertTrue(File(threadFolder, "work/tool-output/read_document-1.txt").readText().contains("6000000"))
    }

    @Test
    fun theResultIsOutsideContentNamedByTheFile() {
        val arguments = JsonObject(mapOf("path" to JsonPrimitive("inbox/offer.pdf")))

        assertEquals("offer.pdf", ReadDocumentTool().outsideContentSourceOf(arguments))
    }
}
