package app.jonaki.tools.exportpdf

import app.jonaki.core.toolapi.Capability
import app.jonaki.core.toolapi.IncomingFiles
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import app.jonaki.core.toolapi.stringArgument
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Makes a PDF file in the thread folder from an HTML report or slide deck. */
class ExportPdfTool(private val renderer: PdfRenderer) : Tool {
    override val name: String = "export_pdf"

    override val promptLine: String = "export_pdf: turn an HTML report or slide deck of the thread into a PDF file"

    override val guidelines: List<String> = listOf(
        "Use export_pdf only when the user asks for a PDF. The HTML file must exist first; write it with the artifact tool.",
        "Use page slides for a slide deck (16:9, one slide per page); a4 is the default.",
        "export_pdf only makes the file. Use share_file to save it to Downloads or share it.",
    )

    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "The HTML file, relative to the thread folder, for example artifacts/report.html")
            }
            putJsonObject("output") {
                put("type", "string")
                put(
                    "description",
                    "The PDF file, relative to the thread folder. Default: the HTML file's name with .pdf, in the same folder",
                )
            }
            putJsonObject("page") {
                put("type", "string")
                putJsonArray("enum") {
                    for (pageSize in PdfPageSize.entries) {
                        add(pageSize.argument)
                    }
                }
                put("description", "Paper: a4 (default), letter, or slides for a 16:9 deck")
            }
        }
        putJsonArray("required") { add(JsonPrimitive("path")) }
    }

    override val sideEffect: SideEffect = SideEffect.CHANGES_THREAD_FOLDER
    override val requiredCapabilities: Set<Capability> = emptySet()

    /** Longer than the render limit, so that a slow page fails with its own message first. */
    override val timeLimit: Duration = 60.seconds

    override suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput {
        val path = arguments.stringArgument("path")?.trim().orEmpty()
        if (path.isEmpty()) {
            return ToolOutput.error(
                "argument path is missing",
                "Call export_pdf with the HTML file, for example artifacts/report.html.",
            )
        }
        val pageArgument = arguments.stringArgument("page")
        val pageSize = pageSizeOf(pageArgument)
            ?: return ToolOutput.error(
                "page $pageArgument is unknown",
                "Use one of: ${PdfPageSize.entries.joinToString(", ") { it.argument }}.",
            )
        val htmlFile = context.paths.resolve(path)
            ?: return ToolOutput.error(
                "$path is outside the thread folder",
                "Use an HTML file of this thread, for example artifacts/report.html.",
            )
        val htmlProblem = problemWithHtml(htmlFile, path, context)
        if (htmlProblem != null) {
            return htmlProblem
        }
        val outputArgument = arguments.stringArgument("output")?.trim().orEmpty()
        val outputFile = if (outputArgument.isEmpty()) {
            File(htmlFile.parentFile, htmlFile.nameWithoutExtension + ".pdf")
        } else {
            context.paths.resolve(outputArgument)
                ?: return ToolOutput.error(
                    "output $outputArgument is outside the thread folder",
                    "Use a path inside the thread folder, for example artifacts/report.pdf.",
                )
        }
        if (outputFile.isDirectory || !outputFile.name.endsWith(".pdf", ignoreCase = true)) {
            return ToolOutput.error(
                "output ${context.paths.relativePath(outputFile)} is not a .pdf file name",
                "Give a file name ending in .pdf, for example artifacts/report.pdf.",
            )
        }
        return export(context.paths.relativePath(htmlFile), outputFile, pageSize, context)
    }

    private fun pageSizeOf(argument: String?): PdfPageSize? {
        if (argument.isNullOrBlank()) {
            return PdfPageSize.A4
        }
        return PdfPageSize.entries.firstOrNull { pageSize -> pageSize.argument == argument.trim().lowercase() }
    }

    /**
     * The page loader serves only artifacts/, as the artifact viewer does, so
     * a page elsewhere would print without its styles and images; refusing
     * it is clearer than a plain PDF.
     */
    private fun problemWithHtml(htmlFile: File, path: String, context: ToolContext): ToolOutput? {
        if (htmlFile.isDirectory) {
            return ToolOutput.error("$path is a folder", "Give the HTML file, for example $path/report.html.")
        }
        if (!htmlFile.isFile) {
            return ToolOutput.error(
                "$path does not exist",
                "Write the HTML file with the artifact tool first, or use find_files to see the thread's files.",
            )
        }
        val extension = htmlFile.extension.lowercase()
        if (extension != "html" && extension != "htm") {
            return ToolOutput.error(
                "$path is not an HTML file",
                "export_pdf only converts .html files; write the page with the artifact tool first.",
            )
        }
        if (!context.paths.relativePath(htmlFile).startsWith("$ARTIFACTS_FOLDER/")) {
            return ToolOutput.error(
                "$path is not in the $ARTIFACTS_FOLDER folder",
                "Write the page with the artifact tool so that it is saved under $ARTIFACTS_FOLDER/, then call export_pdf again.",
            )
        }
        return null
    }

    private suspend fun export(htmlPath: String, outputFile: File, pageSize: PdfPageSize, context: ToolContext): ToolOutput {
        // The renderer writes a side file, so that a failure never leaves half a PDF or destroys an older one.
        val partFile = File(outputFile.parentFile, outputFile.name + ".part")
        try {
            withContext(Dispatchers.IO) {
                outputFile.parentFile?.mkdirs()
                partFile.delete()
            }
            val rendered = renderer.render(context.threadFolder, htmlPath, partFile, pageSize, RENDER_LIMIT)
            val sizeBytes = withContext(Dispatchers.IO) { partFile.length() }
            if (sizeBytes == 0L) {
                return ToolOutput.error(
                    "the PDF of $htmlPath came out empty",
                    "Check that the page has visible content, then call export_pdf again.",
                )
            }
            withContext(Dispatchers.IO) { partFile.copyTo(outputFile, overwrite = true) }
            return ToolOutput.success(successText(context.paths.relativePath(outputFile), rendered, sizeBytes))
        } catch (failure: PdfRenderException) {
            return ToolOutput.error(
                "could not make a PDF of $htmlPath: ${failure.message}",
                "Try export_pdf again once; if it fails again, tell the user the PDF can be saved with the Print button of the artifact viewer.",
            )
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { partFile.delete() }
        }
    }

    private fun successText(relativeOutput: String, rendered: RenderedPdf, sizeBytes: Long): String {
        val size = IncomingFiles.describeSize(sizeBytes)
        val pageCount = rendered.pageCount
        val description = if (pageCount == null) size else "${pages(pageCount)}, $size"
        return "Created $relativeOutput ($description). " +
            "Use share_file with action downloads to save it to Downloads, or action share to send it."
    }

    private fun pages(count: Int): String = if (count == 1) "1 page" else "$count pages"

    private companion object {
        const val ARTIFACTS_FOLDER = "artifacts"

        /** Leaves the tool's own limit a few seconds to report. */
        val RENDER_LIMIT = 50.seconds
    }
}
