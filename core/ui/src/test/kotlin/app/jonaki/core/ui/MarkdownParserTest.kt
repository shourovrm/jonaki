package app.jonaki.core.ui

import app.jonaki.core.ui.MarkdownBlock.BulletList
import app.jonaki.core.ui.MarkdownBlock.CodeBlock
import app.jonaki.core.ui.MarkdownBlock.Heading
import app.jonaki.core.ui.MarkdownBlock.OrderedList
import app.jonaki.core.ui.MarkdownBlock.Paragraph
import app.jonaki.core.ui.MarkdownInline.Code
import app.jonaki.core.ui.MarkdownInline.Emphasis
import app.jonaki.core.ui.MarkdownInline.Link
import app.jonaki.core.ui.MarkdownInline.Strong
import app.jonaki.core.ui.MarkdownInline.Text
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun blankLinesSeparateParagraphsAndSingleNewlinesJoinLines() {
        val blocks = MarkdownParser.parse("first line\nsame paragraph\n\nsecond")

        assertEquals(
            listOf(
                Paragraph(listOf(Text("first line same paragraph"))),
                Paragraph(listOf(Text("second"))),
            ),
            blocks,
        )
    }

    @Test
    fun headingsKeepTheirLevel() {
        val blocks = MarkdownParser.parse("# Title\n### Small")

        assertEquals(listOf(Heading(1, listOf(Text("Title"))), Heading(3, listOf(Text("Small")))), blocks)
    }

    @Test
    fun hashWithoutSpaceIsNotAHeading() {
        val blocks = MarkdownParser.parse("#hashtag")

        assertEquals(listOf(Paragraph(listOf(Text("#hashtag")))), blocks)
    }

    @Test
    fun boldItalicCodeAndLinksAreInlineSpans() {
        val blocks = MarkdownParser.parse("a **bold** and *it* with `x` see [docs](https://example.com).")

        assertEquals(
            listOf(
                Paragraph(
                    listOf(
                        Text("a "),
                        Strong(listOf(Text("bold"))),
                        Text(" and "),
                        Emphasis(listOf(Text("it"))),
                        Text(" with "),
                        Code("x"),
                        Text(" see "),
                        Link("docs", "https://example.com"),
                        Text("."),
                    ),
                ),
            ),
            blocks,
        )
    }

    @Test
    fun italicInsideBoldIsNested() {
        val blocks = MarkdownParser.parse("**very *much* so**")

        assertEquals(
            listOf(Paragraph(listOf(Strong(listOf(Text("very "), Emphasis(listOf(Text("much"))), Text(" so")))))),
            blocks,
        )
    }

    @Test
    fun unclosedMarkersStayLiteral() {
        val blocks = MarkdownParser.parse("2 * 3 = 6 and **open")

        assertEquals(listOf(Paragraph(listOf(Text("2 * 3 = 6 and **open")))), blocks)
    }

    @Test
    fun markersInsideInlineCodeAreNotParsed() {
        val blocks = MarkdownParser.parse("`**not bold**`")

        assertEquals(listOf(Paragraph(listOf(Code("**not bold**")))), blocks)
    }

    @Test
    fun fencedCodeBlockKeepsLinesAndLanguage() {
        val blocks = MarkdownParser.parse("before\n```kotlin\nval x = 1\n\nval y = 2\n```\nafter")

        assertEquals(
            listOf(
                Paragraph(listOf(Text("before"))),
                CodeBlock("val x = 1\n\nval y = 2", "kotlin"),
                Paragraph(listOf(Text("after"))),
            ),
            blocks,
        )
    }

    @Test
    fun unclosedFenceWhileStreamingRunsToTheEnd() {
        val blocks = MarkdownParser.parse("```\nline one\nline two")

        assertEquals(listOf(CodeBlock("line one\nline two", null)), blocks)
    }

    @Test
    fun bulletAndNumberedListsCollectTheirItems() {
        val blocks = MarkdownParser.parse("- one\n* **two**\n\n3. three\n4. four")

        assertEquals(
            listOf(
                BulletList(listOf(listOf(Text("one")), listOf(Strong(listOf(Text("two")))))),
                OrderedList(3, listOf(listOf(Text("three")), listOf(Text("four")))),
            ),
            blocks,
        )
    }

    @Test
    fun banglaTextPassesThroughUnchanged() {
        val blocks = MarkdownParser.parse("**সিলেট** ভ্রমণ")

        assertEquals(listOf(Paragraph(listOf(Strong(listOf(Text("সিলেট"))), Text(" ভ্রমণ")))), blocks)
    }
}
