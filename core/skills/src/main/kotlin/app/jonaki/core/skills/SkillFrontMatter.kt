package app.jonaki.core.skills

/**
 * The two fields Jonaki reads from the YAML front matter at the top of a
 * SKILL.md, the format of Anthropic's and pi's skills:
 *
 * ```
 * ---
 * name: report
 * description: Write a report as an HTML page.
 * ---
 * ```
 *
 * Other keys (license, metadata, allowed-tools) are accepted and ignored.
 */
data class SkillFrontMatter(
    val name: String,
    val description: String,
) {
    companion object {
        /** Anthropic's limit; the description goes into every system prompt, so it stays short. */
        const val MAX_DESCRIPTION_LENGTH = 1_024
        const val MAX_NAME_LENGTH = 64

        /** Reads the front matter; a skill that does not follow the format is refused with the reason. */
        fun parse(skillMarkdown: String): FrontMatterResult = FrontMatterReader(skillMarkdown).read()

        /** True for names such as "report" and "bangla-formal-letter"; the name is also the folder name. */
        fun isValidName(name: String): Boolean =
            name.length <= MAX_NAME_LENGTH && NAME_PATTERN.matches(name)

        private val NAME_PATTERN = Regex("[a-z0-9]+(-[a-z0-9]+)*")
    }
}

sealed interface FrontMatterResult {
    data class Parsed(val frontMatter: SkillFrontMatter) : FrontMatterResult

    /** [reason] is shown to the user as it is, so it is a short plain sentence fragment. */
    data class Invalid(val reason: String) : FrontMatterResult
}

/**
 * A small reader for the part of YAML that skill front matter uses: top-level
 * "key: value" lines whose value is plain, quoted, or a block (| or >), with
 * indented lines continuing it. Jonaki has no YAML library and adding one is
 * a decision (D-021); full YAML is not needed for two text fields.
 */
private class FrontMatterReader(skillMarkdown: String) {
    private val lines: List<String> = skillMarkdown.removePrefix(BYTE_ORDER_MARK).replace("\r\n", "\n").split("\n")

    fun read(): FrontMatterResult {
        if (lines.first().trimEnd() != FENCE) {
            return FrontMatterResult.Invalid("SKILL.md must start with a --- line")
        }
        val closingIndex = (1 until lines.size).firstOrNull { index -> lines[index].trimEnd() == FENCE }
            ?: return FrontMatterResult.Invalid("the front matter has no closing --- line")
        val values = mutableMapOf<String, String>()
        var index = 1
        while (index < closingIndex) {
            val line = lines[index]
            if (line.isBlank() || line.trimStart().startsWith("#") || startsIndented(line)) {
                index++
                continue
            }
            val lineNumber = index + 1
            val match = KEY_LINE.matchEntire(line)
                ?: return FrontMatterResult.Invalid("line $lineNumber is not \"key: value\"")
            val key = match.groupValues[1]
            val firstPart = match.groupValues[2].trim()
            val continuation = continuationLines(after = index, before = closingIndex)
            index += 1 + continuation.size
            if (key in values) {
                return FrontMatterResult.Invalid("$key appears twice")
            }
            val value = scalarValue(firstPart, continuation)
                ?: return FrontMatterResult.Invalid("line $lineNumber has an unclosed quote")
            values[key] = value
        }
        return checkedFields(values)
    }

    private fun checkedFields(values: Map<String, String>): FrontMatterResult {
        val name = values["name"]?.trim().orEmpty()
        if (name.isEmpty()) {
            return FrontMatterResult.Invalid("name is missing")
        }
        if (!SkillFrontMatter.isValidName(name)) {
            return FrontMatterResult.Invalid(
                "name must be lowercase letters, digits and single hyphens, " +
                    "at most ${SkillFrontMatter.MAX_NAME_LENGTH} characters",
            )
        }
        val description = values["description"]?.trim().orEmpty()
        if (description.isEmpty()) {
            return FrontMatterResult.Invalid("description is missing")
        }
        if (description.length > SkillFrontMatter.MAX_DESCRIPTION_LENGTH) {
            return FrontMatterResult.Invalid(
                "description has ${description.length} characters; " +
                    "the limit is ${SkillFrontMatter.MAX_DESCRIPTION_LENGTH}",
            )
        }
        return FrontMatterResult.Parsed(SkillFrontMatter(name = name, description = description))
    }

    /** The indented or blank lines that belong to the key on line [after]. */
    private fun continuationLines(after: Int, before: Int): List<String> {
        val continuation = mutableListOf<String>()
        var index = after + 1
        while (index < before && (lines[index].isBlank() || startsIndented(lines[index]))) {
            continuation += lines[index]
            index++
        }
        // Trailing blank lines separate keys; they are not part of the value.
        while (continuation.isNotEmpty() && continuation.last().isBlank()) {
            continuation.removeAt(continuation.lastIndex)
        }
        return continuation
    }

    /** The value's text, or null when a quote is never closed. */
    private fun scalarValue(firstPart: String, continuation: List<String>): String? = when {
        firstPart.startsWith("|") -> literalBlock(continuation)
        firstPart.startsWith(">") -> foldedBlock(continuation)
        firstPart.startsWith("\"") -> doubleQuoted(joinedForQuote(firstPart, continuation))
        firstPart.startsWith("'") -> singleQuoted(joinedForQuote(firstPart, continuation))
        else -> plain(firstPart, continuation)
    }

    private fun joinedForQuote(firstPart: String, continuation: List<String>): String =
        (listOf(firstPart) + continuation.map { line -> line.trim() }).joinToString(" ")

    private fun literalBlock(continuation: List<String>): String =
        withoutCommonIndent(continuation).joinToString("\n")

    /** Lines next to each other join with a space; a blank line becomes a line break. */
    private fun foldedBlock(continuation: List<String>): String {
        val folded = StringBuilder()
        var previousWasText = false
        for (line in withoutCommonIndent(continuation)) {
            if (line.isBlank()) {
                folded.append('\n')
                previousWasText = false
                continue
            }
            if (previousWasText) {
                folded.append(' ')
            }
            folded.append(line)
            previousWasText = true
        }
        return folded.toString()
    }

    private fun withoutCommonIndent(continuation: List<String>): List<String> {
        val indent = continuation.filter { line -> line.isNotBlank() }
            .minOfOrNull { line -> line.length - line.trimStart().length } ?: 0
        return continuation.map { line -> if (line.isBlank()) "" else line.substring(indent).trimEnd() }
    }

    private fun plain(firstPart: String, continuation: List<String>): String {
        val parts = listOf(withoutComment(firstPart)) + continuation.map { line -> line.trim() }
        return parts.filter { part -> part.isNotEmpty() }.joinToString(" ")
    }

    /** In plain YAML values, " #" starts a comment. */
    private fun withoutComment(value: String): String {
        val commentStart = value.indexOf(" #")
        return if (commentStart < 0) value else value.substring(0, commentStart).trimEnd()
    }

    private fun doubleQuoted(text: String): String? {
        val result = StringBuilder()
        var index = 1
        while (index < text.length) {
            val character = text[index]
            if (character == '"') {
                return result.toString()
            }
            if (character == '\\' && index + 1 < text.length) {
                result.append(escaped(text[index + 1]))
                index += 2
                continue
            }
            result.append(character)
            index++
        }
        return null
    }

    private fun escaped(character: Char): String = when (character) {
        'n' -> "\n"
        't' -> "\t"
        '"' -> "\""
        '\\' -> "\\"
        '/' -> "/"
        else -> "\\" + character
    }

    /** In single quotes the only escape is a doubled quote. */
    private fun singleQuoted(text: String): String? {
        val result = StringBuilder()
        var index = 1
        while (index < text.length) {
            val character = text[index]
            if (character == '\'' && index + 1 < text.length && text[index + 1] == '\'') {
                result.append('\'')
                index += 2
                continue
            }
            if (character == '\'') {
                return result.toString()
            }
            result.append(character)
            index++
        }
        return null
    }

    private fun startsIndented(line: String): Boolean = line.startsWith(" ") || line.startsWith("\t")

    private companion object {
        const val FENCE = "---"
        const val BYTE_ORDER_MARK = "﻿"
        val KEY_LINE = Regex("([A-Za-z0-9_.-]+)\\s*:(?:\\s+(.*))?\\s*")
    }
}
