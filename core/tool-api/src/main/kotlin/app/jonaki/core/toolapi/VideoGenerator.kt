package app.jonaki.core.toolapi

import java.io.File

/**
 * What the generate_video tool needs to make a video. The tool module sees
 * only this interface (like [ImageGenerator]); the OpenRouter implementation
 * lives in providers/openai-compatible and the app joins the two.
 *
 * A video takes minutes, so making one is three separate steps: [start] a
 * job, [check] it until it is done, [download] its file. The job goes on at
 * the service, and is charged, even when nobody checks it.
 */
interface VideoGenerator {
    suspend fun start(request: VideoRequest): VideoStartOutcome

    /** Asks the service how the job is doing. [jobId] is the id [start] gave. */
    suspend fun check(serviceKey: String, jobId: String): VideoCheckOutcome

    /**
     * Streams the finished video to [target] without holding it in memory.
     * [contentUrl] is one of [VideoCheckOutcome.Completed.contentUrls]. A
     * failed download leaves no partly written [target] behind.
     */
    suspend fun download(serviceKey: String, contentUrl: String, target: File): VideoDownloadOutcome
}

data class VideoRequest(
    /** The video service, for example "openrouter". */
    val serviceKey: String,
    /** The service's id of the video model, for example "google/veo-3.1-lite". */
    val modelId: String,
    val prompt: String,
    /** Whole seconds; null leaves the length to the model. */
    val durationSeconds: Int? = null,
    /** For example "720p"; null leaves it to the model. */
    val resolution: String? = null,
    /** For example "16:9"; null leaves it to the model. */
    val aspectRatio: String? = null,
    /** Null leaves the sound to the model's own default. */
    val withAudio: Boolean? = null,
)

sealed interface VideoStartOutcome {
    data class Started(val jobId: String) : VideoStartOutcome

    data class Failed(val kind: VideoFailure, val message: String) : VideoStartOutcome
}

sealed interface VideoCheckOutcome {
    /** [status] is the service's word, for example "pending" or "in_progress". */
    data class Working(val status: String) : VideoCheckOutcome

    /**
     * The video is ready. [contentUrls] are what the service listed, as it
     * wrote them (a full link or a path); [costUsd] is what the job cost, null
     * when the service did not say.
     */
    data class Completed(val contentUrls: List<String>, val costUsd: Double?) : VideoCheckOutcome

    /** The job ended without a video: it failed, was cancelled or expired. [reason] is the service's own text. */
    data class JobEnded(val status: String, val reason: String) : VideoCheckOutcome

    /** The question itself failed (no connection, a refused key); the job may still be running. */
    data class Failed(val kind: VideoFailure, val message: String) : VideoCheckOutcome
}

sealed interface VideoDownloadOutcome {
    data class Saved(val mediaType: String, val sizeBytes: Long) : VideoDownloadOutcome

    data class Failed(val kind: VideoFailure, val message: String) : VideoDownloadOutcome
}

/** The cases where the model or the user can do something different next. */
enum class VideoFailure {
    /** No key is saved for the service, or the service refused the saved one. */
    KEY_PROBLEM,

    /** The account has no credit left. */
    OUT_OF_CREDIT,

    /**
     * The service, or the company it passes the request to, is over a limit of
     * its own. The user's key and credit are fine, and nothing was charged.
     */
    SERVICE_LIMIT,

    /** The service refused the prompt for its content rules. */
    BLOCKED,

    TIMED_OUT,

    OTHER,
}

/**
 * The plain-text contract between generate_video and the chat (like
 * [GeneratedImages]): the result starts with a fixed line naming the file,
 * and the chat shows that file as a video card under the step.
 */
object GeneratedVideos {
    const val TOOL_NAME = "generate_video"

    private const val RESULT_PREFIX = "Video saved: "

    fun firstLine(path: String): String = RESULT_PREFIX + path

    /** The saved file's path in a generate_video result; null for an error, a hand-over or any other text. */
    fun pathIn(resultText: String): String? {
        val firstLine = resultText.lineSequence().firstOrNull() ?: return null
        if (!firstLine.startsWith(RESULT_PREFIX)) {
            return null
        }
        return firstLine.removePrefix(RESULT_PREFIX).trim().ifEmpty { null }
    }
}
