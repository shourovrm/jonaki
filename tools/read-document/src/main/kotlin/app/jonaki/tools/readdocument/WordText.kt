package app.jonaki.tools.readdocument

import java.io.File
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/**
 * Reads the body of a .docx file as plain text: one line per paragraph,
 * "#" before headings, "- " before list items, and each table row as
 * tab-separated cells. Headers, footers, footnotes and comments are left out.
 */
internal object WordText {
    private const val BODY_PART = "word/document.xml"

    fun read(file: File): DocumentWindow {
        val handler = BodyHandler()
        OfficePackage(file, "Word").use { officePackage -> officePackage.parse(BODY_PART, handler) }
        return DocumentWindow("Word document", unitName = null, totalUnits = 1, listOf(Section(1, "", handler.text())))
    }

    private class BodyHandler : DefaultHandler() {
        private val lines = mutableListOf<String>()
        private val paragraph = StringBuilder()
        private var paragraphPrefix = ""
        private var insideText = false
        private var insideRun = false

        // Tables can sit inside table cells, so rows and cells are stacks.
        private val rowStack = ArrayDeque<MutableList<String>>()
        private val cellStack = ArrayDeque<StringBuilder>()

        fun text(): String = lines.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()

        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            when (localName) {
                "p" -> {
                    paragraph.setLength(0)
                    paragraphPrefix = ""
                }
                "pStyle" -> paragraphPrefix = headingPrefix(attributes.valueOf("val"))
                "numPr" -> if (paragraphPrefix.isEmpty()) paragraphPrefix = "- "
                "r" -> insideRun = true
                "t" -> insideText = true
                // Outside a run, "tab" is a tab stop in the paragraph's settings, not a character.
                "tab" -> if (insideRun) paragraph.append('\t')
                "br", "cr" -> if (insideRun) paragraph.append(' ')
                "tr" -> rowStack.addLast(mutableListOf())
                "tc" -> cellStack.addLast(StringBuilder())
            }
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            when (localName) {
                "r" -> insideRun = false
                "t" -> insideText = false
                "p" -> endParagraph()
                "tc" -> {
                    val cell = cellStack.removeLastOrNull() ?: return
                    rowStack.lastOrNull()?.add(cell.toString().trim())
                }
                "tr" -> {
                    val row = rowStack.removeLastOrNull() ?: return
                    addLine(row.joinToString("\t"))
                }
                "tbl" -> if (cellStack.isEmpty()) lines += ""
            }
        }

        override fun characters(characters: CharArray, start: Int, length: Int) {
            if (insideText) {
                paragraph.appendRange(characters, start, start + length)
            }
        }

        private fun endParagraph() {
            val text = paragraph.toString().trim()
            val cell = cellStack.lastOrNull()
            if (cell != null) {
                // Paragraphs inside one cell share its line.
                if (text.isNotEmpty()) {
                    if (cell.isNotEmpty()) cell.append(' ')
                    cell.append(text)
                }
                return
            }
            addLine(if (text.isEmpty()) "" else paragraphPrefix + text)
        }

        /** A row of a table nested in a cell joins that cell; others are lines of their own. */
        private fun addLine(line: String) {
            val cell = cellStack.lastOrNull()
            if (cell != null) {
                if (cell.isNotEmpty()) cell.append(' ')
                cell.append(line.replace('\t', ' '))
                return
            }
            lines += line
        }

        /** Built-in style ids are "Title", "Heading1" to "Heading9" in every language of Word. */
        private fun headingPrefix(styleId: String?): String {
            if (styleId == null) {
                return ""
            }
            if (styleId == "Title") {
                return "# "
            }
            val level = styleId.removePrefix("Heading").toIntOrNull() ?: return ""
            if (!styleId.startsWith("Heading") || level !in 1..9) {
                return ""
            }
            return "#".repeat(level) + " "
        }
    }
}
