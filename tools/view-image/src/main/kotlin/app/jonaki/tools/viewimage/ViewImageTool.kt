package app.jonaki.tools.viewimage

import app.jonaki.core.toolapi.Capability
import app.jonaki.core.toolapi.ImageSource
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import app.jonaki.core.toolapi.ViewedImages
import app.jonaki.core.toolapi.intArgument
import app.jonaki.core.toolapi.stringArgument
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
 * Lets the model look at an image or a PDF page in the thread folder
 * (D-050). The tool only checks the path and answers with a fixed text;
 * before the next request the agent loop sends the image itself as a user
 * message, because tool results carry only text in both wire formats.
 * Offered only to models that take images.
 */
class ViewImageTool : Tool {
    override val name: String = ViewedImages.TOOL_NAME
    override val promptLine: String = "view_image: look at an image or one page of a PDF in the thread folder"
    override val guidelines: List<String> = listOf(
        "Images the user attaches are already in the message; use view_image for other images and for PDF pages without text.",
    )
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "Path relative to the thread folder, for example inbox/photo.jpg or inbox/scan.pdf")
            }
            putJsonObject("page") {
                put("type", "integer")
                put("description", "For a PDF: the page to look at, counting from 1. Default 1.")
            }
        }
        putJsonArray("required") { add(JsonPrimitive("path")) }
    }
    override val sideEffect: SideEffect = SideEffect.READ_ONLY
    override val requiredCapabilities: Set<Capability> = emptySet()
    override val timeLimit: Duration = 10.seconds

    override suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput = withContext(Dispatchers.IO) {
        val path = arguments.stringArgument("path")
            ?: return@withContext ToolOutput.error("argument path is missing", "Call view_image with a path.")
        val file = context.paths.resolve(path)
            ?: return@withContext ToolOutput.error("$path is outside the thread folder", "Use a path inside the thread folder.")
        if (!file.isFile) {
            return@withContext ToolOutput.error("$path does not exist", "Use find_files to list the files.")
        }
        val relativePath = context.paths.relativePath(file)
        if (ViewedImages.isImagePath(relativePath)) {
            return@withContext ToolOutput.success(ViewedImages.resultText(ImageSource(relativePath)))
        }
        if (relativePath.endsWith(".pdf", ignoreCase = true)) {
            val page = arguments.intArgument("page") ?: 1
            if (page < 1) {
                return@withContext ToolOutput.error("page $page does not exist", "Pages count from 1.")
            }
            return@withContext ToolOutput.success(ViewedImages.resultText(ImageSource(relativePath, page)))
        }
        ToolOutput.error(
            "$path is not an image",
            "view_image shows ${ViewedImages.IMAGE_EXTENSIONS.sorted().joinToString(", ")} files and PDF pages.",
        )
    }
}
