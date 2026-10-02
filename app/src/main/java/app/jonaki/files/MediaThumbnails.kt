package app.jonaki.files

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.jonaki.feature.gallery.GalleryImage
import app.jonaki.feature.gallery.ThumbnailSource
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/**
 * Grid thumbnails for the gallery sheet (D-085), kept in a small memory
 * cache so scrolling back shows them at once. Android 10 and later hand
 * out MediaStore's own thumbnails; Android 8 and 9 decode the photo at
 * the smallest power-of-two size that still covers the cell.
 */
class MediaThumbnails(private val contentResolver: ContentResolver) : ThumbnailSource {
    private val cache = object : LruCache<String, Bitmap>(cacheBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    // A fling starts dozens of loads at once; four at a time keeps MediaStore and the disk responsive.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val loading = Dispatchers.IO.limitedParallelism(PARALLEL_LOADS)

    override suspend fun thumbnail(image: GalleryImage, sizePx: Int): ImageBitmap? {
        val key = "${image.id}@$sizePx"
        val cached = cache.get(key)
        if (cached != null) {
            return cached.asImageBitmap()
        }
        val loaded = withContext(loading) { loadOrNull(image, sizePx) } ?: return null
        cache.put(key, loaded)
        return loaded.asImageBitmap()
    }

    private fun loadOrNull(image: GalleryImage, sizePx: Int): Bitmap? {
        val uri = Uri.parse(image.uri)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
            } else {
                decodeSmall(uri, sizePx, image.orientationDegrees)
            }
        } catch (unreadable: IOException) {
            null
        } catch (noAccess: SecurityException) {
            null
        }
    }

    private fun decodeSmall(uri: Uri, sizePx: Int, orientationDegrees: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { input -> BitmapFactory.decodeStream(input, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }
        val original = PixelSize(bounds.outWidth, bounds.outHeight)
        // The cell crops to a square, so both sides must stay at least the cell's size.
        val options = BitmapFactory.Options().apply { inSampleSize = ImageScale.sampleSize(original, PixelSize(sizePx, sizePx)) }
        val decoded = contentResolver.openInputStream(uri)?.use { input -> BitmapFactory.decodeStream(input, null, options) }
            ?: return null
        if (orientationDegrees == 0) {
            return decoded
        }
        val turn = Matrix().apply { postRotate(orientationDegrees.toFloat()) }
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, turn, true)
        decoded.recycle()
        return upright
    }

    private companion object {
        const val PARALLEL_LOADS = 4

        /** An eighth of the app's memory, at most 32 MB: about 55 thumbnails of 384 by 384 pixels. */
        fun cacheBytes(): Int {
            val eighth = Runtime.getRuntime().maxMemory() / 8
            return minOf(eighth, 32L * 1024 * 1024).toInt()
        }
    }
}
