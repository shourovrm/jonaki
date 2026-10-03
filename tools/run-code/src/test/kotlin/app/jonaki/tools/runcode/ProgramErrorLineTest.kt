package app.jonaki.tools.runcode

import app.jonaki.core.runtimeapi.CodeLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgramErrorLineTest {
    @Test
    fun pythonTakesTheInnermostFrameOfTheProgram() {
        val traceback = """
            Traceback (most recent call last):
              File "main.py", line 7, in <module>
                print(average([]))
              File "main.py", line 3, in average
                return sum(values) / len(values)
            ZeroDivisionError: division by zero
        """.trimIndent()

        assertEquals(3, ProgramErrorLine.of(CodeLanguage.PYTHON, traceback))
    }

    @Test
    fun pythonSyntaxErrorsNameTheirLineToo() {
        val traceback = "  File \"main.py\", line 12\n    total = (1 +\n            ^\nSyntaxError: '(' was never closed"

        assertEquals(12, ProgramErrorLine.of(CodeLanguage.PYTHON, traceback))
    }

    @Test
    fun pythonFramesOutsideTheProgramAreIgnored() {
        val traceback = "  File \"/lib/python3.14/json/decoder.py\", line 345, in decode\nJSONDecodeError: Expecting value"

        assertNull(ProgramErrorLine.of(CodeLanguage.PYTHON, traceback))
    }

    @Test
    fun javascriptTakesTheLineInsideTheEvaluatedProgram() {
        val stack = "TypeError: Cannot read properties of undefined (reading 'length')\n" +
            "    at eval (eval at <anonymous> (<anonymous>:84:22), <anonymous>:4:17)"

        assertEquals(4, ProgramErrorLine.of(CodeLanguage.JAVASCRIPT, stack))
    }

    @Test
    fun javascriptErrorsInsideAFunctionOfTheProgram() {
        val stack = "ReferenceError: totl is not defined\n" +
            "    at average (eval at <anonymous> (<anonymous>:84:22), <anonymous>:9:3)"

        assertEquals(9, ProgramErrorLine.of(CodeLanguage.JAVASCRIPT, stack))
    }

    @Test
    fun javascriptFramesOfTheRunnerAreIgnored() {
        // files.read throws from the runner, and syntax errors point at the runner's eval call.
        assertNull(ProgramErrorLine.of(CodeLanguage.JAVASCRIPT, "Error: inbox/a.csv was not given\n    at Object.read (<anonymous>:60:13)"))
        assertNull(ProgramErrorLine.of(CodeLanguage.JAVASCRIPT, "SyntaxError: Unexpected token ')'\n    at <anonymous>:84:22"))
    }

    @Test
    fun anErrorWithoutAStackHasNoLine() {
        assertNull(ProgramErrorLine.of(CodeLanguage.JAVASCRIPT, "oops"))
        assertNull(ProgramErrorLine.of(CodeLanguage.PYTHON, "the program ran longer than 120 seconds"))
    }
}
