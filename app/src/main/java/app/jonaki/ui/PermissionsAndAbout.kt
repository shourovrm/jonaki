package app.jonaki.ui

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.jonaki.JonakiApplication
import app.jonaki.feature.gallery.PhotoAccess
import app.jonaki.feature.gallery.PhotoGrant
import app.jonaki.feature.settings.PermissionButton
import app.jonaki.feature.settings.PermissionRow
import app.jonaki.feature.settings.PermissionRowUi
import app.jonaki.feature.settings.PermissionStatus
import app.jonaki.feature.settings.PermissionStatuses

/** The page the About section's GitHub row opens. */
private const val GITHUB_URL = "https://github.com/shourovrm/jonaki"

/**
 * Reads Settings > Permissions (D-124, D-127). [refusedPermissions] is the
 * app's record of refusals; Android alone cannot tell "Not asked" from a
 * refusal for good.
 */
internal fun readPermissionRows(context: Context, refusedPermissions: Set<String>): List<PermissionRowUi> =
    PermissionRow.entries.map { row -> PermissionRowUi(row, statusOf(row, context, refusedPermissions)) }

private fun statusOf(row: PermissionRow, context: Context, refusedPermissions: Set<String>): PermissionStatus {
    val sdkInt = Build.VERSION.SDK_INT
    val isGranted = { permission: String ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    val refusedBefore = row.permissions.any { permission -> permission in refusedPermissions }
    val showsRationale = showsRationaleForAny(context, row.permissions)
    return when (row) {
        PermissionRow.NOTIFICATIONS -> PermissionStatuses.notifications(
            sdkInt,
            permissionGranted = isGranted(Manifest.permission.POST_NOTIFICATIONS),
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            refusedBefore = refusedBefore,
            showsRationale = showsRationale,
        )
        PermissionRow.CALENDAR -> PermissionStatuses.calendar(
            readGranted = isGranted(Manifest.permission.READ_CALENDAR),
            writeGranted = isGranted(Manifest.permission.WRITE_CALENDAR),
            refusedBefore = refusedBefore,
            showsRationale = showsRationale,
        )
        PermissionRow.PHOTOS -> PermissionStatuses.photos(
            sdkInt,
            imagesGranted = isGranted(Manifest.permission.READ_MEDIA_IMAGES),
            selectedPhotosGranted = isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
            externalStorageGranted = isGranted(Manifest.permission.READ_EXTERNAL_STORAGE),
            refusedBefore = refusedBefore,
            showsRationale = showsRationale,
        )
        PermissionRow.ALARMS -> PermissionStatuses.alarms(sdkInt, canScheduleExactAlarms(context))
        PermissionRow.BATTERY -> PermissionStatuses.battery(isIgnoringBatteryOptimisation(context))
    }
}

private fun showsRationaleForAny(context: Context, permissions: List<String>): Boolean =
    permissions.any { permission -> showsRationale(context, permission) }

/** Android's flag for a dialog it can show again after one refusal. It is kept per activity; Jonaki's screens live in MainActivity. */
internal fun showsRationale(context: Context, permission: String): Boolean {
    val activity = context.findActivity() ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) {
            return current
        }
        current = current.baseContext
    }
    return null
}

private fun canScheduleExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return true
    }
    return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
}

private fun isIgnoringBatteryOptimisation(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

/** "Allow" shows Android's dialog; every other tap opens the system page where the user can change the permission. */
internal suspend fun onPermissionTapped(application: JonakiApplication, context: Context, item: PermissionRowUi) {
    if (PermissionStatuses.buttonFor(item.status) == PermissionButton.ALLOW) {
        askFor(application, context, item.row)
    } else {
        openSystemPageFor(context, item.row)
    }
}

private suspend fun askFor(application: JonakiApplication, context: Context, row: PermissionRow) {
    val permissions = application.runtimePermissions
    when (row) {
        PermissionRow.NOTIFICATIONS -> permissions.requestNotifications()
        PermissionRow.CALENDAR -> permissions.request(listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
        PermissionRow.PHOTOS -> {
            val asked = PhotoAccess.permissionsToAsk(Build.VERSION.SDK_INT)
            permissions.request(asked)
            // The composer never asks again after a refusal, wherever it happened (D-086); Back is no refusal (D-127).
            val refused = application.settings.snapshot.value.refusedPermissions.any { permission -> permission in asked }
            if (currentPhotoGrant(context) == PhotoGrant.NONE && refused) {
                PhotoRefusal.remember(context)
            }
        }
        // A special access switch has no dialog; it is never "Not asked".
        PermissionRow.ALARMS, PermissionRow.BATTERY -> openSystemPageFor(context, row)
    }
}

private fun openSystemPageFor(context: Context, row: PermissionRow) {
    if (row == PermissionRow.BATTERY) {
        openBatteryOptimisationList(context)
        return
    }
    if (row == PermissionRow.ALARMS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", context.packageName, null))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            return
        } catch (missing: ActivityNotFoundException) {
            // Some phones leave this page out; Jonaki's own page in system settings links to it.
        }
    }
    openAppSettings(context)
}

/**
 * Android's list of apps with battery optimisation. The request that jumps
 * straight to a "Let Jonaki ignore" dialog needs its own manifest
 * permission, so the user picks Jonaki from this list instead.
 */
private fun openBatteryOptimisationList(context: Context) {
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (missing: ActivityNotFoundException) {
        // Some phones leave this list out; Jonaki's own page in system settings has a battery entry.
        openAppSettings(context)
    }
}

/** The installed version name, read from the package so the build needs no BuildConfig. */
internal fun installedVersionName(context: Context): String {
    val packageManager = context.packageManager
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(context.packageName, 0)
    }
    return info.versionName.orEmpty()
}

internal fun openGitHub(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (noBrowser: ActivityNotFoundException) {
        // A phone without any browser has nowhere to show the page; the row's text names the address.
    }
}
