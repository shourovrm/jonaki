package app.jonaki.feature.gallery

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoAccessTest {
    private val android12 = 32
    private val android13 = 33
    private val android14 = 34

    @Test
    fun eachAndroidVersionAsksForItsOwnPermissions() {
        assertEquals(listOf(Manifest.permission.READ_EXTERNAL_STORAGE), PhotoAccess.permissionsToAsk(android12))
        assertEquals(listOf(Manifest.permission.READ_MEDIA_IMAGES), PhotoAccess.permissionsToAsk(android13))
        assertEquals(
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
            PhotoAccess.permissionsToAsk(android14),
        )
    }

    @Test
    fun android14SelectedPhotosOnlyIsSomeAccess() {
        val granted = setOf(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

        assertEquals(PhotoGrant.SOME, PhotoAccess.grant(android14) { permission -> permission in granted })
    }

    @Test
    fun allPhotosWinsOverSelectedPhotos() {
        val granted = setOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

        assertEquals(PhotoGrant.ALL, PhotoAccess.grant(android14) { permission -> permission in granted })
    }

    @Test
    fun theSelectedPhotosPermissionMeansNothingBeforeAndroid14() {
        val granted = setOf(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

        assertEquals(PhotoGrant.NONE, PhotoAccess.grant(android13) { permission -> permission in granted })
    }

    @Test
    fun olderAndroidReadsStoragePermission() {
        val granted = setOf(Manifest.permission.READ_EXTERNAL_STORAGE)

        assertEquals(PhotoGrant.ALL, PhotoAccess.grant(android12) { permission -> permission in granted })
        assertEquals(PhotoGrant.NONE, PhotoAccess.grant(android13) { permission -> permission in granted })
    }

    @Test
    fun theFirstTapAsksAndAfterARefusalTheSystemPickerOpens() {
        assertEquals(PhotosAction.ASK_PERMISSION, PhotoAccess.onPhotosTapped(PhotoGrant.NONE, refusedBefore = false))
        assertEquals(PhotosAction.SYSTEM_PICKER, PhotoAccess.afterAnswer(PhotoGrant.NONE))
        assertEquals(PhotosAction.SYSTEM_PICKER, PhotoAccess.onPhotosTapped(PhotoGrant.NONE, refusedBefore = true))
    }

    @Test
    fun accessGrantedInSettingsAfterARefusalOpensTheGallery() {
        assertEquals(PhotosAction.OPEN_GALLERY, PhotoAccess.onPhotosTapped(PhotoGrant.ALL, refusedBefore = true))
        assertEquals(PhotosAction.OPEN_GALLERY, PhotoAccess.onPhotosTapped(PhotoGrant.SOME, refusedBefore = true))
    }

    @Test
    fun choosingSomePhotosInTheDialogOpensTheGallery() {
        assertEquals(PhotosAction.OPEN_GALLERY, PhotoAccess.afterAnswer(PhotoGrant.SOME))
        assertEquals(PhotosAction.OPEN_GALLERY, PhotoAccess.afterAnswer(PhotoGrant.ALL))
    }
}
