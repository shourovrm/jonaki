package app.jonaki.core.ui

/**
 * Reads GitHub-flavoured Markdown tables: a header line, a separator line
 * such as `|---|:---:|` with one cell per header cell, then body lines. The
 * table ends at a blank line or at a line without a pipe; GitHub would keep
 * a pipe-less line as a row, but models put their next sentence right under
 * a table more often than they write a one-cell row without pipes.
 */
internal object MarkdownTables {
    private val separatorCellPattern = Regex("^:?-+:?$")

    class Match(val block: MarkdownBlock.Table, val nextLineIndex: Int)

    /** The table that starts at [lines][startIndex], or null if no table starts there. */
    fun readAt(lines: List<String>, startIndex: Int): Match? {
        val headerLine = lines[startIndex]
        val separatorLine = lines.getOrNull(startIndex + 1) ?: return null
        if (!hasUnescapedPipe(headerLine) || !hasUnescapedPipe(separatorLine)) return null
        val headerCells = splitCells(headerLine)
        val alignments = alignmentsOf(separatorLine) ?: return null
        if (alignments.size != headerCells.size) return null

        var index = startIndex + 2
        val rows = mutableListOf<List<List<MarkdownInline>>>()
        while (index < lines.size && lines[index].isNotBlank() && hasUnescapedPipe(lines[index])) {
            rows += parseRow(splitCells(lines[index]), headerCells.size)
            index += 1
        }
        val table = MarkdownBlock.Table(
            alignments = alignments,
            header = parseRow(headerCells, headerCells.size),
            rows = rows,
            source = lines.subList(startIndex, index).joinToString("\n"),
        )
        return Match(table, index)
    }

    /** Pads a short row with empty cells and drops the cells past [columnCount], as GitHub does. */
    private fun parseRow(cells: List<String>, columnCount: Int): List<List<MarkdownInline>> =
        List(columnCount) { column ->
            val cellText = cells.getOrNull(column).orEmpty()
            MarkdownParser.parseInline(cellText)
        }

    private fun alignmentsOf(separatorLine: String): List<TableAlignment>? {
        val cells = splitCells(separatorLine)
        if (cells.any { cell -> !separatorCellPattern.matches(cell) }) return null
        return cells.map { cell -> alignmentOf(cell) }
    }

    private fun alignmentOf(separatorCell: String): TableAlignment {
        val colonAtStart = separatorCell.startsWith(":")
        val colonAtEnd = separatorCell.endsWith(":")
        return when {
            colonAtStart && colonAtEnd -> TableAlignment.CENTER
            colonAtEnd -> TableAlignment.END
            else -> TableAlignment.START
        }
    }

    /**
     * Splits a row at its unescaped pipes, without the optional outer pipes.
     * `\|` becomes a plain `|` in the cell, also inside code spans, as in GitHub.
     */
    private fun splitCells(line: String): List<String> {
        var row = line.trim()
        if (row.startsWith("|")) {
            row = row.substring(1)
        }
        if (row.endsWith("|") && !row.endsWith("\\|")) {
            row = row.dropLast(1)
        }
        val cells = mutableListOf<String>()
        val currentCell = StringBuilder()
        var index = 0
        while (index < row.length) {
            val character = row[index]
            val isEscapedPipe = character == '\\' && row.getOrNull(index + 1) == '|'
            if (isEscapedPipe) {
                currentCell.append('|')
                index += 2
                continue
            }
            if (character == '|') {
                cells += currentCell.toString().trim()
                currentCell.clear()
            } else {
                currentCell.append(character)
            }
            index += 1
        }
        cells += currentCell.toString().trim()
        return cells
    }

    private fun hasUnescapedPipe(line: String): Boolean =
        line.indices.any { index -> line[index] == '|' && line.getOrNull(index - 1) != '\\' }
}
