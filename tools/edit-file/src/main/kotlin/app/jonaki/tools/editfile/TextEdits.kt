package app.jonaki.tools.editfile

/** Replace [oldText] with [newText]; [oldText] must occur exactly once. */
data class TextEdit(val oldText: String, val newText: String)

enum class MatchKind {
    EXACT,

    /** Matched after ignoring trailing spaces, line-end style, smart quotes and dashes. */
    FUZZY,
}

data class EditMatch(val kind: MatchKind, val firstLine: Int)

sealed interface EditResult {
    data class Applied(val newContent: String, val matches: List<EditMatch>) : EditResult

    /** Edit number [editNumber] (counting from 1) failed; no edit was applied. */
    data class Failed(val editNumber: Int, val reason: String) : EditResult
}

/**
 * Applies the edits in order, each to the result of the one before. Either
 * all edits apply or none does. An edit first looks for an exact match; only
 * when there is none does it try a whole-line fuzzy match, because models
 * often get trailing spaces and quote styles wrong when they copy text.
 */
fun applyEdits(content: String, edits: List<TextEdit>): EditResult {
    var current = content
    val matches = mutableListOf<EditMatch>()
    for ((index, edit) in edits.withIndex()) {
        val editNumber = index + 1
        if (edit.oldText.isEmpty()) {
            return EditResult.Failed(editNumber, "old_text is empty; give the exact text to replace")
        }
        val outcome = applyOneEdit(current, edit)
        when (outcome) {
            is SingleEditOutcome.Done -> {
                current = outcome.newContent
                matches += outcome.match
            }
            is SingleEditOutcome.Problem -> return EditResult.Failed(editNumber, outcome.reason)
        }
    }
    return EditResult.Applied(current, matches)
}

private sealed interface SingleEditOutcome {
    data class Done(val newContent: String, val match: EditMatch) : SingleEditOutcome

    data class Problem(val reason: String) : SingleEditOutcome
}

private fun applyOneEdit(content: String, edit: TextEdit): SingleEditOutcome {
    val exactCount = countOccurrences(content, edit.oldText)
    if (exactCount == 1) {
        val start = content.indexOf(edit.oldText)
        val newContent = content.substring(0, start) + edit.newText + content.substring(start + edit.oldText.length)
        return SingleEditOutcome.Done(newContent, EditMatch(MatchKind.EXACT, lineNumberAt(content, start)))
    }
    if (exactCount > 1) {
        return SingleEditOutcome.Problem(
            "old_text appears $exactCount times; include more surrounding lines so that it appears once",
        )
    }
    return applyFuzzyEdit(content, edit)
}

private fun applyFuzzyEdit(content: String, edit: TextEdit): SingleEditOutcome {
    val fileLines = splitKeepingLineEnds(content)
    val oldTextWithUnixEnds = edit.oldText.replace("\r\n", "\n")
    val oldEndsWithNewline = oldTextWithUnixEnds.endsWith("\n")
    val oldLines = oldTextWithUnixEnds.removeSuffix("\n").split("\n").map(::normalizeLine)

    val starts = (0..fileLines.size - oldLines.size).filter { start ->
        oldLines.indices.all { offset -> normalizeLine(fileLines[start + offset].text) == oldLines[offset] }
    }
    if (starts.isEmpty()) {
        return SingleEditOutcome.Problem(
            "old_text not found, not even when ignoring spacing and quote style; read the file again and copy the text exactly",
        )
    }
    if (starts.size > 1) {
        return SingleEditOutcome.Problem(
            "old_text appears ${starts.size} times (ignoring spacing); include more surrounding lines so that it appears once",
        )
    }

    val first = starts.single()
    val last = first + oldLines.size - 1
    val lineEnd = fileLines.firstOrNull { line -> line.ending.isNotEmpty() }?.ending ?: "\n"
    val replacement = edit.newText.replace("\r\n", "\n").replace("\n", lineEnd)

    val newContent = StringBuilder()
    for (line in fileLines.subList(0, first)) {
        newContent.append(line.text).append(line.ending)
    }
    newContent.append(replacement)
    // The matched block's own final line end stays unless old_text included it.
    if (!oldEndsWithNewline) {
        newContent.append(fileLines[last].ending)
    }
    for (line in fileLines.subList(last + 1, fileLines.size)) {
        newContent.append(line.text).append(line.ending)
    }
    return SingleEditOutcome.Done(newContent.toString(), EditMatch(MatchKind.FUZZY, first + 1))
}

private data class Line(val text: String, val ending: String)

private fun splitKeepingLineEnds(content: String): List<Line> {
    val lines = mutableListOf<Line>()
    var lineStart = 0
    var index = 0
    while (index < content.length) {
        if (content[index] == '\n') {
            val hasCarriageReturn = index > lineStart && content[index - 1] == '\r'
            val textEnd = if (hasCarriageReturn) index - 1 else index
            val ending = if (hasCarriageReturn) "\r\n" else "\n"
            lines += Line(content.substring(lineStart, textEnd), ending)
            lineStart = index + 1
        }
        index += 1
    }
    if (lineStart < content.length) {
        lines += Line(content.substring(lineStart), "")
    }
    return lines
}

private fun normalizeLine(line: String): String {
    val plain = StringBuilder(line.length)
    for (character in line) {
        val replacement = when (character) {
            '‘', '’', '‚', '‛' -> '\''
            '“', '”', '„', '‟' -> '"'
            '–', '—', '‑', '−' -> '-'
            ' ' -> ' '
            else -> character
        }
        plain.append(replacement)
    }
    return plain.toString().trimEnd()
}

private fun countOccurrences(content: String, text: String): Int {
    var count = 0
    var from = content.indexOf(text)
    while (from >= 0) {
        count += 1
        from = content.indexOf(text, from + 1)
    }
    return count
}

private fun lineNumberAt(content: String, index: Int): Int =
    content.substring(0, index).count { character -> character == '\n' } + 1
