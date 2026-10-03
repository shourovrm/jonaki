package app.jonaki.ui

import app.jonaki.core.runtimeapi.CodeLanguage
import app.jonaki.core.storage.MessageDao
import app.jonaki.core.storage.StepEntity
import app.jonaki.core.storage.StepStatus
import app.jonaki.core.toolapi.OutputLimiter
import app.jonaki.feature.chat.CodeRunUi
import app.jonaki.feature.chat.CodeSyntax
import app.jonaki.feature.chat.NotSavedFileUi
import app.jonaki.run.RunSession
import app.jonaki.tools.runcode.ProgramErrorLine
import app.jonaki.tools.runcode.RunCodeReport
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The code sheet of one run_code step (D-090). The code comes from the
 * step's arguments. The output comes from the tool row the model got, which
 * is whole, because the step keeps only a 2,000-character preview; when
 * OutputLimiter cut that text too, the whole report is read from the file
 * it was saved to.
 */
object CodeRunDetails {
    /** Printed text beyond this is not shown: a Text this long already takes seconds to lay out. */
    const val MAX_SHOWN_CHARACTERS = 50_000

    /** Two streams of 1,000,000 characters each (CappedText) fit with room to spare. */
    private const val MAX_SPILL_FILE_BYTES = 8L * 1024 * 1024

    suspend fun load(messageDao: MessageDao, threadFolder: File, step: StepEntity): CodeRunUi {
        val toolResultText = messageDao.findToolResult(step.toolCallId)?.text
        return withContext(Dispatchers.IO) {
            of(step, toolResultText) { relativePath -> readInside(threadFolder, relativePath) }
        }
    }

    /** Null for a missing or oversized file, and for a path that leads out of the thread folder. */
    private fun readInside(threadFolder: File, relativePath: String): String? {
        val file = File(threadFolder, relativePath).canonicalFile
        val isInside = file.path.startsWith(threadFolder.canonicalPath + File.separator)
        if (!isInside || !file.isFile || file.length() > MAX_SPILL_FILE_BYTES) {
            return null
        }
        return file.readText()
    }

    /**
     * [toolResultText] is the TOOL row's text, null when there is none (a
     * subagent's call, or a step still running). [readThreadFile] reads a
     * file of the thread by its relative path, null when it is missing.
     */
    fun of(step: StepEntity, toolResultText: String?, readThreadFile: (String) -> String?): CodeRunUi {
        val arguments = runCatching { Json.parseToJsonElement(step.argumentsJson) as? JsonObject }.getOrNull()
        val code = arguments?.text("code").orEmpty().trimEnd('\n', '\r')
        val languageText = arguments?.text("language").orEmpty()
        val language = CodeLanguage.fromArgument(languageText)
        val isRunning = step.status == StepStatus.RUNNING.name || step.status == StepStatus.WAITING_FOR_APPROVAL.name
        val output = if (isRunning) null else outputOf(step, toolResultText, readThreadFile)
        val report = output?.report
        val printed = shown(report?.printed.orEmpty())
        val printedToStderr = shown(report?.printedToStderr.orEmpty())
        val error = report?.error
        return CodeRunUi(
            stepId = step.toolCallId,
            syntax = language?.let(::syntaxOf),
            languageName = language?.displayName ?: languageText,
            code = code,
            isRunning = isRunning,
            printed = printed.text,
            printedToStderr = printedToStderr.text,
            result = report?.result,
            error = error,
            errorLine = if (language != null && error != null) ProgramErrorLine.of(language, error) else null,
            savedFiles = report?.savedPaths.orEmpty(),
            notSavedFiles = report?.notSaved.orEmpty().map { file -> NotSavedFileUi(file.path, file.reason) },
            outputIsCut = output?.isCut == true || printed.isCut || printedToStderr.isCut,
        )
    }

    private class Output(val report: RunCodeReport, val isCut: Boolean)

    private fun outputOf(step: StepEntity, toolResultText: String?, readThreadFile: (String) -> String?): Output? {
        if (toolResultText == null) {
            val preview = step.resultText ?: return null
            val isCut = preview.length >= RunSession.STEP_RESULT_PREVIEW_LENGTH
            return Output(RunCodeReport.parse(OutputLimiter.visiblePartOf(preview)), isCut)
        }
        val spillPath = OutputLimiter.spillPathOf(toolResultText) ?: return Output(RunCodeReport.parse(toolResultText), isCut = false)
        val wholeText = readThreadFile(spillPath)
        if (wholeText != null) {
            return Output(RunCodeReport.parse(wholeText), isCut = false)
        }
        return Output(RunCodeReport.parse(OutputLimiter.visiblePartOf(toolResultText)), isCut = true)
    }

    private class Shown(val text: String, val isCut: Boolean)

    private fun shown(text: String): Shown {
        if (text.length <= MAX_SHOWN_CHARACTERS) {
            return Shown(text, isCut = false)
        }
        return Shown(text.substring(0, MAX_SHOWN_CHARACTERS), isCut = true)
    }

    private fun syntaxOf(language: CodeLanguage): CodeSyntax = when (language) {
        CodeLanguage.JAVASCRIPT -> CodeSyntax.JAVASCRIPT
        CodeLanguage.PYTHON -> CodeSyntax.PYTHON
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.content
}
