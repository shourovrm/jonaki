package app.jonaki.localmodels

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.jonaki.JonakiApplication
import app.jonaki.MainActivity
import app.jonaki.R
import app.jonaki.core.localmodels.DownloadOutcome
import app.jonaki.core.localmodels.DownloadRequest
import java.io.IOException
import kotlinx.coroutines.CancellationException

/**
 * Downloads one model file in a foreground service of type dataSync, which
 * Jonaki already declares for agent runs (D-133). A network error ends the
 * attempt with a retry; the next attempt asks the huggingface.co address
 * again (its CDN redirect expires) and resumes from the part file. A wrong
 * SHA-256 deletes the file and fails. Cancel from the page or the
 * notification deletes the part file.
 */
class ModelDownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private var lastReportMillis = 0L
    private var lastReportedPercent = -1

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val spec = DownloadSpec.from(inputData)
        return foregroundInfo(spec?.fileName.orEmpty(), downloadedBytes = 0, totalBytes = spec?.sizeBytes ?: 0)
    }

    override suspend fun doWork(): Result {
        val spec = DownloadSpec.from(inputData) ?: return Result.failure()
        val localModels = (applicationContext as JonakiApplication).localModels
        val store = localModels.store
        if (store.isDownloaded(spec.fileName)) {
            return Result.success()
        }
        val needed = localModels.downloads.neededBytes(spec)
        if (DeviceResources.allocatableBytes(applicationContext, store.modelsFolder.apply { mkdirs() }) < needed) {
            return Result.failure(reason(REASON_NO_SPACE, needed))
        }
        goToForeground(spec, store.partialBytes(spec.fileName))
        val request = DownloadRequest(
            url = spec.url,
            expectedBytes = spec.sizeBytes,
            expectedSha256 = spec.sha256,
            partFile = store.partFile(spec.fileName),
            targetFile = store.modelFile(spec.fileName),
        )
        return try {
            val outcome = localModels.downloader.download(request) { downloaded -> report(spec, downloaded) }
            resultOf(outcome, localModels)
        } catch (exception: IOException) {
            if (runAttemptCount + 1 < MAX_ATTEMPTS) Result.retry() else Result.failure(reason(REASON_FAILED))
        } catch (cancelled: CancellationException) {
            if (stopReason == WorkInfo.STOP_REASON_CANCELLED_BY_APP) {
                store.deletePartial(spec.fileName)
            }
            throw cancelled
        }
    }

    private fun resultOf(outcome: DownloadOutcome, localModels: LocalModels): Result = when (outcome) {
        DownloadOutcome.Finished -> {
            localModels.markChanged()
            Result.success()
        }
        DownloadOutcome.HashMismatch -> Result.failure(reason(REASON_DAMAGED))
        is DownloadOutcome.Refused -> Result.failure(reason(REASON_FAILED))
    }

    /**
     * Android 12 and later refuse a foreground service started from the
     * background, for example on a retry while the app is closed. The
     * download then goes on as ordinary background work.
     */
    private suspend fun goToForeground(spec: DownloadSpec, partialBytes: Long) {
        try {
            setForeground(foregroundInfo(spec.fileName, partialBytes, spec.sizeBytes))
        } catch (refused: IllegalStateException) {
            // Carry on without the notification.
        }
    }

    /** At most once a second and once a percent, so neither WorkManager's database nor the notification is flooded. */
    private suspend fun report(spec: DownloadSpec, downloadedBytes: Long) {
        val percent = percentOf(downloadedBytes, spec.sizeBytes)
        val now = SystemClock.elapsedRealtime()
        if (percent == lastReportedPercent || now - lastReportMillis < REPORT_INTERVAL_MILLIS) {
            return
        }
        lastReportedPercent = percent
        lastReportMillis = now
        setProgress(
            Data.Builder()
                .putLong(PROGRESS_DOWNLOADED, downloadedBytes)
                .putLong(PROGRESS_TOTAL, spec.sizeBytes)
                .build(),
        )
        notifications.notify(NOTIFICATION_ID_BASE + spec.fileName.hashCode(), notification(spec.fileName, downloadedBytes, spec.sizeBytes))
    }

    private fun foregroundInfo(fileName: String, downloadedBytes: Long, totalBytes: Long): ForegroundInfo {
        val notification = notification(fileName, downloadedBytes, totalBytes)
        val notificationId = NOTIFICATION_ID_BASE + fileName.hashCode()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }
        return ForegroundInfo(notificationId, notification)
    }

    private fun notification(fileName: String, downloadedBytes: Long, totalBytes: Long): Notification {
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.model_download_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
        val openApp = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val cancel = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val cancelAction = Notification.Action.Builder(null, applicationContext.getString(R.string.model_download_cancel), cancel).build()
        return Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.model_download_title, fileName))
            .setProgress(PERCENT, percentOf(downloadedBytes, totalBytes), totalBytes <= 0)
            .setContentIntent(openApp)
            .addAction(cancelAction)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun percentOf(downloadedBytes: Long, totalBytes: Long): Int {
        if (totalBytes <= 0) {
            return 0
        }
        return (downloadedBytes * PERCENT / totalBytes).toInt()
    }

    private fun reason(reason: String, neededBytes: Long = 0): Data = Data.Builder()
        .putString(OUTPUT_REASON, reason)
        .putLong(OUTPUT_NEEDED_BYTES, neededBytes)
        .build()

    companion object {
        const val PROGRESS_DOWNLOADED = "downloaded"
        const val PROGRESS_TOTAL = "total"
        const val OUTPUT_REASON = "reason"
        const val OUTPUT_NEEDED_BYTES = "needed_bytes"
        const val REASON_FAILED = "failed"
        const val REASON_DAMAGED = "damaged"
        const val REASON_NO_SPACE = "no_space"

        private const val CHANNEL_ID = "model_downloads"
        private const val NOTIFICATION_ID_BASE = 4_000

        /** Five attempts with growing waits (30 s, 1 min, 2 min, 4 min) cover a phone that drops off Wi-Fi for a while. */
        private const val MAX_ATTEMPTS = 5
        private const val REPORT_INTERVAL_MILLIS = 1_000L
        private const val PERCENT = 100
    }
}
