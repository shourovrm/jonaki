package app.jonaki.files

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Opens a thread file in another app, through the same FileProvider that
 * share_file uses (D-044), for the files a run_code program saved (D-090).
 * HTML in artifacts/ opens in Jonaki's own viewer instead; the caller
 * decides that.
 */
object ThreadFileViewer {
    enum class Result {
        OPENED,
        MISSING,
        NO_APP,
    }

    /** [context] must be an activity, or the system refuses to start another app from it. */
    fun open(context: Context, file: File): Result {
        if (!file.isFile) {
            return Result.MISSING
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + AndroidFileDestinations.FILE_PROVIDER_SUFFIX, file)
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, viewTypeOf(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            context.startActivity(view)
            Result.OPENED
        } catch (noApp: ActivityNotFoundException) {
            Result.NO_APP
        }
    }

    /** An unknown type is offered to every app rather than to the few that take application/octet-stream. */
    private fun viewTypeOf(file: File): String {
        val type = AndroidFileDestinations.mimeTypeOf(file)
        return if (type == UNKNOWN_TYPE) "*/*" else type
    }

    private const val UNKNOWN_TYPE = "application/octet-stream"
}
