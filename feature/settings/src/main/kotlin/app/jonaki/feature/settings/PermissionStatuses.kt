package app.jonaki.feature.settings

import android.Manifest
import android.os.Build
import androidx.annotation.StringRes

/** What Settings > Permissions shows for one live row (D-124). */
enum class PermissionStatus {
    ALLOWED,

    /** Android 14's "Select photos": only the photos the user picked. */
    SELECTED_PHOTOS,

    /** Jonaki never asked, so Android's dialog can still be shown. */
    NOT_ASKED,

    /** Jonaki asked and the permission is not granted; only system settings can change it now. */
    DENIED,

    /** A special access switch (Alarms & reminders) that is off in system settings. */
    OFF,
}

/** The text button at the end of a permission row. */
enum class PermissionButton {
    ALLOW,
    OPEN_SETTINGS,
    NONE,
}

/** A permission the user can grant or take back; each row reads its own status. */
enum class PermissionRow(
    @StringRes val title: Int,
    @StringRes val purpose: Int,
    /** Every manifest permission the row stands for. */
    val permissions: List<String>,
) {
    NOTIFICATIONS(
        R.string.settings_permissions_notifications,
        R.string.settings_permissions_notifications_purpose,
        listOf(Manifest.permission.POST_NOTIFICATIONS),
    ),
    CALENDAR(
        R.string.settings_permissions_calendar,
        R.string.settings_permissions_calendar_purpose,
        listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
    ),
    PHOTOS(
        R.string.settings_permissions_photos,
        R.string.settings_permissions_photos_purpose,
        listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ),
    ),
    ALARMS(
        R.string.settings_permissions_alarms,
        R.string.settings_permissions_alarms_purpose,
        listOf(Manifest.permission.SCHEDULE_EXACT_ALARM),
    ),
}

/** A permission Android grants at install without a dialog; listed so the user sees every one. */
enum class AlwaysOnRow(
    @StringRes val title: Int,
    @StringRes val purpose: Int,
    val permissions: List<String>,
) {
    INTERNET(
        R.string.settings_permissions_internet,
        R.string.settings_permissions_internet_purpose,
        listOf(Manifest.permission.INTERNET),
    ),
    NETWORK_STATE(
        R.string.settings_permissions_network_state,
        R.string.settings_permissions_network_state_purpose,
        listOf(Manifest.permission.ACCESS_NETWORK_STATE),
    ),
    BACKGROUND(
        R.string.settings_permissions_background,
        R.string.settings_permissions_background_purpose,
        listOf(Manifest.permission.FOREGROUND_SERVICE, Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC),
    ),
    BOOT(
        R.string.settings_permissions_boot,
        R.string.settings_permissions_boot_purpose,
        listOf(Manifest.permission.RECEIVE_BOOT_COMPLETED),
    ),
    KEEP_AWAKE(
        R.string.settings_permissions_keep_awake,
        R.string.settings_permissions_keep_awake_purpose,
        listOf(Manifest.permission.WAKE_LOCK),
    ),
}

/**
 * Turns what Android reports into a row's status. Android cannot tell a
 * permission that was never asked for from one that was refused, so the
 * app passes [requestedBefore] from its own record of requests.
 */
object PermissionStatuses {
    fun runtime(granted: Boolean, requestedBefore: Boolean): PermissionStatus = when {
        granted -> PermissionStatus.ALLOWED
        requestedBefore -> PermissionStatus.DENIED
        else -> PermissionStatus.NOT_ASKED
    }

    /** Below Android 13 there is no notification permission, only the app's switch in system settings. */
    fun notifications(
        sdkInt: Int,
        permissionGranted: Boolean,
        notificationsEnabled: Boolean,
        requestedBefore: Boolean,
    ): PermissionStatus {
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            return runtime(permissionGranted, requestedBefore)
        }
        if (notificationsEnabled) {
            return PermissionStatus.ALLOWED
        }
        return PermissionStatus.DENIED
    }

    /** The calendar tools read and add events, so the row is allowed only with both. */
    fun calendar(readGranted: Boolean, writeGranted: Boolean, requestedBefore: Boolean): PermissionStatus =
        runtime(readGranted && writeGranted, requestedBefore)

    /** The same reading as the composer's gallery (D-086). */
    fun photos(
        sdkInt: Int,
        imagesGranted: Boolean,
        selectedPhotosGranted: Boolean,
        externalStorageGranted: Boolean,
        requestedBefore: Boolean,
    ): PermissionStatus {
        if (sdkInt < Build.VERSION_CODES.TIRAMISU) {
            return runtime(externalStorageGranted, requestedBefore)
        }
        if (imagesGranted) {
            return PermissionStatus.ALLOWED
        }
        val canSelectPhotos = sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        if (canSelectPhotos && selectedPhotosGranted) {
            return PermissionStatus.SELECTED_PHOTOS
        }
        return runtime(granted = false, requestedBefore = requestedBefore)
    }

    /** Exact alarms need "Alarms & reminders" from Android 12; before that every app may set them. */
    fun alarms(sdkInt: Int, canScheduleExactAlarms: Boolean): PermissionStatus {
        if (sdkInt < Build.VERSION_CODES.S) {
            return PermissionStatus.ALLOWED
        }
        if (canScheduleExactAlarms) {
            return PermissionStatus.ALLOWED
        }
        return PermissionStatus.OFF
    }

    fun buttonFor(status: PermissionStatus): PermissionButton = when (status) {
        PermissionStatus.NOT_ASKED -> PermissionButton.ALLOW
        PermissionStatus.DENIED, PermissionStatus.OFF -> PermissionButton.OPEN_SETTINGS
        PermissionStatus.ALLOWED, PermissionStatus.SELECTED_PHOTOS -> PermissionButton.NONE
    }
}
