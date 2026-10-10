package app.jonaki.run

import app.jonaki.core.toolapi.GeneratedImages
import app.jonaki.core.toolapi.GeneratedVideos
import app.jonaki.core.toolapi.ToolOutput
import app.jonaki.feature.chat.MediaKind
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The call that a send in media mode makes: the user's text, exactly as
 * typed (trimmed), is the prompt, and nothing else is set, so the tool uses
 * the thread's own model (or the starred one) and its low-cost defaults.
 */
object MediaModeCall {
    /**
     * A video may outlive one call of the tool (about 8 minutes). With no model
     * to collect it, the run asks again for the same job, up to this many calls
     * in all (about 24 minutes).
     */
    const val MAX_VIDEO_CALLS = 3

    fun arguments(typedText: String): JsonObject = buildJsonObject { put("prompt", typedText.trim()) }

    fun toolName(kind: MediaKind): String = when (kind) {
        MediaKind.PICTURE -> GeneratedImages.TOOL_NAME
        MediaKind.VECTOR -> GeneratedImages.VECTOR_TOOL_NAME
        MediaKind.VIDEO -> GeneratedVideos.TOOL_NAME
    }

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
