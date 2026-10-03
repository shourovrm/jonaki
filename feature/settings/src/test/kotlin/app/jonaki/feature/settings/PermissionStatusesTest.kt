package app.jonaki.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionStatusesTest {
    private val android11 = 30
    private val android12 = 31
    private val android12L = 32
    private val android13 = 33
    private val android14 = 34

    @Test
    fun runtimePermissionNeverRequestedIsNotAsked() {
        assertEquals(PermissionStatus.NOT_ASKED, PermissionStatuses.runtime(granted = false, requestedBefore = false))
    }

    @Test
    fun runtimePermissionRequestedAndNotGrantedIsDenied() {
        assertEquals(PermissionStatus.DENIED, PermissionStatuses.runtime(granted = false, requestedBefore = true))
    }

    @Test
    fun grantedIsAllowedWhetherOrNotJonakiAsked() {
        // Granted in system settings without Jonaki asking still counts.
        assertEquals(PermissionStatus.ALLOWED, PermissionStatuses.runtime(granted = true, requestedBefore = false))
        assertEquals(PermissionStatus.ALLOWED, PermissionStatuses.runtime(granted = true, requestedBefore = true))
    }

    @Test
    fun notificationsFromAndroid13FollowThePermission() {
        val notAsked = PermissionStatuses.notifications(android13, permissionGranted = false, notificationsEnabled = false, requestedBefore = false)
        val denied = PermissionStatuses.notifications(android13, permissionGranted = false, notificationsEnabled = false, requestedBefore = true)
        val allowed = PermissionStatuses.notifications(android14, permissionGranted = true, notificationsEnabled = true, requestedBefore = true)
        assertEquals(PermissionStatus.NOT_ASKED, notAsked)
        assertEquals(PermissionStatus.DENIED, denied)
        assertEquals(PermissionStatus.ALLOWED, allowed)
    }

    @Test
    fun notificationsBefore13FollowTheAppSwitchAndAreNeverNotAsked() {
        // Below Android 13 there is no dialog, so the app switch is the whole answer.
        val on = PermissionStatuses.notifications(android12L, permissionGranted = false, notificationsEnabled = true, requestedBefore = false)
        val off = PermissionStatuses.notifications(android12L, permissionGranted = false, notificationsEnabled = false, requestedBefore = false)
        assertEquals(PermissionStatus.ALLOWED, on)
        assertEquals(PermissionStatus.DENIED, off)
    }

    @Test
    fun calendarNeedsBothReadAndWrite() {
        assertEquals(PermissionStatus.ALLOWED, PermissionStatuses.calendar(readGranted = true, writeGranted = true, requestedBefore = true))
        assertEquals(PermissionStatus.DENIED, PermissionStatuses.calendar(readGranted = true, writeGranted = false, requestedBefore = true))
        assertEquals(PermissionStatus.NOT_ASKED, PermissionStatuses.calendar(readGranted = false, writeGranted = false, requestedBefore = false))
    }

    @Test
    fun photosAllowedWithReadMediaImages() {
        val status = PermissionStatuses.photos(
            android14,
            imagesGranted = true,
            selectedPhotosGranted = true,
            externalStorageGranted = false,
            requestedBefore = true,
        )
        assertEquals(PermissionStatus.ALLOWED, status)
    }

    @Test
    fun photosWithOnlyUserSelectedOnAndroid14AreSelectedPhotos() {
        val status = PermissionStatuses.photos(
            android14,
            imagesGranted = false,
            selectedPhotosGranted = true,
            externalStorageGranted = false,
            requestedBefore = true,
        )
        assertEquals(PermissionStatus.SELECTED_PHOTOS, status)
    }

    @Test
    fun userSelectedDoesNotCountBeforeAndroid14() {
        val status = PermissionStatuses.photos(
            android13,
            imagesGranted = false,
            selectedPhotosGranted = true,
            externalStorageGranted = false,
            requestedBefore = true,
        )
        assertEquals(PermissionStatus.DENIED, status)
    }

    @Test
    fun photosOnAndroid12UseExternalStorage() {
        val allowed = PermissionStatuses.photos(
            android12L,
            imagesGranted = false,
            selectedPhotosGranted = false,
            externalStorageGranted = true,
            requestedBefore = false,
        )
        val notAsked = PermissionStatuses.photos(
            android12L,
            imagesGranted = true,
            selectedPhotosGranted = false,
            externalStorageGranted = false,
            requestedBefore = false,
        )
        assertEquals(PermissionStatus.ALLOWED, allowed)
        assertEquals(PermissionStatus.NOT_ASKED, notAsked)
    }

    @Test
    fun photosOnAndroid13IgnoreExternalStorage() {
        val status = PermissionStatuses.photos(
            android13,
            imagesGranted = false,
            selectedPhotosGranted = false,
            externalStorageGranted = true,
            requestedBefore = false,
        )
        assertEquals(PermissionStatus.NOT_ASKED, status)
    }

    @Test
    fun exactAlarmsAreAllowedOrOff() {
        assertEquals(PermissionStatus.ALLOWED, PermissionStatuses.alarms(android14, canScheduleExactAlarms = true))
        assertEquals(PermissionStatus.OFF, PermissionStatuses.alarms(android12, canScheduleExactAlarms = false))
    }

    @Test
    fun exactAlarmsBeforeAndroid12AreAlwaysAllowed() {
        assertEquals(PermissionStatus.ALLOWED, PermissionStatuses.alarms(android11, canScheduleExactAlarms = false))
    }

    @Test
    fun onlyNotAskedOffersAllowAndOnlyDeniedOrOffOfferSettings() {
        assertEquals(PermissionButton.ALLOW, PermissionStatuses.buttonFor(PermissionStatus.NOT_ASKED))
        assertEquals(PermissionButton.OPEN_SETTINGS, PermissionStatuses.buttonFor(PermissionStatus.DENIED))
        assertEquals(PermissionButton.OPEN_SETTINGS, PermissionStatuses.buttonFor(PermissionStatus.OFF))
        assertEquals(PermissionButton.NONE, PermissionStatuses.buttonFor(PermissionStatus.ALLOWED))
        assertEquals(PermissionButton.NONE, PermissionStatuses.buttonFor(PermissionStatus.SELECTED_PHOTOS))
    }

    @Test
    fun everyPermissionBelongsToOneRow() {
        val all = PermissionRow.entries.flatMap { row -> row.permissions } + AlwaysOnRow.entries.flatMap { row -> row.permissions }
        assertEquals(all.size, all.toSet().size)
    }
}
