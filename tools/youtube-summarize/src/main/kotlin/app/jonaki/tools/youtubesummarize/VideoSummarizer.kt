package app.jonaki.tools.youtubesummarize

/**
 * What the tool needs from a video-understanding model. The app wires this
 * to the Gemini provider (D-012), so this module never depends on a provider.
 */
fun interface VideoSummarizer {
    suspend fun summarize(request: VideoRequest): VideoAnswer
}

data class VideoRequest(
    val videoUrl: String,
    val prompt: String,
    val startSeconds: Int?,
    val endSeconds: Int?,
    val lowMediaResolution: Boolean,
)

sealed interface VideoAnswer {
    data class Success(val text: String, val inputTokens: Int?) : VideoAnswer

    data class Failed(val message: String, val retryable: Boolean) : VideoAnswer
}
