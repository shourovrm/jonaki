package app.jonaki.feature.chat

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap

/** What loading a picture gave. */
sealed interface ChatImageResult {
    @Immutable
    data class Loaded(val image: ImageBitmap) : ChatImageResult

    /** The file is gone or cannot be decoded, for example in an old thread whose file was deleted. */
    data object Missing : ChatImageResult
}

/**
 * The pictures of one thread, supplied by the app so that this module does
 * not decode files itself. A [path] is the thread-relative path from a
 * message ("inbox/photo.jpg") or the path of a file waiting to be sent.
 */
interface ChatImages {
    /** The tile's picture if it is already in memory, so scrolling back shows it at once. */
    fun cachedTile(path: String, sidePx: Int): ChatImageResult.Loaded?

    /** A square tile's picture, at least [sidePx] on its short side; the tile crops it to the middle. */
    suspend fun tile(path: String, sidePx: Int): ChatImageResult

    /** The picture sized for a screen of this size, with room to zoom; see [ImageViewFit]. */
    suspend fun full(path: String, screenWidthPx: Int, screenHeightPx: Int): ChatImageResult

    /**
     * The text of an SVG file that generate_vector_image saved (already
     * sanitised); null when the file is gone, over 2 MB or not readable.
     */
    suspend fun svgText(path: String): String?
}

/** Set by the app around the chat; null in previews, where messages then show their text only. */
val LocalChatImages = staticCompositionLocalOf<ChatImages?> { null }
