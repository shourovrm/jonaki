package app.jonaki.run

import app.jonaki.core.toolapi.GeneratedImages
import app.jonaki.core.toolapi.GeneratedVideos
import app.jonaki.core.toolapi.ToolOutput
import app.jonaki.feature.chat.MediaKind
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * What the user chose in the settings sheet of a kind of media (D-174). A
 * null value leaves that argument out of the call, so the tool uses the
 * thread's own model (or the starred one) and its low-cost defaults.
 */
data class MediaCallOptions(
    /** "service:modelId". */
    val modelKey: String? = null,
    /** Picture only: true for High, false for Standard. */
    val isHighQuality: Boolean? = null,
    /** Picture and vector image, for example "16:9". */
    val aspectRatio: String? = null,
    /** Video only. */
    val durationSeconds: Int? = null,
    /** Video only, for example "720p". */
    val resolution: String? = null,
    /** Picture only: the attached pictures, as paths in the thread folder such as "inbox/photo.jpg". */
    val referenceImages: List<String> = emptyList(),
)

/**
 * The call that a send in media mode makes: the user's text, exactly as
 * typed (trimmed), is the prompt, and the only other arguments are the ones
 * the user set in the kind's settings sheet.
 */
object MediaModeCall {
    /**
     * A video may outlive one call of the tool (about 8 minutes). With no model
     * to collect it, the run asks again for the same job, up to this many calls
     * in all (about 24 minutes).
     */
    const val MAX_VIDEO_CALLS = 3

    /** Only the arguments that [kind]'s tool has are written; a setting of another kind is ignored. */
    fun arguments(typedText: String, kind: MediaKind? = null, options: MediaCallOptions = MediaCallOptions()): JsonObject =
        buildJsonObject {
            put("prompt", typedText.trim())
            if (kind == null) {
                return@buildJsonObject
            }
            options.modelKey?.let { modelKey -> put("model", modelKey) }
            if (kind == MediaKind.PICTURE) {
                options.isHighQuality?.let { isHigh -> put("quality", if (isHigh) "high" else "standard") }
                if (options.referenceImages.isNotEmpty()) {
                    putJsonArray("reference_images") { options.referenceImages.forEach { path -> add(path) } }
                }
            }
            if (kind == MediaKind.PICTURE || kind == MediaKind.VECTOR) {
                options.aspectRatio?.let { aspectRatio -> put("aspect_ratio", aspectRatio) }
            }
            if (kind == MediaKind.VIDEO) {
                options.durationSeconds?.let { seconds -> put("duration_seconds", seconds) }
                options.resolution?.let { resolution -> put("resolution", resolution) }
            }
        }

    fun toolName(kind: MediaKind): String = when (kind) {
        MediaKind.PICTURE -> GeneratedImages.TOOL_NAME
        MediaKind.VECTOR -> GeneratedImages.VECTOR_TOOL_NAME
        MediaKind.VIDEO -> GeneratedVideos.TOOL_NAME
    }

    /**
     * What the chat shows when the call failed: the tool's own error text
     * without the "Error: " that marks it for a chat model. Null for a call
     * that worked, and for a run that made no call.
     */
    fun failureText(lastOutput: ToolOutput?): String? {
        if (lastOutput == null || !lastOutput.isError) {
            return null
        }
        return lastOutput.text.trim().removePrefix(ERROR_PREFIX).trim().ifEmpty { null }
    }

    private const val ERROR_PREFIX = "Error:"

    fun maxCalls(kind: MediaKind): Int = if (kind == MediaKind.VIDEO) MAX_VIDEO_CALLS else 1

    /**
     * The arguments of the call that follows [output], or null when the run is
     * over. Only a video can need one: a hand-over result with a job id is
     * followed by a call that collects that job, which costs nothing more.
     */
    fun nextCall(kind: MediaKind, output: ToolOutput): JsonObject? {
        if (kind != MediaKind.VIDEO) {
            return null
        }
        val jobId = VideoHandOver.jobIdIn(output) ?: return null
        return buildJsonObject { put("job_id", jobId) }
    }
}
