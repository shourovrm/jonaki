package app.jonaki.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/** The place and the way the downloaded APK is handed to Android's installer. */
class ApkInstaller(private val context: Context) {
    /** One fixed name, overwritten by each download. */
    val apkFile: File = File(File(context.cacheDir, UPDATE_FOLDER), APK_FILE_NAME)

    fun canInstallFromThisApp(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Android 8+ keeps "install unknown apps" per app; this opens Jonaki's own switch. */
    fun openInstallPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** The system installer checks the signature and refuses an APK signed with another key. */
    fun startInstaller() {
        val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.files", apkFile)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(apkUri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private companion object {
        // Matches the cache-path "update" entry in res/xml/shared_file_paths.xml.
        const val UPDATE_FOLDER = "update"
        const val APK_FILE_NAME = "jonaki-update.apk"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
