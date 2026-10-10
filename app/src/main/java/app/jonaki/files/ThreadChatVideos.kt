package app.jonaki.files

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.asImageBitmap
import app.jonaki.core.toolapi.ThreadPaths
import app.jonaki.feature.chat.ChatVideoResult
import app.jonaki.feature.chat.ChatVideos
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/**
 * The preview frame, length and size of the videos of one thread, for the
 * chat's video cards. Android's own [MediaMetadataRetriever] reads the file
 * off the main thread, two at a time (it is slow and holds native memory). A
 * file it cannot read still gives a card, with a plain tile.
 */
class ThreadChatVideos(threadFolder: File) : ChatVideos {
    private val paths = ThreadPaths(threadFolder)

    override suspend fun info(path: String, widthPx: Int): ChatVideoResult {
        val file = paths.resolve(path)?.takeIf(File::isFile) ?: return ChatVideoResult.Missing
        return withContext(reading) {
            val cacheKey = "${file.path}|${file.lastModified()}|$widthPx"
            frameCache.get(cacheKey)?.let { cached -> return@withContext cached }
            val read = read(file, widthPx)
            frameCache.put(cacheKey, read)
            read
        }
    }

    private fun read(file: File, widthPx: Int): ChatVideoResult.Loaded {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.path)
            val durationMillis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val seconds = durationMillis?.let { millis -> ((millis + 500) / 1000).toInt() }
            return ChatVideoResult.Loaded(frameOf(retriever, widthPx)?.asImageBitmap(), seconds, file.length())
        } catch (unreadable: RuntimeException) {
            // A damaged or unsupported file: the card shows a plain tile, and Play lets the phone's player say more.
            return ChatVideoResult.Loaded(frame = null, durationSeconds = null, sizeBytes = file.length())
        } catch (outOfMemory: OutOfMemoryError) {
            return ChatVideoResult.Loaded(frame = null, durationSeconds = null, sizeBytes = file.length())
        } finally {
            retriever.release()
        }
    }

    /** The first frame, already small: a full 1080p frame would take 8 MB for a 240 dp tile. */
    private fun frameOf(retriever: MediaMetadataRetriever, widthPx: Int): Bitmap? {
        val heightPx = widthPx * 9 / 16
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            return retriever.getScaledFrameAtTime(FIRST_FRAME_MICROSECONDS, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, widthPx, heightPx)
        }
        val full = retriever.getFrameAtTime(FIRST_FRAME_MICROSECONDS, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
        val scaled = Bitmap.createScaledBitmap(full, widthPx, full.height * widthPx / full.width.coerceAtLeast(1), true)
        if (scaled !== full) {
            full.recycle()
        }
        return scaled
    }

    private companion object {
        const val FIRST_FRAME_MICROSECONDS = 0L

        @OptIn(ExperimentalCoroutinesApi::class)
        val reading = Dispatchers.IO.limitedParallelism(2)

        /** About twenty cards: a scroll back through the thread shows them without reading the files again. */
        val frameCache = object : LruCache<String, ChatVideoResult.Loaded>(20) {}
    }
}
