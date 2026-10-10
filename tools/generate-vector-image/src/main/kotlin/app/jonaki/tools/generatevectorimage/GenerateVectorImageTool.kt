package app.jonaki.tools.generatevectorimage

import app.jonaki.core.toolapi.Capability
import app.jonaki.core.toolapi.GeneratedImages
import app.jonaki.core.toolapi.ImageFailureTexts
import app.jonaki.core.toolapi.ImageFileNames
import app.jonaki.core.toolapi.ImageGenerator
import app.jonaki.core.toolapi.ImageModelChoice
import app.jonaki.core.toolapi.ImageOutcome
import app.jonaki.core.toolapi.ImageRequest
import app.jonaki.core.toolapi.IncomingFiles
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import app.jonaki.core.toolapi.stringArgument
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Makes one vector picture (an SVG file) from a text description with a
 * vector model the user added in Settings, and saves it in the thread's
 * images/ folder. Each call costs money and writes a file, so every call
 * asks the user first.
 *
 * [modelKeys] are only the user's vector models, as "service:modelId"; the
 * app keeps them apart from the raster models that generate_image gets.
 * [defaultModelKey] is asked at every call, like in generate_image. The
 * service sends the SVG as an image answer, so the tool uses the same
 * [ImageGenerator] and the same failure kinds as generate_image.
 *
 * An SVG can carry scripts and outside references, so nothing is saved
 * before [SvgSanitizer] has rewritten it.
 */
class GenerateVectorImageTool(
    private val generator: ImageGenerator,
    private val modelKeys: List<String>,
    private val defaultModelKey: () -> String?,
) : Tool {
    constructor(generator: ImageGenerator, modelKeys: List<String>, defaultModelKey: String?) :
        this(generator, modelKeys, { defaultModelKey })

    override val name: String = GeneratedImages.VECTOR_TOOL_NAME

    override val promptLine: String =
        "generate_vector_image: make a logo, icon or simple illustration as an SVG file; costs money, the user approves each call"

    override val guidelines: List<String> = listOf(
        "Use generate_vector_image when the user asks for a logo, an icon, a simple illustration or anything that must scale " +
            "without blur or be edited as a vector. Use generate_image for photos and detailed pictures. " +
            "Each call costs money, so make one per request unless the user asks for more, and never to test or to try variants on your own.",
        "The prompt must describe the subject, the style, the colours and any text in it fully. " +
            "The vector model sees nothing else of the conversation.",
        "The file is an SVG saved in images/ and the user sees it in the chat; do not paste its path as a link. " +
            "If the call failed, tell the user before trying again.",
    )

    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("prompt") {
                put("type", "string")
                put("description", "A full description of the vector picture: subject, style, colours, text in it")
            }
            putJsonObject("aspect_ratio") {
                put("type", "string")
                put("description", "Shape of the picture, for example 1:1, 4:3, 3:4, 16:9 or 9:16; the model's default when left out")
            }
            putJsonObject("file_name") {
                put("type", "string")
                put("description", "Name for the file without folder or ending, for example firefly-logo; made from the prompt when left out")
            }
            putJsonObject("model") {
                put("type", "string")
                put("description", "One of the vector models the user added, as service:model; the first one when left out")
                if (modelKeys.isNotEmpty()) {
                    putJsonArray("enum") {
                        for (modelKey in modelKeys) {
                            add(modelKey)
                        }
                    }
                }
            }
        }
        putJsonArray("required") { add("prompt") }
    }

    /** Spends money and writes a file; always asks, in the Auto mode too. */
    override val sideEffect: SideEffect = SideEffect.CHANGES

    override val requiredCapabilities: Set<Capability> = emptySet()

    /** A slow model can take a minute; the request itself gives up a little earlier. */
    override val timeLimit: Duration = 120.seconds

    override suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput {
        val prompt = arguments.stringArgument("prompt")?.trim().orEmpty()
        if (prompt.isEmpty()) {
            return ToolOutput.error("argument prompt is missing", "Call generate_vector_image again with a full description of the picture.")
        }
        val requestedModel = arguments.stringArgument("model")
        val modelKey = ImageModelChoice.choose(requestedModel, modelKeys, defaultModelKey())
            ?: return modelError(requestedModel)
        val aspectRatio = arguments.stringArgument("aspect_ratio")?.trim()?.ifEmpty { null }

        // The first colon ends the service key; a model id may hold more colons, such as "x/y:free".
        val serviceKey = modelKey.substringBefore(':')
        val modelId = modelKey.substringAfter(':')
        return when (val outcome = generator.generate(ImageRequest(serviceKey, modelId, prompt, aspectRatio))) {
            is ImageOutcome.Failed -> ImageFailureTexts.toolOutput(outcome, serviceKey, modelKey)
            is ImageOutcome.Success -> save(outcome, modelKey, prompt, arguments.stringArgument("file_name"), context)
        }
    }

    private fun modelError(requested: String?): ToolOutput {
        if (modelKeys.isEmpty()) {
            return ToolOutput.error(
                "no vector image model is added",
                "Tell the user to add a vector model (marked SVG) under Settings > Models > Image generation.",
            )
        }
        return ToolOutput.error(
            "model ${requested.orEmpty().trim()} is not one of the user's vector models, or more than one service has it",
            "Use one of: ${modelKeys.joinToString(", ")}. Or leave model out to use the first one. " +
                "For a photo or a detailed picture use generate_image.",
        )
    }

    private suspend fun save(
        image: ImageOutcome.Success,
        modelKey: String,
        prompt: String,
        requestedName: String?,
        context: ToolContext,
    ): ToolOutput {
        val cleaned = when (val checked = checkAndClean(image)) {
            is Checked.Refused -> return checked.output
            is Checked.Ready -> checked.clean
        }
        val bytes = cleaned.svg.toByteArray(Charsets.UTF_8)
        val baseName = ImageFileNames.baseName(requestedName, prompt)
        val file = try {
            withContext(Dispatchers.IO) { write(context.threadFolder, baseName, bytes) }
        } catch (problem: IOException) {
            return ToolOutput.error(
                "the picture could not be saved (${problem.message})",
                "Tell the user; the picture was charged and is lost.",
            )
        }
        return ToolOutput.success(describeResult(context.paths.relativePath(file), file, cleaned, image, modelKey))
    }

    private sealed interface Checked {
        class Ready(val clean: SvgSanitizing.Clean) : Checked

        class Refused(val output: ToolOutput) : Checked
    }

    /** Every refusal says that the picture was charged, since the service has already billed it. */
    private fun checkAndClean(image: ImageOutcome.Success): Checked {
        if (image.bytes.size > SvgCheck.MAX_BYTES) {
            val limit = IncomingFiles.describeSize(SvgCheck.MAX_BYTES.toLong())
            return Checked.Refused(
                ToolOutput.error(
                    "the SVG is ${IncomingFiles.describeSize(image.bytes.size.toLong())}, over the limit of $limit",
                    "Nothing was saved; the picture was charged. Tell the user, and ask for a simpler picture if they want another try.",
                ),
            )
        }
        val text = SvgCheck.decode(image.bytes)
        if (text == null || !SvgCheck.looksLikeSvg(text)) {
            val type = image.mediaType.ifBlank { "unknown" }
            return Checked.Refused(
                ToolOutput.error(
                    "the service sent a file of type $type that is not an SVG document",
                    "Nothing was saved; the picture was charged. Tell the user. For a raster picture use generate_image.",
                ),
            )
        }
        return when (val sanitized = SvgSanitizer.sanitize(text)) {
            is SvgSanitizing.Clean -> Checked.Ready(sanitized)
            is SvgSanitizing.Rejected -> Checked.Refused(
                ToolOutput.error(
                    "the SVG was not saved because it cannot be made safe: ${sanitized.reason}",
                    "The picture was charged. Tell the user; trying again may give a usable file.",
                ),
            )
        }
    }

    private fun write(threadFolder: File, baseName: String, bytes: ByteArray): File {
        val folder = File(threadFolder, ImageFileNames.FOLDER)
        folder.mkdirs()
        val file = ImageFileNames.freeFile(folder, baseName, EXTENSION)
        file.writeBytes(bytes)
        return file
    }

    private fun describeResult(
        path: String,
        file: File,
        cleaned: SvgSanitizing.Clean,
        image: ImageOutcome.Success,
        modelKey: String,
    ): String {
        val fileSize = IncomingFiles.describeSize(file.length())
        val cost = image.costUsd?.let { dollars -> String.format(Locale.ENGLISH, "$%.4f", dollars) } ?: "not reported by the service"
        val lines = mutableListOf(
            GeneratedImages.firstLine(path),
            "Size: ${describeDrawingSize(cleaned)}, $fileSize",
            "Model: $modelKey",
            "Cost: $cost",
        )
        if (cleaned.removed.isNotEmpty()) {
            lines += "Safety: ${cleaned.removed.size} unsafe or outside part(s) were removed from the SVG."
        }
        lines += "The user sees the picture in the chat."
        return lines.joinToString("\n")
    }

    private fun describeDrawingSize(cleaned: SvgSanitizing.Clean): String {
        val width = cleaned.width
        val height = cleaned.height
        if (width == null || height == null) {
            return "drawing size not stated"
        }
        return "${plainNumber(width)}x${plainNumber(height)} units"
    }

    private fun plainNumber(value: Double): String =
        if (value == Math.rint(value)) value.toLong().toString() else String.format(Locale.ENGLISH, "%.1f", value)

    private companion object {
        const val EXTENSION = "svg"
    }
}
