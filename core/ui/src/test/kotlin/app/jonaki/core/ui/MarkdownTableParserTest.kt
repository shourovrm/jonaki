package app.jonaki.core.ui

import app.jonaki.core.ui.MarkdownBlock.Paragraph
import app.jonaki.core.ui.MarkdownBlock.Table
import app.jonaki.core.ui.MarkdownInline.Code
import app.jonaki.core.ui.MarkdownInline.Link
import app.jonaki.core.ui.MarkdownInline.Strong
import app.jonaki.core.ui.MarkdownInline.Text
import app.jonaki.core.ui.TableAlignment.CENTER
import app.jonaki.core.ui.TableAlignment.END
import app.jonaki.core.ui.TableAlignment.START
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTableParserTest {

    private fun cell(text: String) = listOf(Text(text))

    @Test
    fun basicTableHasHeaderAndRows() {
        val source = "| Name | Age |\n|------|-----|\n| Rafi | 31 |\n| Mitu | 28 |"

        val blocks = MarkdownParser.parse(source)

        assertEquals(
            listOf(
                Table(
                    alignments = listOf(START, START),
                    header = listOf(cell("Name"), cell("Age")),
                    rows = listOf(
                        listOf(cell("Rafi"), cell("31")),
                        listOf(cell("Mitu"), cell("28")),
                    ),
                    source = source,
                ),
            ),
            blocks,
        )
    }

    @Test
    fun outerPipesAreOptional() {
        val table = MarkdownParser.parse("a | b\n--- | ---\n1 | 2").single() as Table

        assertEquals(listOf(cell("a"), cell("b")), table.header)
        assertEquals(listOf(listOf(cell("1"), cell("2"))), table.rows)
    }

    @Test
    fun colonsInTheSeparatorSetTheAlignment() {
        val table = MarkdownParser.parse("| a | b | c | d |\n|:--|:-:|--:|---|\n| 1 | 2 | 3 | 4 |").single() as Table

        assertEquals(listOf(START, CENTER, END, START), table.alignments)
    }

    @Test
    fun cellsHoldInlineMarkdown() {
        val table = MarkdownParser.parse(
            "| Tool | Note |\n|---|---|\n| **grep** | use `-r`, see [docs](https://example.com) |",
        ).single() as Table

        assertEquals(
            listOf(
                listOf(Strong(listOf(Text("grep")))),
                listOf(Text("use "), Code("-r"), Text(", see "), Link("docs", "https://example.com")),
            ),
            table.rows.single(),
        )
    }

    @Test
    fun anEscapedPipeStaysInsideItsCell() {
        val table = MarkdownParser.parse("| Operator | Meaning |\n|---|---|\n| `a \\| b` | a or b |").single() as Table

        assertEquals(listOf(listOf(Code("a | b")), cell("a or b")), table.rows.single())
    }

    @Test
    fun missingCellsAreEmptyAndExtraCellsAreDropped() {
        val table = MarkdownParser.parse("| a | b | c |\n|---|---|---|\n| 1 |\n| 1 | 2 | 3 | 4 |").single() as Table

        assertEquals(
            listOf(
                listOf(cell("1"), emptyList(), emptyList()),
                listOf(cell("1"), cell("2"), cell("3")),
            ),
            table.rows,
        )
    }

    @Test
    fun aTableRightAfterAParagraphEndsTheParagraph() {
        val blocks = MarkdownParser.parse("Here are the prices:\n| Item | Price |\n|---|---|\n| Tea | 20 |\n\nThat is all.")

        assertEquals(3, blocks.size)
        assertEquals(Paragraph(listOf(Text("Here are the prices:"))), blocks[0])
        val table = blocks[1] as Table
        assertEquals(listOf(cell("Item"), cell("Price")), table.header)
        assertEquals(listOf(listOf(cell("Tea"), cell("20"))), table.rows)
        assertEquals("| Item | Price |\n|---|---|\n| Tea | 20 |", table.source)
        assertEquals(Paragraph(listOf(Text("That is all."))), blocks[2])
    }

    @Test
    fun aLineWithoutAPipeEndsTheTable() {
        val blocks = MarkdownParser.parse("| a |\n|---|\n| 1 |\nNext sentence.")

        assertEquals(listOf(listOf(cell("1"))), (blocks[0] as Table).rows)
        assertEquals(Paragraph(listOf(Text("Next sentence."))), blocks[1])
    }

    @Test
    fun aPipeInNormalTextIsNotATable() {
        val blocks = MarkdownParser.parse("Use a | b in the shell.\nIt pipes output.")

        assertEquals(listOf(Paragraph(listOf(Text("Use a | b in the shell. It pipes output.")))), blocks)
    }

    @Test
    fun aSeparatorWithAnotherCellCountIsNotATable() {
        val blocks = MarkdownParser.parse("| a | b |\n|---|\n| 1 | 2 |")

        assertEquals(1, blocks.size)
        assertEquals(Paragraph::class, blocks.single()::class)
    }

    @Test
    fun aHeaderWithoutItsSeparatorYetStaysAParagraphWhileStreaming() {
        val blocks = MarkdownParser.parse("| Name | Age |")

        assertEquals(listOf(Paragraph(listOf(Text("| Name | Age |")))), blocks)
    }

    @Test
    fun aTableWithOnlyTheHeaderHasNoRows() {
        val table = MarkdownParser.parse("| Name | Age |\n|---|---|").single() as Table

        assertEquals(emptyList<List<List<MarkdownInline>>>(), table.rows)
    }
}
