package app.jonaki.files

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Where the system camera app writes a photo for the composer's camera
 * button (D-053). Jonaki hands the camera app a FileProvider link to an
 * empty file in its cache, so it needs no camera permission of its own.
 */
object CameraPhotos {
    private const val FOLDER = "camera"
    private val NAME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    /** A fresh file for the next photo. */
    fun newFile(context: Context): File {
        val folder = File(context.cacheDir, FOLDER)
        folder.mkdirs()
        return File(folder, "photo-${NAME_FORMAT.format(LocalDateTime.now())}.jpg")
    }

    /** The link the camera app writes to; the authority is the one share_file uses (D-044). */
    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    // No clean-up at app start: Android may stop Jonaki while the camera app
    // is open, and the photo must still be there when the result comes back.
}
