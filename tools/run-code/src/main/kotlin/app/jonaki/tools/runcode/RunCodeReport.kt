package app.jonaki.tools.runcode

import app.jonaki.core.runtimeapi.CodeLanguage

/** A file the program wrote that run_code did not save, with run_code's reason. */
data class NotSavedFile(val path: String, val reason: String)

/**
 * run_code's reply to the model read back into its parts, for the code
 * viewer (D-090). The labels are shared with [RunCodeTool], which writes
 * them. Printed text is free text, so a program that prints a label line
 * itself (for example "Result: 5" with no result of its own) is read
 * wrongly; the later sections are searched from the end to make that rare.
 */
data class RunCodeReport(
    val printed: String,
    val printedToStderr: String,
    val result: String?,
    /** The program's error, the timeout, or why run_code could not run it; null when it ran without one. */
    val error: String?,
    val timedOut: Boolean,
    val savedPaths: List<String>,
    val notSaved: List<NotSavedFile>,
) {
    companion object {
        internal const val FINISHED = "finished."
        internal const val STOPPED_WITH_ERROR = "stopped with an error."
        internal const val PRINTED = "\nPrinted:\n"
        internal const val PRINTED_TO_STDERR = "\nPrinted to stderr:\n"
        internal const val RESULT = "\nResult: "
        internal const val ERROR = "\nError:\n"
        internal const val NO_OUTPUT = "No output and no result."
        internal const val SAVED = "Saved: "
        internal const val NOT_SAVED = "Not saved: "
        internal const val FIX_AND_RETRY = "Fix the program and call run_code again."
        internal const val TIMED_OUT_START = "Error: the program ran longer than"
        private const val ERROR_START = "Error: "

        private val savedLine = Regex("^$SAVED" + """(.+) \((new|replaced), \d+ bytes\)$""")
        private val notSavedLine = Regex("^$NOT_SAVED" + """(.+) \(([^()]*)\)$""")

        fun parse(text: String): RunCodeReport {
            val lines = text.trimEnd().lines().toMutableList()
            val savedPaths = mutableListOf<String>()
            val notSaved = mutableListOf<NotSavedFile>()
            takeClosingLines(lines, savedPaths, notSaved)
            val body = lines.joinToString("\n")
            val header = lines.firstOrNull().orEmpty()
            val sections = when {
                header.startsWith(TIMED_OUT_START) -> sectionsOf(body.substringAfter('\n', ""), firstError = header.removePrefix(ERROR_START))
                isFinishedHeader(header) -> sectionsOf(body.substringAfter('\n', ""), firstError = null)
                text.startsWith(ERROR_START) -> Sections(printed = "", stderr = "", result = null, error = text.removePrefix(ERROR_START).trim())
                else -> Sections(printed = text.trim(), stderr = "", result = null, error = null)
            }
            return RunCodeReport(
                printed = sections.printed,
                printedToStderr = sections.stderr,
                result = sections.result,
                error = sections.error,
                timedOut = header.startsWith(TIMED_OUT_START),
                savedPaths = savedPaths.reversed(),
                notSaved = notSaved.reversed(),
            )
        }

        /** Removes the file lines and closing remarks from the end of [lines], collecting the files. */
        private fun takeClosingLines(lines: MutableList<String>, savedPaths: MutableList<String>, notSaved: MutableList<NotSavedFile>) {
            while (lines.size > 1) {
                val last = lines.last()
                val saved = savedLine.find(last)
                val refused = notSavedLine.find(last)
                when {
                    last == FIX_AND_RETRY || last == NO_OUTPUT -> Unit
                    saved != null -> savedPaths += saved.groupValues[1]
                    refused != null -> notSaved += NotSavedFile(refused.groupValues[1], refused.groupValues[2])
                    else -> return
                }
                lines.removeAt(lines.lastIndex)
            }
        }

        private fun isFinishedHeader(header: String): Boolean = CodeLanguage.entries.any { language ->
            header == "${language.displayName} $FINISHED" || header == "${language.displayName} $STOPPED_WITH_ERROR"
        }

        private class Sections(val printed: String, val stderr: String, val result: String?, val error: String?)

        /** [afterHeader] starts with a newline, as each section's label does. */
        private fun sectionsOf(afterHeader: String, firstError: String?): Sections {
            val withNewline = "\n$afterHeader"
            val errorStart = withNewline.lastIndexOf(ERROR)
            val resultStart = withNewline.lastIndexOf(RESULT, startIndex = endOf(errorStart, withNewline))
            val stderrStart = withNewline.lastIndexOf(PRINTED_TO_STDERR, startIndex = endOf(firstOf(resultStart, errorStart), withNewline))
            val printedStart = if (withNewline.startsWith(PRINTED)) 0 else -1
            val ends = listOf(stderrStart, resultStart, errorStart, withNewline.length)
            fun section(start: Int, label: String): String? {
                if (start < 0) {
                    return null
                }
                val end = ends.filter { candidate -> candidate > start }.min()
                return withNewline.substring(start + label.length, end).trimEnd()
            }
            val programError = section(errorStart, ERROR)
            return Sections(
                printed = section(printedStart, PRINTED).orEmpty(),
                stderr = section(stderrStart, PRINTED_TO_STDERR).orEmpty(),
                result = section(resultStart, RESULT),
                error = programError ?: firstError,
            )
        }

        /** The index to search back from: just before [start], or the end of [text] when there is no such section. */
        private fun endOf(start: Int, text: String): Int = if (start < 0) text.length else start - 1

        private fun firstOf(first: Int, second: Int): Int = if (first >= 0) first else second
    }
}
