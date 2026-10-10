package app.jonaki.core.toolapi

import java.io.File
import java.io.IOException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The result of [ImageReferences.load]: pictures ready to send, or a refusal with the text for the chat model. */
sealed interface ReferenceResult {
    class Loaded(val references: List<ImageReference>) : ReferenceResult

    class Refused(val output: ToolOutput) : ReferenceResult
}

/**
 * Reads and checks the pictures a call gives an image model with its prompt.
 * Every check runs before any request is made, so a wrong path never costs a
 * picture. The file work blocks, so callers run [load] off the main thread.
 *
 * A reference is a file of the thread folder only: absolute paths, ".." and
 * a link that leads out are refused (ThreadPaths). It must be a png, jpeg or
 * webp by its first bytes.
 */
object ImageReferences {
    const val ARGUMENT_NAME = "reference_images"

    const val MAX_FILE_BYTES: Long = 10L * 1024 * 1024

    const val MAX_TOTAL_BYTES: Long = 20L * 1024 * 1024

    private const val HEADER_BYTES = 512

    /** The paths in the call's `reference_images`; a single text counts as a list of one. Blank entries are dropped. */
    fun pathsIn(arguments: JsonObject): List<String> {
        val value = arguments[ARGUMENT_NAME]
        val texts = when (value) {
            is JsonArray -> value.mapNotNull { element -> (element as? JsonPrimitive)?.takeIf { it.isString }?.content }
            is JsonPrimitive -> listOfNotNull(value.takeIf { it.isString }?.content)
            else -> emptyList()
        }
        return texts.map { text -> text.trim() }.filter { text -> text.isNotEmpty() }
    }

    /**
     * [facts] is what the model declares, null when the model list is not
     * available; [otherModelsWithReferences] are the other added models that
     * take pictures, named when this one does not. Gemini has no such list,
     * so its models are not checked for a count.
     */
    fun load(
        paths: List<String>,
        threadPaths: ThreadPaths,
        modelKey: String,
        facts: ImageModelFacts?,
        otherModelsWithReferences: List<String>,
    ): ReferenceResult {
        countProblem(paths.size, modelKey, facts, otherModelsWithReferences)?.let { return ReferenceResult.Refused(it) }
        if (paths.isEmpty()) {
            return ReferenceResult.Loaded(emptyList())
        }
        val files = mutableListOf<Pair<String, File>>()
        var totalBytes = 0L
        for (path in paths) {
            val file = when (val checked = checkedFile(path, threadPaths)) {
                is Checked.Found -> checked.file
                is Checked.Problem -> return ReferenceResult.Refused(checked.output)
            }
            totalBytes += file.length()
            files += path to file
        }
        if (totalBytes > MAX_TOTAL_BYTES) {
            return ReferenceResult.Refused(
                ToolOutput.error(
                    "the reference pictures are ${IncomingFiles.describeSize(totalBytes)} together, and the limit is ${IncomingFiles.describeSize(MAX_TOTAL_BYTES)}",
                    "Pass fewer or smaller pictures.",
                ),
            )
        }
        val references = mutableListOf<ImageReference>()
        for ((path, file) in files) {
            val bytes = try {
                file.readBytes()
            } catch (problem: IOException) {
                return ReferenceResult.Refused(
                    ToolOutput.error("reference picture $path could not be read (${problem.message})", "Check the path, or leave reference_images out."),
                )
            }
            val mediaType = ImageFormats.mediaTypeOfHeader(bytes)
                ?: return ReferenceResult.Refused(notAPicture(path, bytes.copyOf(minOf(bytes.size, HEADER_BYTES))))
            references += ImageReference(path, mediaType, bytes)
        }
        return ReferenceResult.Loaded(references)
    }

    private sealed interface Checked {
        class Found(val file: File) : Checked

        class Problem(val output: ToolOutput) : Checked
    }

    private fun checkedFile(path: String, threadPaths: ThreadPaths): Checked {
        val outside = ToolOutput.error(
            "reference picture $path is not inside this thread's folder",
            "Give a path relative to the thread folder, such as images/name.png or inbox/photo.jpg. No absolute paths and no '..'.",
        )
        if (File(path).isAbsolute) {
            return Checked.Problem(outside)
        }
        val file = threadPaths.resolve(path) ?: return Checked.Problem(outside)
        if (!file.isFile) {
            return Checked.Problem(
                ToolOutput.error(
                    "reference picture $path does not exist",
                    "Use find_files to list images/ and inbox/, then pass a path from that list.",
                ),
            )
        }
        if (file.length() > MAX_FILE_BYTES) {
            return Checked.Problem(
                ToolOutput.error(
                    "reference picture $path is ${IncomingFiles.describeSize(file.length())}, and one picture can be at most ${IncomingFiles.describeSize(MAX_FILE_BYTES)}",
                    "Pass a smaller picture, or leave it out.",
                ),
            )
        }
        return Checked.Found(file)
    }

    private fun notAPicture(path: String, start: ByteArray): ToolOutput {
        if (ImageFormats.looksLikeSvg(start)) {
            return ToolOutput.error(
                "reference picture $path is an SVG file, and image models take only png, jpeg or webp pictures",
                "A vector file cannot be a reference, because the app cannot turn it into a picture. Pass a png, jpeg or webp picture, or leave it out.",
            )
        }
        return ToolOutput.error(
            "reference picture $path is not a png, jpeg or webp picture",
            "Pass a png, jpeg or webp file. The file ending does not matter, the content does.",
        )
    }

    private fun countProblem(
        count: Int,
        modelKey: String,
        facts: ImageModelFacts?,
        otherModelsWithReferences: List<String>,
    ): ToolOutput? {
        if (facts == null) {
            if (count == 0 || isGemini(modelKey)) {
                return null
            }
            return ToolOutput.error(
                "the list of what $modelKey accepts could not be loaded, so reference pictures cannot be checked",
                "Try again later, or make the picture without reference_images.",
            )
        }
        val maximum = facts.maxReferences
        if (count == 0) {
            return null
        }
        if (maximum == null || maximum == 0) {
            return ToolOutput.error("$modelKey takes no reference pictures", noReferenceAdvice(otherModelsWithReferences))
        }
        if (count > maximum) {
            return ToolOutput.error(
                "$count reference pictures were given, but $modelKey takes at most $maximum",
                "Pass $maximum or fewer.",
            )
        }
        return null
    }

    private fun noReferenceAdvice(others: List<String>): String {
        if (others.isEmpty()) {
            return "Leave reference_images out. None of the user's added image models takes reference pictures; they can add one under Settings > Models > Image generation."
        }
        return "Leave reference_images out, or set model to one that takes them: ${others.joinToString(", ")}."
    }

    /** The text for a call that gives fewer pictures than the model needs, for example some vector models; null when fine. */
    fun minimumProblem(count: Int, modelKey: String, facts: ImageModelFacts?): ToolOutput? {
        val minimum = facts?.minReferences ?: return null
        if (count >= minimum) {
            return null
        }
        return ToolOutput.error(
            "$modelKey needs at least $minimum reference picture${if (minimum == 1) "" else "s"}, and the call gave $count",
            "Pass them in reference_images, or use another model.",
        )
    }

    private fun isGemini(modelKey: String): Boolean = modelKey.substringBefore(':') == "gemini"
}
