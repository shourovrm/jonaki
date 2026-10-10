package app.jonaki.tools.generateimage

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
 * Makes one picture from a text description with an image model the user
 * added in Settings, and saves it in the thread's images/ folder. Each call
 * costs money and writes a file, so every call asks the user first.
 *
 * [modelKeys] are the models the user added as "service:modelId", for example
 * "openrouter:black-forest-labs/flux.2-klein-4b"; [defaultModelKey] is the
 * model that is used when the call names no model. [defaultModelKey] is asked
 * at every call, so the thread's pick made while a run is going counts for the
 * next picture. [generator] routes a request to its service by
 * [ImageRequest.serviceKey].
 */
class GenerateImageTool(
    private val generator: ImageGenerator,
    private val modelKeys: List<String>,
    private val defaultModelKey: () -> String?,
) : Tool {
    constructor(generator: ImageGenerator, modelKeys: List<String>, defaultModelKey: String?) :
        this(generator, modelKeys, { defaultModelKey })

    override val name: String = GeneratedImages.TOOL_NAME

    override val promptLine: String =
        "generate_image: make a picture from a text description; costs money, the user approves each call"

    override val guidelines: List<String> = listOf(
        "Use generate_image only when the user asks for a new picture, logo, illustration or photo. " +
            "Each picture costs money, so make one picture per request unless the user asks for more, " +
            "and never to test or to try variants on your own.",
        "The prompt must describe the picture fully: subject, setting, style, colours, light and any text that must appear. " +
            "The image model sees nothing else of the conversation.",
        "The picture is saved in images/ and the user sees it in the chat; do not paste its path as a link. " +
            "If a picture was blocked or failed, tell the user before trying again.",
    )

    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("prompt") {
                put("type", "string")
                put("description", "A full description of the picture: subject, setting, style, colours, light, text in it")
            }
            putJsonObject("aspect_ratio") {
                put("type", "string")
                put("description", "Shape of the picture, for example 1:1, 16:9, 9:16, 4:3 or 3:2; the model's default when left out")
            }
            putJsonObject("file_name") {
                put("type", "string")
                put("description", "Name for the file without folder or ending, for example blue-door; made from the prompt when left out")
            }
            putJsonObject("model") {
                put("type", "string")
                put("description", "One of the image models the user added, as service:model; the user's starred model when left out")
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

    /** A slow image model can take a minute; the request itself gives up a little earlier. */
    override val timeLimit: Duration = 120.seconds

    override suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput {
        val prompt = arguments.stringArgument("prompt")?.trim().orEmpty()
        if (prompt.isEmpty()) {
            return ToolOutput.error("argument prompt is missing", "Call generate_image again with a full description of the picture.")
        }
        val modelKey = ImageModelChoice.choose(arguments.stringArgument("model"), modelKeys, defaultModelKey())
            ?: return modelError(arguments.stringArgument("model"))
        val aspectRatio = arguments.stringArgument("aspect_ratio")?.trim()?.ifEmpty { null }

        // The first colon ends the service key; a model id may hold more colons, such as "x/y:free".
        val serviceKey = modelKey.substringBefore(':')
        val modelId = modelKey.substringAfter(':')
        val outcome = generator.generate(ImageRequest(serviceKey, modelId, prompt, aspectRatio))
        return when (outcome) {
            is ImageOutcome.Failed -> ImageFailureTexts.toolOutput(outcome, serviceKey, modelKey)
            is ImageOutcome.Success -> save(outcome, modelKey, prompt, arguments.stringArgument("file_name"), context)
        }
    }

    private fun modelError(requested: String?): ToolOutput {
        if (modelKeys.isEmpty()) {
            return ToolOutput.error(
                "no image model is added",
                "Tell the user to add an image service with a key and an image model under Settings > Models > Image generation.",
            )
        }
        return ToolOutput.error(
            "model ${requested.orEmpty().trim()} is not one of the user's image models, or more than one service has it",
            "Use one of: ${modelKeys.joinToString(", ")}. Or leave model out to use the starred one.",
        )
    }

    private suspend fun save(
        image: ImageOutcome.Success,
        modelKey: String,
        prompt: String,
        requestedName: String?,
        context: ToolContext,
    ): ToolOutput {
        val extension = ImageFiles.extensionFor(image.mediaType, image.bytes)
            ?: return ToolOutput.error(
                "the service sent a file of type ${image.mediaType.ifBlank { "unknown" }}, not a png, jpeg or webp picture",
                "Nothing was saved. Try another model or tell the user; the picture was charged.",
            )
        val baseName = ImageFileNames.baseName(requestedName, prompt)
        val file = try {
            withContext(Dispatchers.IO) { write(context.threadFolder, baseName, extension, image.bytes) }
        } catch (problem: IOException) {
            return ToolOutput.error(
                "the picture could not be saved (${problem.message})",
                "Tell the user; the picture was charged and is lost.",
            )
        }
        return ToolOutput.success(describeResult(context.paths.relativePath(file), file, image, modelKey))
    }

    private fun write(threadFolder: File, baseName: String, extension: String, bytes: ByteArray): File {
        val folder = File(threadFolder, ImageFileNames.FOLDER)
        folder.mkdirs()
        val file = ImageFileNames.freeFile(folder, baseName, extension)
        file.writeBytes(bytes)
        return file
    }

    private fun describeResult(path: String, file: File, image: ImageOutcome.Success, modelKey: String): String {
        val fileSize = IncomingFiles.describeSize(file.length())
        val size = ImageDimensions.of(image.bytes)?.let { dimensions -> "${dimensions.describe()}, $fileSize" } ?: fileSize
        val cost = image.costUsd?.let { dollars -> String.format(Locale.ENGLISH, "$%.4f", dollars) } ?: "not reported by the service"
        return listOf(
            GeneratedImages.firstLine(path),
            "Size: $size",
            "Model: $modelKey",
            "Cost: $cost",
            "The user sees the picture in the chat.",
        ).joinToString("\n")
    }
}
