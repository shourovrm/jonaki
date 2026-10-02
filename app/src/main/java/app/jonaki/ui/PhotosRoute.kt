package app.jonaki.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.jonaki.JonakiApplication
import app.jonaki.feature.gallery.GallerySheet
import app.jonaki.feature.gallery.PhotoAccess
import app.jonaki.feature.gallery.PhotoGrant
import app.jonaki.feature.gallery.PhotosAction

/**
 * The composer's Photos choice (D-085, D-086). Returns what tapping Photos
 * does, and shows Jonaki's gallery sheet while it is open. The first tap
 * asks for the photo permission; after a refusal every tap opens the
 * system photo picker instead, until access is granted in system settings.
 * Picked images become chips through the same path as picked files.
 */
@Composable
fun rememberPhotosChoice(threadKey: String, application: JonakiApplication): () -> Unit {
    val context = LocalContext.current
    var galleryOpen by rememberSaveable(threadKey) { mutableStateOf(false) }
    var grant by remember { mutableStateOf(currentPhotoGrant(context)) }
    // Bumped when the access changes, so the gallery reads its lists again.
    var reloadKey by remember { mutableIntStateOf(0) }

    val systemPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        application.incomingShares.attach(threadKey, uris)
    }
    val openSystemPicker = {
        systemPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // The answer map does not tell Android 14's "Select photos" apart, so the grant is read again.
        grant = currentPhotoGrant(context)
        reloadKey += 1
        when (PhotoAccess.afterAnswer(grant)) {
            PhotosAction.SYSTEM_PICKER -> {
                PhotoRefusal.remember(context)
                galleryOpen = false
                openSystemPicker()
            }
            else -> galleryOpen = true
        }
    }
    val askPermission = {
        permissionRequest.launch(PhotoAccess.permissionsToAsk(Build.VERSION.SDK_INT).toTypedArray())
    }

    // Allow all goes through system settings; the grant is read again when Jonaki is back on screen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val resumedGrant = currentPhotoGrant(context)
        if (resumedGrant != grant) {
            grant = resumedGrant
            reloadKey += 1
        }
        if (resumedGrant == PhotoGrant.NONE) {
            galleryOpen = false
        }
    }

    if (galleryOpen) {
        GallerySheet(
            source = application.gallerySource,
            someAccess = grant == PhotoGrant.SOME,
            reloadKey = reloadKey,
            onSelectMore = askPermission,
            onAllowAll = { openAppSettings(context) },
            onAdd = { images ->
                galleryOpen = false
                application.incomingShares.attach(threadKey, images.map { image -> Uri.parse(image.uri) })
            },
            onDismiss = { galleryOpen = false },
        )
    }

    return {
        grant = currentPhotoGrant(context)
        when (PhotoAccess.onPhotosTapped(grant, PhotoRefusal.happened(context))) {
            PhotosAction.OPEN_GALLERY -> galleryOpen = true
            PhotosAction.ASK_PERMISSION -> askPermission()
            PhotosAction.SYSTEM_PICKER -> openSystemPicker()
        }
    }
}

private fun currentPhotoGrant(context: Context): PhotoGrant =
    PhotoAccess.grant(Build.VERSION.SDK_INT) { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

/** Jonaki's page in system settings, where Permissions > Photos and videos offers Allow all. */
private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

/** Whether the user once refused the photo permission; kept so Jonaki never asks a second time (D-086). */
private object PhotoRefusal {
    private const val PREFERENCES = "photo_access"
    private const val REFUSED = "refused"

    fun happened(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(REFUSED, false)

    fun remember(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putBoolean(REFUSED, true).apply()
    }
}
