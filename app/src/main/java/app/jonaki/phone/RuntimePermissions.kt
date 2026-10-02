package app.jonaki.phone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.jonaki.files.VisibleActivity

/**
 * Asks for Android permissions the first time an action needs them (plan M9
 * step 1). The dialog needs Jonaki's window, so the agent service asks
 * through [VisibleActivity] (D-045).
 */
class RuntimePermissions(
    private val context: Context,
    private val visibleActivity: VisibleActivity,
) {
    enum class Answer {
        GRANTED,
        DENIED,

        /** Not granted yet, and no Jonaki window is on screen to ask in. */
        NOT_ON_SCREEN,
    }

    /** Granted when every one of [permissions] is; asks only for the missing ones. */
    suspend fun request(permissions: List<String>): Answer {
        val missing = permissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            return Answer.GRANTED
        }
        val answer = visibleActivity.launchForResult(ActivityResultContracts.RequestMultiplePermissions(), missing.toTypedArray())
        return when (answer) {
            is VisibleActivity.Answer.Result -> {
                val allGranted = missing.all { permission -> answer.value[permission] == true }
                if (allGranted) Answer.GRANTED else Answer.DENIED
            }
            VisibleActivity.Answer.NotOnScreen -> Answer.NOT_ON_SCREEN
            VisibleActivity.Answer.NoAppToHandle -> Answer.DENIED
        }
    }

    /**
     * Asks for the notification permission where Android has one (13 and
     * later), then checks the switch the user can turn off on any version.
     */
    suspend fun requestNotifications(): Answer {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val answer = request(listOf(Manifest.permission.POST_NOTIFICATIONS))
            if (answer != Answer.GRANTED) {
                return answer
            }
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return Answer.DENIED
        }
        return Answer.GRANTED
    }
}
