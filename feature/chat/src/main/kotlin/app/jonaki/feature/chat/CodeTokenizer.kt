package app.jonaki.feature.chat

/** The languages run_code takes; the code viewer colours them (D-090). */
enum class CodeSyntax {
    JAVASCRIPT,
    PYTHON,
}

enum class CodeTokenKind {
    KEYWORD,
    STRING,
    NUMBER,
    COMMENT,

    /** A called name, or a built-in such as print or console. */
    FUNCTION,
}

/** A coloured stretch of the code, from [start] up to but not including [end]. */
data class CodeToken(val kind: CodeTokenKind, val start: Int, val end: Int)

/**
 * A small scanner for colouring JavaScript and Python, written for the code
 * viewer instead of adding a highlighting library (D-090). It knows comments,
 * strings (escapes, template literals, Python prefixes and triple quotes),
 * numbers, keywords and called names; everything else stays plain. It never
 * fails: an unterminated string ends at its line, an unterminated comment or
 * triple-quoted string at the end of the code.
 */
object CodeTokenizer {
    fun tokenize(code: String, syntax: CodeSyntax): List<CodeToken> = Scanner(code, syntax).tokens()
}

private class Scanner(private val code: String, private val syntax: CodeSyntax) {
    private val found = mutableListOf<CodeToken>()
    private var position = 0

    fun tokens(): List<CodeToken> {
        while (position < code.length) {
            val start = position
            val character = code[position]
            when {
                startsLineComment() -> mark(CodeTokenKind.COMMENT, start, lineEndFrom(position))
                syntax == CodeSyntax.JAVASCRIPT && code.startsWith("/*", position) -> mark(CodeTokenKind.COMMENT, start, blockCommentEnd())
                character in quoteCharacters() -> mark(CodeTokenKind.STRING, start, stringEnd(position))
                startsNumber() -> mark(CodeTokenKind.NUMBER, start, numberEnd())
                isNameStart(character) -> scanName()
                else -> position++
            }
        }
        return found
    }

    private fun mark(kind: CodeTokenKind, start: Int, end: Int) {
        found += CodeToken(kind, start, end)
        position = end
    }

    private fun startsLineComment(): Boolean = when (syntax) {
        CodeSyntax.JAVASCRIPT -> code.startsWith("//", position)
        CodeSyntax.PYTHON -> code[position] == '#'
    }

    private fun quoteCharacters(): String = when (syntax) {
        CodeSyntax.JAVASCRIPT -> "\"'`"
        CodeSyntax.PYTHON -> "\"'"
    }

    private fun lineEndFrom(index: Int): Int {
        val newline = code.indexOf('\n', index)
        return if (newline < 0) code.length else newline
    }

    private fun blockCommentEnd(): Int {
        val close = code.indexOf("*/", position + 2)
        return if (close < 0) code.length else close + 2
    }

    /** Where the string opening at [openIndex] ends, its closing quote included. */
    private fun stringEnd(openIndex: Int): Int {
        val quote = code[openIndex]
        val tripleQuote = "$quote$quote$quote"
        if (syntax == CodeSyntax.PYTHON && code.startsWith(tripleQuote, openIndex)) {
            return closingIndex(openIndex + 3, tripleQuote, stopsAtLineEnd = false)
        }
        // A template literal may span lines; ordinary quotes stop at the end of their line.
        val isTemplate = quote == '`'
        return closingIndex(openIndex + 1, quote.toString(), stopsAtLineEnd = !isTemplate)
    }

    private fun closingIndex(from: Int, closing: String, stopsAtLineEnd: Boolean): Int {
        var index = from
        while (index < code.length) {
            val character = code[index]
            when {
                character == '\\' -> index += 2
                character == '\n' && stopsAtLineEnd -> return index
                code.startsWith(closing, index) -> return index + closing.length
                else -> index++
            }
        }
        return code.length
    }

    private fun startsNumber(): Boolean {
        val character = code[position]
        if (character.isDigit()) {
            return true
        }
        return character == '.' && code.getOrNull(position + 1)?.isDigit() == true
    }

    /** Takes in hex digits, separators, exponents and suffixes such as 0xFF, 1_000, 1e-5 and 10n alike. */
    private fun numberEnd(): Int {
        var index = position
        while (index < code.length) {
            val character = code[index]
            val isExponentSign = (character == '+' || character == '-') && code[index - 1] in "eE" && !isHexNumber()
            if (character.isLetterOrDigit() || character == '_' || character == '.' || isExponentSign) {
                index++
            } else {
                break
            }
        }
        return index
    }

    private fun isHexNumber(): Boolean = code.startsWith("0x", position) || code.startsWith("0X", position)

    private fun isNameStart(character: Char): Boolean =
        character.isLetter() || character == '_' || (character == '$' && syntax == CodeSyntax.JAVASCRIPT)

    private fun isNamePart(character: Char): Boolean = isNameStart(character) || character.isDigit()

    private fun scanName() {
        val start = position
        var end = position
        while (end < code.length && isNamePart(code[end])) {
            end++
        }
        val name = code.substring(start, end)
        val isStringPrefix = syntax == CodeSyntax.PYTHON &&
            name.lowercase() in pythonStringPrefixes &&
            code.getOrNull(end) in listOf('"', '\'')
        if (isStringPrefix) {
            mark(CodeTokenKind.STRING, start, stringEnd(end))
            return
        }
        val kind = nameKind(name, start, end)
        if (kind == null) {
            position = end
        } else {
            mark(kind, start, end)
        }
    }

    /** A property after a dot is never a keyword, but may still be a call: options.default, rows.sort(). */
    private fun nameKind(name: String, start: Int, end: Int): CodeTokenKind? {
        val isProperty = previousVisibleCharacter(start) == '.'
        val keywords = if (syntax == CodeSyntax.JAVASCRIPT) javascriptKeywords else pythonKeywords
        val builtIns = if (syntax == CodeSyntax.JAVASCRIPT) javascriptBuiltIns else pythonBuiltIns
        if (!isProperty && name in keywords) {
            return CodeTokenKind.KEYWORD
        }
        if (nextVisibleCharacter(end) == '(') {
            return CodeTokenKind.FUNCTION
        }
        if (!isProperty && name in builtIns) {
            return CodeTokenKind.FUNCTION
        }
        return null
    }

    private fun previousVisibleCharacter(before: Int): Char? {
        var index = before - 1
        while (index >= 0 && code[index] == ' ') {
            index--
        }
        return code.getOrNull(index)
    }

    private fun nextVisibleCharacter(from: Int): Char? {
        var index = from
        while (index < code.length && code[index] == ' ') {
            index++
        }
        return code.getOrNull(index)
    }
}

private val pythonStringPrefixes = setOf("r", "u", "b", "f", "br", "rb", "fr", "rf")

private val javascriptKeywords = setOf(
    "async", "await", "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete",
    "do", "else", "export", "extends", "false", "finally", "for", "function", "if", "import", "in", "instanceof",
    "let", "new", "null", "of", "return", "static", "super", "switch", "this", "throw", "true", "try", "typeof",
    "undefined", "var", "void", "while", "with", "yield", "NaN", "Infinity",
)

private val javascriptBuiltIns = setOf(
    "Array", "BigInt", "Boolean", "Date", "Error", "JSON", "Map", "Math", "Number", "Object", "Promise",
    "RegExp", "Set", "String", "Symbol", "console", "files", "isNaN", "parseFloat", "parseInt",
)

private val pythonKeywords = setOf(
    "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class", "continue",
    "def", "del", "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in", "is",
    "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with", "yield",
)

private val pythonBuiltIns = setOf(
    "abs", "all", "any", "bool", "dict", "enumerate", "filter", "float", "format", "input", "int", "isinstance",
    "len", "list", "map", "max", "min", "open", "print", "range", "repr", "reversed", "round", "set", "sorted",
    "str", "sum", "super", "tuple", "type", "zip",
)
