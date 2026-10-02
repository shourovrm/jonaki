package app.jonaki.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.os.ParcelFileDescriptor
import app.jonaki.core.agent.ImageLoader
import app.jonaki.core.model.ImagePart
import app.jonaki.core.toolapi.ImageSource
import app.jonaki.core.toolapi.ThreadPaths
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import kotlin.math.roundToInt

/**
 * Shrinks an image or renders a PDF page for the model (D-049): at most
 * 1,568 pixels on the long side, JPEG at quality 85, turned upright from
 * the photo's EXIF orientation, transparency on white. The result is kept
 * in [cacheFolder] under the hash of the file's bytes, so the same file
 * gives the same bytes on every later request and the prompt cache holds,
 * even after a phone update changes the JPEG encoder.
 */
class ModelImageLoader(threadFolder: File, private val cacheFolder: File) : ImageLoader {
    private val paths = ThreadPaths(threadFolder)

    override fun load(source: ImageSource): ImagePart? {
        val file = paths.resolve(source.path)?.takeIf(File::isFile) ?: return null
        val jpegBytes = try {
            cachedOrEncoded(file, source.pdfPage)
        } catch (unreadable: IOException) {
            null
        } catch (locked: SecurityException) {
            // PdfRenderer refuses PDFs with a password.
            null
        } catch (tooLargeOrBroken: RuntimeException) {
            null
        } ?: return null
        return ImagePart(JPEG_TYPE, Base64.getEncoder().encodeToString(jpegBytes))
    }

    private fun cachedOrEncoded(file: File, pdfPage: Int?): ByteArray? {
        val pageSuffix = if (pdfPage == null) "" else "-page$pdfPage"
        val cached = File(cacheFolder, contentHash(file) + pageSuffix + ".jpg")
        if (cached.isFile) {
            return cached.readBytes()
        }
        val bitmap = if (pdfPage == null) decodeUpright(file) else renderPdfPage(file, pdfPage)
        if (bitmap == null) {
            return null
        }
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        bitmap.recycle()
        val jpegBytes = output.toByteArray()
        cacheFolder.mkdirs()
        // Written aside, then renamed, so a half-written file is never read as the cached image.
        val partial = File(cacheFolder, cached.name + ".partial")
        partial.writeBytes(jpegBytes)
        partial.renameTo(cached)
        return jpegBytes
    }

    private fun decodeUpright(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }
        val original = PixelSize(bounds.outWidth, bounds.outHeight)
        val target = ImageScale.fitted(original)
        val options = BitmapFactory.Options().apply { inSampleSize = ImageScale.sampleSize(original, target) }
        val decoded = BitmapFactory.decodeFile(file.path, options) ?: return null
        val rotation = rotationDegrees(file)
        val turnsSideways = rotation == 90 || rotation == 270
        val outputSize = if (turnsSideways) PixelSize(target.height, target.width) else target

        val output = Bitmap.createBitmap(outputSize.width, outputSize.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        // JPEG has no transparency; a transparent PNG would otherwise turn black.
        canvas.drawColor(Color.WHITE)
        val placement = Matrix()
        placement.postScale(target.width.toFloat() / decoded.width, target.height.toFloat() / decoded.height)
        placement.postRotate(rotation.toFloat())
        when (rotation) {
            90 -> placement.postTranslate(outputSize.width.toFloat(), 0f)
            180 -> placement.postTranslate(outputSize.width.toFloat(), outputSize.height.toFloat())
            270 -> placement.postTranslate(0f, outputSize.height.toFloat())
        }
        canvas.drawBitmap(decoded, placement, Paint(Paint.FILTER_BITMAP_FLAG))
        decoded.recycle()
        return output
    }

    /** Phone cameras often save the picture sideways and note the turn in EXIF. */
    private fun rotationDegrees(file: File): Int {
        val orientation = try {
            ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (noExif: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }

    /** A scanned page, drawn at 1,568 pixels on its long side on white. */
    private fun renderPdfPage(file: File, pageNumber: Int): Bitmap? {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (failure: IOException) {
            descriptor.close()
            throw failure
        }
        try {
            if (pageNumber < 1 || pageNumber > renderer.pageCount) {
                return null
            }
            val page = renderer.openPage(pageNumber - 1)
            try {
                val factor = ImageScale.MAX_LONG_SIDE.toDouble() / maxOf(page.width, page.height)
                val width = (page.width * factor).roundToInt().coerceAtLeast(1)
                val height = (page.height * factor).roundToInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bitmap
            } finally {
                page.close()
            }
        } finally {
            renderer.close()
        }
    }

    private fun contentHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val JPEG_TYPE = "image/jpeg"
        const val JPEG_QUALITY = 85
    }
}
