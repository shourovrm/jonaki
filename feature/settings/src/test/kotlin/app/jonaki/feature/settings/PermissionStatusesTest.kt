package app.jonaki.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionStatusesTest {
    private val android11 = 30
    private val android12 = 31
    private val android12L = 32
    private val android13 = 33
    private val android14 = 34

    @Test
    fun runtimePermissionNeverRefusedIsNotAsked() {
        assertEquals(PermissionStatus.NOT_ASKED, PermissionStatuses.runtime(granted = false, refusedBefore = false, showsRationale = false))
    }

    @Test
    fun refusedOnceIsDeniedAndCanBeAskedAgain() {
        // After one refusal Android shows its dialog again and says so through the rationale.
        val status = PermissionStatuses.runtime(granted = false, refusedBefore = true, showsRationale = true)
        assertEquals(PermissionStatus.DENIED, status)
        assertEquals(PermissionButton.ALLOW, PermissionStatuses.buttonFor(status))
    }

    @Test
    fun refusedAndNoRationaleIsBlockedAndOpensSettings() {
        // "Don't ask again", or a second refusal from Android 11: the dialog never shows again.
        val status = PermissionStatuses.runtime(granted = false, refusedBefore = true, showsRationale = false)
        assertEquals(PermissionStatus.BLOCKED, status)
        assertEquals(PermissionButton.OPEN_SETTINGS, PermissionStatuses.buttonFor(status))
    }

    @Test
    fun rationaleWithoutARecordStillCountsAsARefusal() {
        // A refusal on a build before the record, or a permission taken back in system settings.
        val status = PermissionStatuses.runtime(granted = false, refusedBefore = false, showsRationale = true)
        assertEquals(PermissionStatus.DENIED, status)
    }

    @Test
    fun grantedIsAllowedWhetherOrNotJonakiAsked() {
        // Granted in system settings without Jonaki asking still counts.
        assertEquals(PermissionStatus.ALLOWED, PermissionStatuses.runtime(granted = true, refusedBefore = false, showsRationale = false))
        assertEquals(PermissionStatus.ALLOWED, PermissionStatuses.runtime(granted = true, refusedBefore = true, showsRationale = true))
    }

    @Test
    fun firstRefusalIsARefusal() {
        assertTrue(PermissionStatuses.refusedNow(granted = false, rationaleBefore = false, rationaleAfter = true))
    }

    @Test
    fun dialogDismissedWithBackIsNotARefusal() {
        // Back leaves the rationale false, the same as before the dialog, so Android will ask again.
        assertFalse(PermissionStatuses.refusedNow(granted = false, rationaleBefore = false, rationaleAfter = false))
    }

    @Test
    fun secondRefusalThatBlocksTheDialogIsARefusal() {
        assertTrue(PermissionStatuses.refusedNow(granted = false, rationaleBefore = true, rationaleAfter = false))
    }

    @Test
    fun grantIsNeverARefusal() {
        assertFalse(PermissionStatuses.refusedNow(granted = true, rationaleBefore = true, rationaleAfter = false))
    }

    @Test
    fun notificationsFromAndroid13FollowThePermission() {
        val notAsked = PermissionStatuses.notifications(android13, permissionGranted = false, notificationsEnabled = false, refusedBefore = false, showsRationale = false)
        val denied = PermissionStatuses.notifications(android13, permissionGranted = false, notificationsEnabled = false, refusedBefore = true, showsRationale = false)
        val allowed = PermissionStatuses.notifications(android14, permissionGranted = true, notificationsEnabled = true, refusedBefore = true, showsRationale = false)
        assertEquals(PermissionStatus.NOT_ASKED, notAsked)
        assertEquals(PermissionStatus.BLOCKED, denied)
        assertEquals(PermissionStatus.ALLOWED, allowed)
    }

    @Test
    fun notificationsBefore13FollowTheAppSwitchAndAreNeverNotAsked() {
        // Below Android 13 there is no dialog, so the app switch is the whole answer.
        val on = PermissionStatuses.notifications(android12L, permissionGranted = false, notificationsEnabled = true, refusedBefore = false, showsRationale = false)
        val off = PermissionStatuses.notifications(android12L, permissionGranted = false, notificationsEnabled = false, refusedBefore = false, showsRationale = false)
        assertEquals(PermissionStatus.ALLOWED, on)
        assertEquals(PermissionStatus.BLOCKED, off)
    }

    @Test
    fun calendarNeedsBothReadAndWrite() {
        assertEquals(PermissionStatus.ALLOWED, PermissionStatuses.calendar(readGranted = true, writeGranted = true, refusedBefore = true, showsRationale = false))
        assertEquals(PermissionStatus.BLOCKED, PermissionStatuses.calendar(readGranted = true, writeGranted = false, refusedBefore = true, showsRationale = false))
        assertEquals(PermissionStatus.NOT_ASKED, PermissionStatuses.calendar(readGranted = false, writeGranted = false, refusedBefore = false, showsRationale = false))
    }

    @Test
    fun photosAllowedWithReadMediaImages() {
        val status = PermissionStatuses.photos(
            android14,
            imagesGranted = true,
            selectedPhotosGranted = true,
            externalStorageGranted = false,
            refusedBefore = true, showsRationale = false,
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
            refusedBefore = true, showsRationale = false,
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
            refusedBefore = true, showsRationale = false,
        )
        assertEquals(PermissionStatus.BLOCKED, status)
    }

    @Test
    fun photosOnAndroid12UseExternalStorage() {
        val allowed = PermissionStatuses.photos(
            android12L,
            imagesGranted = false,
            selectedPhotosGranted = false,
            externalStorageGranted = true,
            refusedBefore = false, showsRationale = false,
        )
        val notAsked = PermissionStatuses.photos(
            android12L,
            imagesGranted = true,
            selectedPhotosGranted = false,
            externalStorageGranted = false,
            refusedBefore = false, showsRationale = false,
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
            refusedBefore = false, showsRationale = false,
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
    fun aDialogAndroidStillShowsOffersAllowAndOtherwiseSettings() {
        assertEquals(PermissionButton.ALLOW, PermissionStatuses.buttonFor(PermissionStatus.NOT_ASKED))
        assertEquals(PermissionButton.ALLOW, PermissionStatuses.buttonFor(PermissionStatus.DENIED))
        assertEquals(PermissionButton.OPEN_SETTINGS, PermissionStatuses.buttonFor(PermissionStatus.BLOCKED))
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
