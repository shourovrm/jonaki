package app.jonaki.ui

import app.jonaki.core.toolapi.ImageQuality
import app.jonaki.core.toolapi.ImageReferences
import app.jonaki.feature.chat.StepPromptUi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The words of a picture step's line, from string resources so they follow the app's language. */
class ImageStepWords(
    /** "high", shown between the model and the prompt when the call asks for high quality. */
    val high: String,
    /** "2 reference pictures". */
    val referenceCount: (count: Int) -> String,
    /** The Settings default; a call that names no quality uses it. */
    val defaultIsHigh: Boolean = false,
)

/**
 * The text of a picture or video step in its three places: the short line of
 * the step track, the approval card, which shows the whole prompt before the
 * user allows the call, and the sheet that opens from the line. The line cuts
 * the prompt at [LINE_PROMPT_CHARACTERS] because the sheet shows all of it.
 */
object PromptStepText {
    const val LINE_PROMPT_CHARACTERS = 120

    private const val CUT_MARK = "…"

    /** The prompt on one line: line breaks become spaces, and a prompt over the limit ends in "…". */
    fun cut(prompt: String): String {
        val oneLine = prompt.trim().replace(Regex("\\s+"), " ")
        if (oneLine.length <= LINE_PROMPT_CHARACTERS) {
            return oneLine
        }
        return oneLine.take(LINE_PROMPT_CHARACTERS).trimEnd() + CUT_MARK
    }

    /** "model · high · 2 reference pictures · prompt"; [model] is the one the call names, else the default. */
    fun imageLine(arguments: JsonObject, defaultModel: String?, words: ImageStepWords?, withImageOptions: Boolean, promptOf: (String) -> String): String? {
        val parts = mutableListOf<String>()
        modelOf(arguments, defaultModel)?.let { model -> parts += model }
        if (withImageOptions && words != null) {
            if (isHigh(arguments, words)) {
                parts += words.high
            }
            val count = referencePaths(arguments).size
            if (count > 0) {
                parts += words.referenceCount(count)
            }
        }
        text(arguments, "prompt")?.let { prompt -> parts += promptOf(prompt) }
        return parts.joinToString(" · ").ifEmpty { null }
    }

    /** What the sheet shows for a picture call. Null when the call has no prompt. */
    fun imageDetail(arguments: JsonObject, defaultModel: String?, words: ImageStepWords?, withImageOptions: Boolean): StepPromptUi? {
        val prompt = text(arguments, "prompt") ?: return null
        return StepPromptUi(
            prompt = prompt.trim(),
            model = modelOf(arguments, defaultModel),
            isHighQuality = withImageOptions && words != null && isHigh(arguments, words),
            aspectRatio = text(arguments, "aspect_ratio"),
            referencePaths = if (withImageOptions) referencePaths(arguments) else emptyList(),
        )
    }

    fun referencePaths(arguments: JsonObject): List<String> = ImageReferences.pathsIn(arguments)

    private fun modelOf(arguments: JsonObject, defaultModel: String?): String? =
        text(arguments, "model")?.trim() ?: defaultModel

    private fun isHigh(arguments: JsonObject, words: ImageStepWords): Boolean {
        val asked = ImageQuality.fromArgument(text(arguments, "quality"))
        return (asked ?: if (words.defaultIsHigh) ImageQuality.HIGH else ImageQuality.STANDARD) == ImageQuality.HIGH
    }

    private fun text(arguments: JsonObject, key: String): String? =
        (arguments[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
}
