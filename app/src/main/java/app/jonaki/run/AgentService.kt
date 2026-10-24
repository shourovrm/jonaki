package app.jonaki.run

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import app.jonaki.JonakiApplication
import app.jonaki.MainActivity
import app.jonaki.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the process alive while an agent run is active, so Android does not
 * stop a run when the user leaves the app (D-005). The runs themselves live in
 * [AgentRunner]; this service only watches them and stops when none is left.
 *
 * A foreground service keeps the process, not the processor: with the screen
 * off the phone may still sleep, which stops the run's timers and lets its
 * connections die. The service therefore holds a wake lock while it lives (D-161).
 */
class AgentService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val wakeLock: PowerManager.WakeLock by lazy {
        val lock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
        // One lock for all runs: every new run renews it, and one release ends it.
        lock.setReferenceCounted(false)
        lock
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val runner = (application as JonakiApplication).runner
        if (intent?.action == ACTION_STOP_RUNS) {
            // The notification's Stop button: stop every run; the collector below then ends the service.
            runner.runningThreadIds.value.forEach { threadId -> runner.stop(threadId) }
        }
        startInForeground(getString(R.string.run_notification_title_many), null)
        wakeLock.acquire(WAKE_LOCK_LIMIT_MILLIS)
        scope.launch {
            runner.runningThreadIds.collect { threadIds ->
                if (threadIds.isEmpty()) {
                    stopSelf()
                } else {
                    showRunning(threadIds)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        scope.cancel()
        super.onDestroy()
    }

    /** Names the single running thread, or counts the threads when several run. */
    private suspend fun showRunning(threadIds: Set<String>) {
        val manager = getSystemService(NotificationManager::class.java)
        if (threadIds.size == 1) {
            val threadTitle = (application as JonakiApplication).database.threadDao().find(threadIds.single())?.title
            val title = threadTitle?.takeIf { it.isNotBlank() } ?: getString(R.string.run_notification_title)
            manager.notify(NOTIFICATION_ID, buildNotification(title, null))
        } else {
            val text = getString(R.string.run_notification_threads, threadIds.size)
            manager.notify(NOTIFICATION_ID, buildNotification(getString(R.string.run_notification_title_many), text))
        }
    }

    private fun buildNotification(title: String, text: String?): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopRuns = PendingIntent.getService(
            this,
            1,
            Intent(this, AgentService::class.java).setAction(ACTION_STOP_RUNS),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .addAction(Notification.Action.Builder(null, getString(R.string.run_notification_stop), stopRuns).build())
            .setOngoing(true)
            .build()
    }

    private fun startInForeground(title: String, text: String?) {
        val manager = getSystemService(NotificationManager::class.java)
        // Android keeps the importance a channel was first created with, so the quieter level needs a new id.
        manager.deleteNotificationChannel(OLD_CHANNEL_ID)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.run_channel_name), NotificationManager.IMPORTANCE_MIN),
        )
        val notification = buildNotification(title, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "runs-quiet"
        private const val OLD_CHANNEL_ID = "agent_runs"

        /** Sent by the notification's Stop button. */
        const val ACTION_STOP_RUNS = "app.jonaki.STOP_RUNS"
        private const val NOTIFICATION_ID = 1
        private const val WAKE_LOCK_TAG = "jonaki:agent-run"

        /**
         * Android releases the lock by itself after this long, so a run that never
         * ends cannot keep the phone awake for good. Each new run starts the time again.
         */
        private const val WAKE_LOCK_LIMIT_MILLIS = 30 * 60 * 1000L

        fun start(context: Context) {
            try {
                context.startForegroundServiceCompat(Intent(context, AgentService::class.java))
            } catch (notAllowed: IllegalStateException) {
                // Android 12 and later refuse a foreground service started from the background, as a
                // scheduled task's worker is; that worker keeps the process alive for the run (D-100).
            }
        }
    }
}
