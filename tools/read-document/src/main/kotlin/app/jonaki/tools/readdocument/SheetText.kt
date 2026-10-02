package app.jonaki.tools.readdocument

import java.io.File
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/**
 * Reads an .xlsx workbook: each sheet as tab-separated rows of the values
 * Excel last saved, with shared strings resolved. Formulas show their saved
 * result; dates show as Excel's day numbers, because the cell styles that
 * mark them as dates are not read.
 */
internal object SheetText {
    private const val WORKBOOK_PART = "xl/workbook.xml"
    private const val SHARED_STRINGS_PART = "xl/sharedStrings.xml"

    fun read(file: File, firstSheet: Int, sheetLimit: Int): DocumentWindow {
        OfficePackage(file, "Excel").use { officePackage ->
            val sheets = sheetsOf(officePackage)
            val lastSheet = minOf(sheets.size, firstSheet + sheetLimit - 1)
            val sharedStrings = if (lastSheet >= firstSheet) sharedStringsOf(officePackage) else emptyList()
            val sections = mutableListOf<Section>()
            for (sheetNumber in firstSheet..lastSheet) {
                val sheet = sheets[sheetNumber - 1]
                val handler = CellHandler(sharedStrings)
                officePackage.parse(sheet.partName, handler)
                val hiddenMark = if (sheet.isHidden) " (hidden)" else ""
                sections += Section(sheetNumber, "Sheet $sheetNumber: ${sheet.name}$hiddenMark", handler.text())
            }
            return DocumentWindow("Excel workbook", "sheet", sheets.size, sections)
        }
    }

    private data class SheetEntry(val name: String, val partName: String, val isHidden: Boolean)

    private fun sheetsOf(officePackage: OfficePackage): List<SheetEntry> {
        val relationships = officePackage.relationships(WORKBOOK_PART)
        val sheets = mutableListOf<SheetEntry>()
        officePackage.parse(
            WORKBOOK_PART,
            object : DefaultHandler() {
                override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
                    if (localName != "sheet") {
                        return
                    }
                    val target = relationships[attributes.relationshipId()]?.target ?: return
                    val name = attributes.valueOf("name") ?: "Sheet"
                    val state = attributes.valueOf("state")
                    sheets += SheetEntry(name, target, isHidden = state == "hidden" || state == "veryHidden")
                }
            },
        )
        return sheets
    }

    /** Text cells point into one shared list by index; a workbook without text has no list. */
    private fun sharedStringsOf(officePackage: OfficePackage): List<String> {
        if (!officePackage.has(SHARED_STRINGS_PART)) {
            return emptyList()
        }
        val strings = mutableListOf<String>()
        officePackage.parse(
            SHARED_STRINGS_PART,
            object : DefaultHandler() {
                private val current = StringBuilder()
                private var insideText = false
                private var insidePhonetic = false

                override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
                    when (localName) {
                        "si" -> current.setLength(0)
                        "t" -> insideText = true
                        // Japanese reading hints repeat the text in kana; they are not part of it.
                        "rPh" -> insidePhonetic = true
                    }
                }

                override fun endElement(uri: String, localName: String, qName: String) {
                    when (localName) {
                        "si" -> strings += current.toString()
                        "t" -> insideText = false
                        "rPh" -> insidePhonetic = false
                    }
                }

                override fun characters(characters: CharArray, start: Int, length: Int) {
                    if (insideText && !insidePhonetic) {
                        current.appendRange(characters, start, start + length)
                    }
                }
            },
        )
        return strings
    }

    private class CellHandler(private val sharedStrings: List<String>) : DefaultHandler() {
        private val lines = mutableListOf<String>()
        private val rowCells = sortedMapOf<Int, String>()
        private var cellColumn = 0
        private var cellType: String? = null
        private val cellValue = StringBuilder()
        private var insideValue = false
        private var nextColumn = 0

        fun text(): String = lines.joinToString("\n")

        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            when (localName) {
                "row" -> {
                    rowCells.clear()
                    nextColumn = 0
                }
                "c" -> {
                    cellColumn = attributes.valueOf("r")?.let(::columnIndex) ?: nextColumn
                    cellType = attributes.valueOf("t")
                    cellValue.setLength(0)
                }
                // "v" holds the value; "t" inside "is" holds an inline string. Formulas ("f") are skipped.
                "v", "t" -> insideValue = true
            }
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            when (localName) {
                "v", "t" -> insideValue = false
                "c" -> {
                    val shown = shownValue(cellValue.toString())
                    if (shown.isNotEmpty()) {
                        rowCells[cellColumn] = shown
                    }
                    nextColumn = cellColumn + 1
                }
                "row" -> if (rowCells.isNotEmpty()) lines += rowLine()
            }
        }

        override fun characters(characters: CharArray, start: Int, length: Int) {
            if (insideValue) {
                cellValue.appendRange(characters, start, start + length)
            }
        }

        private fun rowLine(): String {
            val lastColumn = rowCells.lastKey()
            return (0..lastColumn).joinToString("\t") { column -> rowCells[column].orEmpty() }
        }

        private fun shownValue(raw: String): String {
            val value = when (cellType) {
                "s" -> raw.trim().toIntOrNull()?.let { index -> sharedStrings.getOrNull(index) }.orEmpty()
                "b" -> if (raw.trim() == "1") "TRUE" else "FALSE"
                else -> raw
            }
            // A tab or line break inside a cell would break the row apart.
            return value.replace(Regex("[\t\r\n]+"), " ").trim()
        }

        /** "C7" to 2, "AA1" to 26. */
        private fun columnIndex(reference: String): Int? {
            val letters = reference.takeWhile { character -> character.isLetter() }.uppercase()
            if (letters.isEmpty()) {
                return null
            }
            var index = 0
            for (letter in letters) {
                index = index * 26 + (letter - 'A' + 1)
            }
            return index - 1
        }
    }
}
