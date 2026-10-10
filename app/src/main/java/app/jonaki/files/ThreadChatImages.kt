package app.jonaki.files

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.asImageBitmap
import app.jonaki.core.toolapi.ThreadPaths
import app.jonaki.feature.chat.ChatImageResult
import app.jonaki.feature.chat.ChatImages
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/**
 * The chat's pictures for one thread: thumbnails of the images in its
 * messages and of the files waiting to be sent, and the full-screen view.
 * Decoding runs off the main thread through [UprightImageDecoder]. Tiles
 * are kept in a small memory cache; the full-screen picture is not, because
 * one is shown at a time and it is large.
 */
class ThreadChatImages(threadFolder: File) : ChatImages {
    private val paths = ThreadPaths(threadFolder)

    override fun cachedTile(path: String, sidePx: Int): ChatImageResult.Loaded? {
        val file = fileFor(path) ?: return null
        val bitmap = tileCache.get(TileCacheKey.of(file, sidePx)) ?: return null
        return ChatImageResult.Loaded(bitmap.asImageBitmap())
    }

    override suspend fun tile(path: String, sidePx: Int): ChatImageResult {
        val file = fileFor(path) ?: return ChatImageResult.Missing
        return withContext(decoding) {
            val key = TileCacheKey.of(file, sidePx)
            val bitmap = tileCache.get(key) ?: decodeTile(file, sidePx)?.also { decoded -> tileCache.put(key, decoded) }
            if (bitmap == null) ChatImageResult.Missing else ChatImageResult.Loaded(bitmap.asImageBitmap())
        }
    }

    override suspend fun full(path: String, screenWidthPx: Int, screenHeightPx: Int): ChatImageResult {
        val file = fileFor(path) ?: return ChatImageResult.Missing
        val screen = PixelSize(screenWidthPx, screenHeightPx)
        return withContext(decoding) {
            val bitmap = decodeOrNull { UprightImageDecoder.decode(file, null) { upright -> ImageScale.forFullView(upright, screen) } }
            if (bitmap == null) ChatImageResult.Missing else ChatImageResult.Loaded(bitmap.asImageBitmap())
        }
    }

    override suspend fun svgText(path: String): String? {
        val file = fileFor(path) ?: return null
        if (file.length() > MAX_SVG_BYTES) {
            return null
        }
        return withContext(decoding) {
            try {
                file.readText()
            } catch (unreadable: java.io.IOException) {
                null
            }
        }
    }

    private fun decodeTile(file: File, sidePx: Int): Bitmap? =
        decodeOrNull { UprightImageDecoder.decode(file, null) { upright -> ImageScale.cover(upright, sidePx) } }

    /** A corrupt file or one too large to hold is shown as missing instead of ending the app. */
    private fun decodeOrNull(decode: () -> Bitmap?): Bitmap? = try {
        decode()
    } catch (outOfMemory: OutOfMemoryError) {
        null
    } catch (unreadable: RuntimeException) {
        null
    }

    /**
     * A message's path is relative to the thread folder, and ThreadPaths keeps
     * it inside; a file waiting to be sent is given as an absolute path by the app.
     */
    private fun fileFor(path: String): File? {
        val given = File(path)
        val file = if (given.isAbsolute) given else paths.resolve(path)
        return file?.takeIf(File::isFile)
    }

    private companion object {
        const val PARALLEL_DECODES = 3

        /** The limit generate_vector_image enforces before it saves. */
        const val MAX_SVG_BYTES = 2L * 1024 * 1024

        // A screen of thumbnails starts a dozen decodes at once; three at a time keeps the phone responsive.
        @OptIn(ExperimentalCoroutinesApi::class)
        val decoding = Dispatchers.IO.limitedParallelism(PARALLEL_DECODES)

        /** An eighth of the app's memory, at most 24 MB: about 70 tiles of 288 by 288 pixels. */
        val tileCache = object : LruCache<TileCacheKey, Bitmap>(cacheBytes()) {
            override fun sizeOf(key: TileCacheKey, value: Bitmap): Int = value.byteCount
        }

        fun cacheBytes(): Int {
            val eighth = Runtime.getRuntime().maxMemory() / 8
            return minOf(eighth, 24L * 1024 * 1024).toInt()
        }
    }
}
