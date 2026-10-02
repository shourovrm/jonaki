package app.jonaki.tools.readdocument

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds small Office files part by part, the way Word, Excel and
 * PowerPoint lay them out, so each test shows the XML it reads.
 */
internal object OfficeFixtures {
    private const val MAIN = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val SHEET_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val RELATIONSHIPS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val PACKAGE_RELATIONSHIPS = "http://schemas.openxmlformats.org/package/2006/relationships"
    private const val DRAWING = "http://schemas.openxmlformats.org/drawingml/2006/main"
    private const val PRESENTATION = "http://schemas.openxmlformats.org/presentationml/2006/main"

    fun zip(target: File, parts: Map<String, String>): File {
        ZipOutputStream(target.outputStream()).use { zip ->
            for ((name, content) in parts) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return target
    }

    fun docx(target: File): File = zip(
        target,
        mapOf(
            "word/document.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="$MAIN"><w:body>
<w:p><w:pPr><w:pStyle w:val="Heading1"/><w:tabs><w:tab w:val="left" w:pos="720"/></w:tabs></w:pPr><w:r><w:t>Plan</w:t></w:r></w:p>
<w:p><w:r><w:t xml:space="preserve">First </w:t></w:r><w:r><w:rPr><w:b/></w:rPr><w:t>bold</w:t></w:r><w:r><w:tab/><w:t>after tab</w:t></w:r></w:p>
<w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>খাতা</w:t></w:r></w:p>
<w:tbl><w:tr><w:tc><w:p><w:r><w:t>Name</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Price</w:t></w:r></w:p></w:tc></w:tr>
<w:tr><w:tc><w:p><w:r><w:t>Tea</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>40</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
<w:p><w:r><w:delText>removed</w:delText><w:t>End.</w:t></w:r></w:p>
</w:body></w:document>""",
        ),
    )

    fun xlsx(target: File): File = zip(
        target,
        mapOf(
            "xl/workbook.xml" to """<?xml version="1.0" encoding="UTF-8"?>
<workbook xmlns="$SHEET_MAIN" xmlns:r="$RELATIONSHIPS"><sheets>
<sheet name="Sales" sheetId="1" r:id="rId1"/><sheet name="Old" sheetId="2" state="hidden" r:id="rId2"/>
</sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="$PACKAGE_RELATIONSHIPS">
<Relationship Id="rId2" Type="$RELATIONSHIPS/worksheet" Target="worksheets/sheet2.xml"/>
<Relationship Id="rId1" Type="$RELATIONSHIPS/worksheet" Target="/xl/worksheets/sheet1.xml"/>
<Relationship Id="rId3" Type="$RELATIONSHIPS/sharedStrings" Target="sharedStrings.xml"/>
</Relationships>""",
            "xl/sharedStrings.xml" to """<?xml version="1.0" encoding="UTF-8"?>
<sst xmlns="$SHEET_MAIN" count="3" uniqueCount="3">
<si><t>Item</t></si><si><r><t>Pri</t></r><r><t>ce</t></r></si><si><t>東京</t><rPh sb="0" eb="2"><t>トウキョウ</t></rPh></si>
</sst>""",
            "xl/worksheets/sheet1.xml" to """<?xml version="1.0" encoding="UTF-8"?>
<worksheet xmlns="$SHEET_MAIN"><sheetData>
<row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
<row r="2"><c r="A2" t="s"><v>2</v></c><c r="C2"><f>SUM(1,2)</f><v>3</v></c></row>
<row r="4"><c r="A4" t="inlineStr"><is><t>Total</t></is></c><c r="B4" t="b"><v>1</v></c></row>
</sheetData></worksheet>""",
            "xl/worksheets/sheet2.xml" to """<?xml version="1.0" encoding="UTF-8"?>
<worksheet xmlns="$SHEET_MAIN"><sheetData><row r="1"><c r="A1"><v>7</v></c></row></sheetData></worksheet>""",
        ),
    )

    fun pptx(target: File): File = zip(
        target,
        mapOf(
            "ppt/presentation.xml" to """<?xml version="1.0" encoding="UTF-8"?>
<p:presentation xmlns:p="$PRESENTATION" xmlns:r="$RELATIONSHIPS"><p:sldIdLst>
<p:sldId id="256" r:id="rId7"/><p:sldId id="257" r:id="rId8"/>
</p:sldIdLst></p:presentation>""",
            "ppt/_rels/presentation.xml.rels" to """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="$PACKAGE_RELATIONSHIPS">
<Relationship Id="rId8" Type="$RELATIONSHIPS/slide" Target="slides/slide1.xml"/>
<Relationship Id="rId7" Type="$RELATIONSHIPS/slide" Target="slides/slide2.xml"/>
</Relationships>""",
            // The presentation lists slide2.xml first, so it is slide 1.
            "ppt/slides/slide2.xml" to slide("Welcome", "Agenda for today"),
            "ppt/slides/slide1.xml" to slide("Results", ""),
            "ppt/slides/_rels/slide2.xml.rels" to """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="$PACKAGE_RELATIONSHIPS">
<Relationship Id="rId1" Type="$RELATIONSHIPS/notesSlide" Target="../notesSlides/notesSlide1.xml"/>
</Relationships>""",
            "ppt/notesSlides/notesSlide1.xml" to """<?xml version="1.0" encoding="UTF-8"?>
<p:notes xmlns:p="$PRESENTATION" xmlns:a="$DRAWING"><p:cSld><p:spTree>
<p:sp><p:nvSpPr><p:nvPr><p:ph type="body" idx="1"/></p:nvPr></p:nvSpPr><p:txBody><a:p><a:r><a:t>Say hello first.</a:t></a:r></a:p></p:txBody></p:sp>
<p:sp><p:nvSpPr><p:nvPr><p:ph type="sldNum" idx="5"/></p:nvPr></p:nvSpPr><p:txBody><a:p><a:r><a:t>1</a:t></a:r></a:p></p:txBody></p:sp>
</p:spTree></p:cSld></p:notes>""",
        ),
    )

    private fun slide(title: String, body: String): String = """<?xml version="1.0" encoding="UTF-8"?>
<p:sld xmlns:p="$PRESENTATION" xmlns:a="$DRAWING"><p:cSld><p:spTree>
<p:sp><p:txBody><a:p><a:r><a:t>$title</a:t></a:r></a:p></p:txBody></p:sp>
<p:sp><p:txBody><a:p><a:r><a:t>$body</a:t></a:r></a:p></p:txBody></p:sp>
</p:spTree></p:cSld></p:sld>"""
}
