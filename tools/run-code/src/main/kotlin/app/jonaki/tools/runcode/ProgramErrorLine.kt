package app.jonaki.tools.runcode

import app.jonaki.core.runtimeapi.CodeLanguage

/**
 * The line of the program an error points at, read from the error text the
 * engines return, so the code viewer can mark it (D-090). Null when the
 * text names no line of the program itself.
 */
object ProgramErrorLine {
    /** Pyodide runs the program as main.py (runtimes/pyodide, python-worker.js). */
    private val pythonFrame = Regex("""File "main\.py", line (\d+)""")

    /** The last "line:column)" of a frame, which for evaluated code is the place inside the program. */
    private val javascriptPosition = Regex(""":(\d+):\d+\)""")

    fun of(language: CodeLanguage, errorText: String): Int? = when (language) {
        CodeLanguage.PYTHON -> pythonLine(errorText)
        CodeLanguage.JAVASCRIPT -> javascriptLine(errorText)
    }

    /** The last frame in main.py is the innermost one, where the error happened. */
    private fun pythonLine(errorText: String): Int? =
        pythonFrame.findAll(errorText).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()

    /**
     * The runner evaluates the program with eval, so V8 names the program's
     * frames "eval at …, <anonymous>:line:column". Frames without "eval at"
     * belong to the runner itself, whose lines mean nothing to the user.
     */
    private fun javascriptLine(errorText: String): Int? {
        val programFrame = errorText.lines().firstOrNull { line -> line.trimStart().startsWith("at ") && "eval at" in line }
            ?: return null
        return javascriptPosition.findAll(programFrame).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
    }
}
