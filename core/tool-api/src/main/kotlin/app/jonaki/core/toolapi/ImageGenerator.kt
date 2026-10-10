package app.jonaki.core.toolapi

/**
 * What the generate_image tool needs to make a picture. The tool module sees
 * only this interface, like web_search sees its backends; the OpenRouter
 * implementation lives in providers/openai-compatible and the app joins the
 * two. It sits in this module because the tool may depend on nothing else.
 * There is one generator per image service; the app gives the tool a
 * dispatcher that picks the generator by [ImageRequest.serviceKey].
 */
fun interface ImageGenerator {
    suspend fun generate(request: ImageRequest): ImageOutcome
}

data class ImageRequest(
    /** The image service that makes the picture, for example "openrouter" or "gemini"; the app routes by it. */
    val serviceKey: String,
    /** The service's id of the image model, for example "black-forest-labs/flux.2-klein-4b". */
    val modelId: String,
    val prompt: String,
    /** For example "16:9"; null leaves the choice to the model. */
    val aspectRatio: String? = null,
    /** The model's own `quality` value, for example "high"; null sends none. Only set for a model that declares it. */
    val quality: String? = null,
    /** The model's own `resolution` value, for example "2K"; null sends none. Only set for a model that declares it. */
    val resolution: String? = null,
    /** Pictures the model starts from, already read and checked; empty for a picture made from the prompt alone. */
    val references: List<ImageReference> = emptyList(),
)

/**
 * One reference picture, read from the thread folder. The generator encodes
 * [bytes] for its service; the bytes never go into text the chat model sees,
 * which is why [toString] leaves them out.
 */
class ImageReference(
    /** Relative to the thread folder, for messages. */
    val path: String,
    /** "image/png", "image/jpeg" or "image/webp". */
    val mediaType: String,
    val bytes: ByteArray,
) {
    override fun toString(): String = "ImageReference($path, $mediaType, ${bytes.size} bytes)"
}

sealed interface ImageOutcome {
    /** [bytes] is the picture file; [costUsd] is what the service charged, null when it did not say. */
    class Success(
        val bytes: ByteArray,
        val mediaType: String,
        val costUsd: Double?,
        /** The service's token counts, kept for the hidden cost row; 0 when it gave none. */
        val inputTokens: Int = 0,
        val outputTokens: Int = 0,
    ) : ImageOutcome

    /** [message] is the service's own text when there is one. */
    data class Failed(val kind: ImageFailure, val message: String) : ImageOutcome
}

/** The cases where the model or the user can do something different next. */
enum class ImageFailure {
    /** No key is saved for the service, or the service refused the saved one. */
    KEY_PROBLEM,

    /** The account has no credit left. */
    OUT_OF_CREDIT,

    /**
     * The service, or the company it passes the request to, is over a limit of
     * its own. The user's key and credit are fine, and nothing was charged.
     */
    SERVICE_LIMIT,

    /** The service refused the prompt or the picture for its content rules. */
    BLOCKED,

    TIMED_OUT,

    /** The answer held no picture. */
    NO_IMAGE,

    OTHER,
}

/**
 * The plain-text contract between generate_image and the chat (like
 * [ViewedImages]): the result starts with a fixed line naming the file, and
 * the chat shows that file as a picture under the step.
 */
object GeneratedImages {
    const val TOOL_NAME = "generate_image"

    /**
     * generate_vector_image uses the same first line, so [pathIn] reads both
     * results and the chat needs one parser; the tool name tells the chat
     * which card to show.
     */
    const val VECTOR_TOOL_NAME = "generate_vector_image"

    private const val RESULT_PREFIX = "Image saved: "

    fun firstLine(path: String): String = RESULT_PREFIX + path

    /** The saved file's path in a generate_image result; null for an error or any other text. */
    fun pathIn(resultText: String): String? {
        val firstLine = resultText.lineSequence().firstOrNull() ?: return null
        if (!firstLine.startsWith(RESULT_PREFIX)) {
            return null
        }
        return firstLine.removePrefix(RESULT_PREFIX).trim().ifEmpty { null }
    }
}
