package app.jonaki.feature.chat

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap

/** What looking at a saved video gave. */
sealed interface ChatVideoResult {
    /**
     * The file is there. [frame] is a picture from the video, null when none
     * could be made (the card then shows a plain tile); [durationSeconds] is
     * null when the file did not say.
     */
    @Immutable
    data class Loaded(val frame: ImageBitmap?, val durationSeconds: Int?, val sizeBytes: Long) : ChatVideoResult

    /** The file is gone, for example in an old thread whose file was deleted. */
    data object Missing : ChatVideoResult
}

/**
 * The videos of one thread, supplied by the app so that this module does not
 * read media files itself. A [path] is the thread-relative path from the
 * generate_video result ("videos/boat.mp4").
 */
interface ChatVideos {
    /** The preview frame, length and size of the video, scaled so that the frame is at least [widthPx] wide. */
    suspend fun info(path: String, widthPx: Int): ChatVideoResult
}

/** Set by the app around the chat; null in previews that do not set it, where a card shows a plain tile. */
val LocalChatVideos = staticCompositionLocalOf<ChatVideos?> { null }
