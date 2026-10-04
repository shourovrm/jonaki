package app.jonaki.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import java.io.File
import java.io.IOException

/**
 * Decodes an image file turned upright from its EXIF orientation, at a size
 * the caller picks. The model's pictures (D-049) and the chat's thumbnails
 * both come from here, so a photo that looks upright to the model looks
 * upright to the user.
 */
object UprightImageDecoder {
    /**
     * [targetFor] gets the picture's upright size and returns the size to
     * decode it at. The file is first read at the largest power-of-two
     * reduction that still covers that size, so a 48-megapixel photo is not
     * held whole in memory. [backgroundColor] is painted first, because a
     * transparent PNG turns black in a JPEG; null keeps the transparency.
     * Returns null when the file is not an image the phone can decode.
     */
    fun decode(file: File, backgroundColor: Int?, targetFor: (PixelSize) -> PixelSize): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }
        val rotation = rotationDegrees(file)
        val turnsSideways = rotation == 90 || rotation == 270
        val original = PixelSize(bounds.outWidth, bounds.outHeight)
        val uprightOriginal = if (turnsSideways) PixelSize(original.height, original.width) else original
        val outputSize = targetFor(uprightOriginal)
        // The file's own, still sideways, size scaled to the same factor.
        val target = if (turnsSideways) PixelSize(outputSize.height, outputSize.width) else outputSize

        val options = BitmapFactory.Options().apply { inSampleSize = ImageScale.sampleSize(original, target) }
        val decoded = BitmapFactory.decodeFile(file.path, options) ?: return null

        val output = Bitmap.createBitmap(outputSize.width, outputSize.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        if (backgroundColor != null) {
            canvas.drawColor(backgroundColor)
        }
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
}
