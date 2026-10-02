package app.jonaki.feature.gallery

import android.Manifest
import android.os.Build

/** How much of the photo library Jonaki may read. */
enum class PhotoGrant {
    /** Every image. */
    ALL,

    /** Only the images the user chose in Android 14's "Select photos" (READ_MEDIA_VISUAL_USER_SELECTED). */
    SOME,
    NONE,
}

/** What tapping Photos does. */
enum class PhotosAction {
    OPEN_GALLERY,
    ASK_PERMISSION,
    SYSTEM_PICKER,
}

/**
 * The photo permission rules of D-086. Jonaki asks the first time Photos
 * is tapped, never at start. After a refusal it never asks again: Photos
 * opens the system photo picker, which needs no permission, until the
 * user grants access in system settings.
 */
object PhotoAccess {
    /** The permissions to request together; Android 14 shows its "Select photos" choice only when both are asked. */
    fun permissionsToAsk(sdkInt: Int): List<String> = when {
        sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        sdkInt >= Build.VERSION_CODES.TIRAMISU -> listOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** Reads the grant from [isGranted], which answers for one permission name. */
    fun grant(sdkInt: Int, isGranted: (permission: String) -> Boolean): PhotoGrant {
        val fullPermission = if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (isGranted(fullPermission)) {
            return PhotoGrant.ALL
        }
        val canChooseSome = sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        if (canChooseSome && isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)) {
            return PhotoGrant.SOME
        }
        return PhotoGrant.NONE
    }

    fun onPhotosTapped(grant: PhotoGrant, refusedBefore: Boolean): PhotosAction = when {
        grant != PhotoGrant.NONE -> PhotosAction.OPEN_GALLERY
        refusedBefore -> PhotosAction.SYSTEM_PICKER
        else -> PhotosAction.ASK_PERMISSION
    }

    /** After the permission dialog: no access at all counts as a refusal, and the system picker opens instead. */
    fun afterAnswer(grant: PhotoGrant): PhotosAction = when (grant) {
        PhotoGrant.NONE -> PhotosAction.SYSTEM_PICKER
        else -> PhotosAction.OPEN_GALLERY
    }
}
