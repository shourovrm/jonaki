package app.jonaki.feature.settings

import android.Manifest
import android.os.Build
import androidx.annotation.StringRes

/**
 * What Settings > Permissions shows for one live row (D-124). The user sees
 * three words (D-128): "Allowed", "Not allowed" (NOT_ALLOWED and OFF) and
 * "Blocked"; Android 14's partial photo access keeps "Selected photos".
 */
enum class PermissionStatus {
    ALLOWED,

    /** Android 14's "Select photos": only the photos the user picked. */
    SELECTED_PHOTOS,

    /** Not granted, and Android's dialog can still appear: never asked, closed with Back, or refused once. */
    NOT_ALLOWED,

    /** Refused for good ("Don't ask again", or twice from Android 11); only system settings can change it. */
    BLOCKED,

    /**
     * A switch that lives in system settings is off: Alarms & reminders, or
     * notifications before Android 13. Shown as "Not allowed", but no dialog
     * exists, so the row opens system settings.
     */
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

    /**
     * Not a manifest permission: Android's battery optimisation list is a
     * setting the user changes, and opening it needs no permission.
     */
    BATTERY(
        R.string.settings_permissions_battery,
        R.string.settings_permissions_battery_purpose,
        emptyList(),
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
 * Turns what Android reports into a row's status. Android's rationale flag
 * (shouldShowRequestPermissionRationale) is true after one refusal, but
 * false both before any refusal and after a refusal for good, so the app
 * passes [refusedBefore] from its own record of refusals (D-127).
 */
object PermissionStatuses {
    fun runtime(granted: Boolean, refusedBefore: Boolean, showsRationale: Boolean): PermissionStatus = when {
        granted -> PermissionStatus.ALLOWED
        showsRationale -> PermissionStatus.NOT_ALLOWED
        refusedBefore -> PermissionStatus.BLOCKED
        else -> PermissionStatus.NOT_ALLOWED
    }

    /**
     * Whether the dialog that just closed was a refusal. Back closes it
     * without one and leaves the rationale false; a first refusal turns the
     * rationale true, and a refusal for good turns a true rationale false.
     */
    fun refusedNow(granted: Boolean, rationaleBefore: Boolean, rationaleAfter: Boolean): Boolean =
        !granted && (rationaleBefore || rationaleAfter)

    /** Below Android 13 there is no notification permission, only the app's switch in system settings. */
    fun notifications(
        sdkInt: Int,
        permissionGranted: Boolean,
        notificationsEnabled: Boolean,
        refusedBefore: Boolean,
        showsRationale: Boolean,
    ): PermissionStatus {
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            return runtime(permissionGranted, refusedBefore, showsRationale)
        }
        if (notificationsEnabled) {
            return PermissionStatus.ALLOWED
        }
        return PermissionStatus.OFF
    }

    /** The calendar tools read and add events, so the row is allowed only with both. */
    fun calendar(readGranted: Boolean, writeGranted: Boolean, refusedBefore: Boolean, showsRationale: Boolean): PermissionStatus =
        runtime(readGranted && writeGranted, refusedBefore, showsRationale)

    /** The same reading as the composer's gallery (D-086). */
    fun photos(
        sdkInt: Int,
        imagesGranted: Boolean,
        selectedPhotosGranted: Boolean,
        externalStorageGranted: Boolean,
        refusedBefore: Boolean,
        showsRationale: Boolean,
    ): PermissionStatus {
        if (sdkInt < Build.VERSION_CODES.TIRAMISU) {
            return runtime(externalStorageGranted, refusedBefore, showsRationale)
        }
        if (imagesGranted) {
            return PermissionStatus.ALLOWED
        }
        val canSelectPhotos = sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        if (canSelectPhotos && selectedPhotosGranted) {
            return PermissionStatus.SELECTED_PHOTOS
        }
        return runtime(granted = false, refusedBefore = refusedBefore, showsRationale = showsRationale)
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

    /** "Unrestricted" is Android's name for an app that battery optimisation leaves alone. */
    fun battery(isIgnoringOptimisation: Boolean): PermissionStatus =
        if (isIgnoringOptimisation) PermissionStatus.ALLOWED else PermissionStatus.OFF

    fun buttonFor(status: PermissionStatus): PermissionButton = when (status) {
        PermissionStatus.NOT_ALLOWED -> PermissionButton.ALLOW
        PermissionStatus.BLOCKED, PermissionStatus.OFF -> PermissionButton.OPEN_SETTINGS
        PermissionStatus.ALLOWED, PermissionStatus.SELECTED_PHOTOS -> PermissionButton.NONE
    }
}
