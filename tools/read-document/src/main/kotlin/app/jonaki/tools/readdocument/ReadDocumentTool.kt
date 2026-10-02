package app.jonaki.tools.readdocument

import app.jonaki.core.toolapi.Capability
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import app.jonaki.core.toolapi.intArgument
import app.jonaki.core.toolapi.stringArgument
import java.io.File
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Reads the text of PDF, Word, Excel and PowerPoint files in the thread
 * folder (D-051, D-052). PDF pages, slides and sheets come a window at a
 * time with offset and limit; long output is saved to a file.
 */
class ReadDocumentTool : Tool {
    override val name: String = "read_document"
    override val promptLine: String =
        "read_document: read the text of a PDF, Word, Excel or PowerPoint file (.pdf, .docx, .xlsx, .pptx) in the thread folder"
    override val guidelines: List<String> = listOf(
        "Use read_document, not read_file, for .pdf, .docx, .xlsx and .pptx files; continue with the offset the notice names.",
    )
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "Path relative to the thread folder, for example inbox/report.pdf")
            }
            putJsonObject("offset") {
                put("type", "integer")
                put("description", "First page (PDF), slide (PowerPoint) or sheet (Excel) to show, counting from 1. Default 1.")
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "Most pages, slides or sheets to show. Default $DEFAULT_UNIT_LIMIT.")
            }
        }
        putJsonArray("required") { add(JsonPrimitive("path")) }
    }
    override val sideEffect: SideEffect = SideEffect.READ_ONLY
    override val requiredCapabilities: Set<Capability> = emptySet()
    override val timeLimit: Duration = 60.seconds

    override suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput = withContext(Dispatchers.IO) {
        val path = arguments.stringArgument("path")
            ?: return@withContext ToolOutput.error("argument path is missing", "Call read_document with a path.")
        val file = context.paths.resolve(path)
            ?: return@withContext ToolOutput.error("$path is outside the thread folder", "Use a path inside the thread folder.")
        if (!file.isFile) {
            return@withContext ToolOutput.error("$path does not exist", "Use find_files to list the files.")
        }
        val offset = (arguments.intArgument("offset") ?: 1).coerceAtLeast(1)
        val limit = (arguments.intArgument("limit") ?: DEFAULT_UNIT_LIMIT).coerceIn(1, MAX_UNIT_LIMIT)
        val window = try {
            readWindow(file, path, offset, limit)
        } catch (unreadable: UnreadableDocumentException) {
            return@withContext ToolOutput.error(unreadable.whatFailed, unreadable.whatToTryNext)
        } catch (failure: IOException) {
            return@withContext ToolOutput.error(
                "$path could not be read (${failure.message})",
                "The file may be damaged; ask the user for another copy.",
            )
        }
        if (window.unitName != null && window.totalUnits == 0) {
            return@withContext ToolOutput.error("$path has no ${window.unitName}s", "The file holds nothing to read.")
        }
        if (window.unitName != null && offset > window.totalUnits) {
            return@withContext ToolOutput.error(
                "offset $offset is past the end; $path has ${window.totalUnits} ${window.unitName}s",
                "Use an offset between 1 and ${window.totalUnits}.",
            )
        }
        val text = describe(path, window, offset)
        ToolOutput.success(context.outputLimiter.limit(text, MAX_CHARACTERS, name))
    }

    private fun readWindow(file: File, path: String, offset: Int, limit: Int): DocumentWindow {
        val ending = file.extension.lowercase()
        return when (ending) {
            "pdf" -> PdfText.read(file, offset, limit)
            "docx", "docm" -> WordText.read(file)
            "xlsx", "xlsm" -> SheetText.read(file, offset, limit)
            "pptx", "pptm" -> SlideText.read(file, offset, limit)
            "doc", "xls", "ppt" -> throw UnreadableDocumentException(
                "$path is an old Office file (.$ending), which read_document cannot read",
                "Ask the user to save it as .${ending}x or PDF and send it again.",
            )
            else -> throw UnreadableDocumentException(
                "read_document reads .pdf, .docx, .xlsx and .pptx files, not .$ending",
                "Use read_file for text files.",
            )
        }
    }

    private fun describe(path: String, window: DocumentWindow, offset: Int): String {
        val unitName = window.unitName
        if (unitName == null) {
            val body = window.sections.single().text
            return "$path: ${window.kindName}.\n\n" + body.ifEmpty { "[The document has no text.]" }
        }
        val output = StringBuilder()
        output.append("$path: ${window.kindName}, ${window.totalUnits} ${plural(unitName, window.totalUnits)}.")
        for (section in window.sections) {
            output.append("\n\n--- ${section.heading} ---\n")
            output.append(section.text.ifEmpty { emptyNote(path, unitName, section.number) })
        }
        val lastShown = window.sections.lastOrNull()?.number ?: (offset - 1)
        if (lastShown < window.totalUnits) {
            output.append(
                "\n\n[Showing ${unitName}s $offset-$lastShown of ${window.totalUnits}. " +
                    "Use read_document path=\"$path\" offset=${lastShown + 1} to continue.]",
            )
        }
        return output.toString()
    }

    private fun emptyNote(path: String, unitName: String, number: Int): String {
        if (unitName != "page") {
            return "[No text on this $unitName.]"
        }
        return "[No text on this page; it may be a scan or a picture. " +
            "If view_image is available, call view_image path=\"$path\" page=$number to look at it.]"
    }

    private fun plural(unitName: String, count: Int): String = if (count == 1) unitName else "${unitName}s"

    private companion object {
        const val DEFAULT_UNIT_LIMIT = 20
        const val MAX_UNIT_LIMIT = 100
        const val MAX_CHARACTERS = 30_000
    }
}
