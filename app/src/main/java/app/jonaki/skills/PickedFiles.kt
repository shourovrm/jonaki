package app.jonaki.skills

import android.content.ContentResolver
import android.net.Uri
import app.jonaki.core.skills.SkillDownloader
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Reads a file from Android's document picker, which needs no storage permission. */
object PickedFiles {
    sealed interface ReadResult {
        class Bytes(val bytes: ByteArray) : ReadResult

        data class Failed(val reason: String) : ReadResult
    }

    /** Stops after the import limit, so a huge file is never read whole. Call off the main thread. */
    fun read(contentResolver: ContentResolver, uri: Uri): ReadResult {
        val limit = SkillDownloader.MAX_TOTAL_BYTES
        return try {
            val stream = contentResolver.openInputStream(uri) ?: return ReadResult.Failed("the file could not be opened")
            stream.use {
                val content = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (content.size() <= limit) {
                    val count = stream.read(buffer)
                    if (count < 0) {
                        break
                    }
                    content.write(buffer, 0, count)
                }
                if (content.size() > limit) {
                    ReadResult.Failed("the skill is larger than 2 MB")
                } else {
                    ReadResult.Bytes(content.toByteArray())
                }
            }
        } catch (failure: IOException) {
            ReadResult.Failed("the file could not be read")
        } catch (failure: SecurityException) {
            ReadResult.Failed("the file could not be read")
        }
    }
}
