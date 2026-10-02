package app.jonaki.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class CodeTokenizerTest {
    /** Each token as "KIND text", which reads more plainly in a failure than offsets do. */
    private fun tokensOf(code: String, syntax: CodeSyntax): List<String> =
        CodeTokenizer.tokenize(code, syntax).map { token -> "${token.kind} ${code.substring(token.start, token.end)}" }

    @Test
    fun keywordsAreMarkedButIdentifiersThatContainThemAreNot() {
        val tokens = tokensOf("const total = returnValue + 1", CodeSyntax.JAVASCRIPT)

        assertEquals(listOf("KEYWORD const", "NUMBER 1"), tokens)
    }

    @Test
    fun aPropertyNamedLikeAKeywordIsNotAKeyword() {
        val tokens = tokensOf("module.default = options.class", CodeSyntax.JAVASCRIPT)

        assertEquals(emptyList<String>(), tokens)
    }

    @Test
    fun pythonKeywordsAndConstants() {
        val tokens = tokensOf("if value is None and not done:\n    pass", CodeSyntax.PYTHON)

        assertEquals(listOf("KEYWORD if", "KEYWORD is", "KEYWORD None", "KEYWORD and", "KEYWORD not", "KEYWORD pass"), tokens)
    }

    @Test
    fun anEscapedQuoteDoesNotEndTheString() {
        val tokens = tokensOf("""say("a\"b", 'it\'s')""", CodeSyntax.JAVASCRIPT)

        assertEquals(listOf("FUNCTION say", """STRING "a\"b"""", """STRING 'it\'s'"""), tokens)
    }

    @Test
    fun anUnterminatedStringEndsAtTheEndOfItsLine() {
        val tokens = tokensOf("x = \"abc\ny = 2", CodeSyntax.PYTHON)

        assertEquals(listOf("STRING \"abc", "NUMBER 2"), tokens)
    }

    @Test
    fun javascriptCommentsLineAndBlock() {
        val code = "let a = 1 // the start\n/* two\nlines */ let b"

        assertEquals(
            listOf("KEYWORD let", "NUMBER 1", "COMMENT // the start", "COMMENT /* two\nlines */", "KEYWORD let"),
            tokensOf(code, CodeSyntax.JAVASCRIPT),
        )
    }

    @Test
    fun anUnterminatedBlockCommentRunsToTheEnd() {
        assertEquals(listOf("COMMENT /* never closed\nlet x"), tokensOf("/* never closed\nlet x", CodeSyntax.JAVASCRIPT))
    }

    @Test
    fun aHashInsideAStringIsNotAComment() {
        val tokens = tokensOf("tag = \"#1\"  # first", CodeSyntax.PYTHON)

        assertEquals(listOf("STRING \"#1\"", "COMMENT # first"), tokens)
    }

    @Test
    fun aHashIsNotACommentInJavaScript() {
        assertEquals(emptyList<String>(), tokensOf("this.#count", CodeSyntax.JAVASCRIPT).filter { it.startsWith("COMMENT") })
    }

    @Test
    fun aTemplateLiteralSpansLinesAndKeepsItsPlaceholders() {
        val code = "const text = `total:\n\${sum}`;"

        assertEquals(listOf("KEYWORD const", "STRING `total:\n\${sum}`"), tokensOf(code, CodeSyntax.JAVASCRIPT))
    }

    @Test
    fun pythonTripleQuotesSpanLinesAndHoldSingleQuotes() {
        val code = "doc = \"\"\"one \"quoted\"\nline\"\"\"\nx = 1"

        assertEquals(listOf("STRING \"\"\"one \"quoted\"\nline\"\"\"", "NUMBER 1"), tokensOf(code, CodeSyntax.PYTHON))
    }

    @Test
    fun anUnterminatedTripleQuoteRunsToTheEnd() {
        assertEquals(listOf("STRING '''open\nstill open"), tokensOf("'''open\nstill open", CodeSyntax.PYTHON))
    }

    @Test
    fun pythonStringPrefixesBelongToTheString() {
        val code = "print(f\"{name}!\", rb'\\x00', Rf\"\"\"raw\"\"\")"

        assertEquals(
            listOf("FUNCTION print", "STRING f\"{name}!\"", "STRING rb'\\x00'", "STRING Rf\"\"\"raw\"\"\""),
            tokensOf(code, CodeSyntax.PYTHON),
        )
    }

    @Test
    fun aPrefixLetterWithoutAQuoteIsAnIdentifier() {
        assertEquals(emptyList<String>(), tokensOf("f = b + rb", CodeSyntax.PYTHON))
    }

    @Test
    fun numbersInTheirUsualForms() {
        val tokens = tokensOf("a = [0xFF, 1_000, 3.14, 1e-5, .5, 10n]", CodeSyntax.JAVASCRIPT)

        assertEquals(listOf("NUMBER 0xFF", "NUMBER 1_000", "NUMBER 3.14", "NUMBER 1e-5", "NUMBER .5", "NUMBER 10n"), tokens)
    }

    @Test
    fun digitsInsideANameAreNotANumber() {
        assertEquals(emptyList<String>(), tokensOf("row2 = col_3", CodeSyntax.PYTHON))
    }

    @Test
    fun calledNamesAndBuiltInsAreFunctions() {
        val code = "def area(r):\n    return round(math.pi * r ** 2, 2)\nprint(len(rows))"

        assertEquals(
            listOf(
                "KEYWORD def", "FUNCTION area", "KEYWORD return", "FUNCTION round", "NUMBER 2", "NUMBER 2",
                "FUNCTION print", "FUNCTION len",
            ),
            tokensOf(code, CodeSyntax.PYTHON),
        )
    }

    @Test
    fun javascriptBuiltInObjectsAreMarkedWithoutACall() {
        val tokens = tokensOf("console.log(Math.max(a, b))", CodeSyntax.JAVASCRIPT)

        assertEquals(listOf("FUNCTION console", "FUNCTION log", "FUNCTION Math", "FUNCTION max"), tokens)
    }

    @Test
    fun emptyCodeHasNoTokens() {
        assertEquals(emptyList<String>(), tokensOf("", CodeSyntax.PYTHON))
    }
}
