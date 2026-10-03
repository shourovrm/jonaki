package app.jonaki.localmodels

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** One file to download: [fileName] is also the local model's id. */
data class DownloadSpec(
    val url: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
) {
    fun toData(): Data = Data.Builder()
        .putString(KEY_URL, url)
        .putString(KEY_FILE_NAME, fileName)
        .putLong(KEY_SIZE, sizeBytes)
        .putString(KEY_SHA256, sha256)
        .build()

    companion object {
        private const val KEY_URL = "url"
        private const val KEY_FILE_NAME = "file_name"
        private const val KEY_SIZE = "size"
        private const val KEY_SHA256 = "sha256"

        fun from(data: Data): DownloadSpec? {
            val url = data.getString(KEY_URL) ?: return null
            val fileName = data.getString(KEY_FILE_NAME) ?: return null
            val sha256 = data.getString(KEY_SHA256) ?: return null
            return DownloadSpec(url, fileName, data.getLong(KEY_SIZE, 0), sha256)
        }
    }
}

/** Where one file's download stands, read from WorkManager. */
sealed interface DownloadState {
    data object Waiting : DownloadState

    data class Running(val downloadedBytes: Long, val totalBytes: Long) : DownloadState

    data object Failed : DownloadState

    data object Damaged : DownloadState

    data class NoSpace(val neededBytes: Long) : DownloadState
}

/**
 * Starts, cancels and watches model downloads (D-133). Each file is one
 * unique WorkManager job, so tapping Download twice does not fetch it
 * twice, and a download survives the app being closed.
 */
class ModelDownloads(
    private val context: Context,
    private val store: LocalModelStore,
    private val scope: CoroutineScope,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    /**
     * Queues the download, or returns the bytes of free storage it needs
     * when the phone has less than the rest of the file plus 1 GB (the
     * research doc's margin, so the phone is not left full). Reads disk:
     * call it off the main thread.
     */
    fun start(spec: DownloadSpec): DownloadState.NoSpace? {
        val needed = neededBytes(spec)
        if (DeviceResources.allocatableBytes(context, store.modelsFolder.apply { mkdirs() }) < needed) {
            return DownloadState.NoSpace(needed)
        }
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(spec.toData())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            // Expedited, so it starts while the user is still on the page and can go to the foreground.
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_DELAY_SECONDS, TimeUnit.SECONDS)
            .addTag(TAG)
            .addTag(FILE_TAG_PREFIX + spec.fileName)
            .build()
        workManager.enqueueUniqueWork(uniqueName(spec.fileName), ExistingWorkPolicy.KEEP, request)
        return null
    }

    /** Stops the download and deletes what it fetched, once WorkManager has stopped it. */
    fun cancel(fileName: String) {
        val uniqueName = uniqueName(fileName)
        workManager.cancelUniqueWork(uniqueName)
        scope.launch {
            // A running worker may still be between two writes; delete only after it has stopped.
            workManager.getWorkInfosForUniqueWorkFlow(uniqueName).first { infos -> infos.all { info -> info.state.isFinished } }
            store.deletePartial(fileName)
        }
    }

    /** Every file with a download waiting, running or failed, by file name. */
    val states: Flow<Map<String, DownloadState>> by lazy { workManager.getWorkInfosByTagFlow(TAG).map(::statesByFile) }

    /** Reads disk: call it off the main thread. */
    fun neededBytes(spec: DownloadSpec): Long = spec.sizeBytes - store.partialBytes(spec.fileName) + STORAGE_MARGIN_BYTES

    private fun statesByFile(infos: List<WorkInfo>): Map<String, DownloadState> {
        val states = mutableMapOf<String, DownloadState>()
        for (info in infos) {
            val fileName = fileNameOf(info) ?: continue
            val state = stateOf(info) ?: continue
            states[fileName] = state
        }
        return states
    }

    private fun fileNameOf(info: WorkInfo): String? =
        info.tags.firstOrNull { tag -> tag.startsWith(FILE_TAG_PREFIX) }?.removePrefix(FILE_TAG_PREFIX)

    /** Null for a finished or cancelled job: the file's presence in the folder says the rest. */
    private fun stateOf(info: WorkInfo): DownloadState? = when (info.state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadState.Waiting
        WorkInfo.State.RUNNING -> runningState(info.progress)
        WorkInfo.State.FAILED -> failedState(info.outputData)
        WorkInfo.State.SUCCEEDED, WorkInfo.State.CANCELLED -> null
    }

    private fun runningState(progress: Data): DownloadState {
        val total = progress.getLong(ModelDownloadWorker.PROGRESS_TOTAL, 0)
        if (total <= 0) {
            return DownloadState.Waiting
        }
        return DownloadState.Running(progress.getLong(ModelDownloadWorker.PROGRESS_DOWNLOADED, 0), total)
    }

    private fun failedState(output: Data): DownloadState = when (output.getString(ModelDownloadWorker.OUTPUT_REASON)) {
        ModelDownloadWorker.REASON_DAMAGED -> DownloadState.Damaged
        ModelDownloadWorker.REASON_NO_SPACE -> DownloadState.NoSpace(output.getLong(ModelDownloadWorker.OUTPUT_NEEDED_BYTES, 0))
        else -> DownloadState.Failed
    }

    private fun uniqueName(fileName: String): String = "model-download:$fileName"

    companion object {
        private const val TAG = "model-download"
        private const val FILE_TAG_PREFIX = "model-file:"
        private const val RETRY_DELAY_SECONDS = 30L

        /** Free storage left over after a download (research doc: the file plus 1 GB). */
        const val STORAGE_MARGIN_BYTES = 1_000_000_000L
    }
}
