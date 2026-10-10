package app.jonaki.core.appupdate

import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Downloads one file to disk, replacing what was there. */
class ApkDownloader(private val httpClient: OkHttpClient) {
    /**
     * Returns null on success or the [UpdateFailure]. [onProgress] gets 0..100 and is
     * called only when the server says how long the file is. The bytes go to a
     * ".part" file that replaces [target] only when complete, so a failed download
     * leaves no file and the installer is never handed half an APK.
     */
    suspend fun download(url: String, target: File, onProgress: (percent: Int) -> Unit): UpdateFailure? =
        withContext(Dispatchers.IO) {
            target.parentFile?.mkdirs()
            val partialFile = File(target.path + ".part")
            try {
                val failure = copyToFile(url, partialFile, onProgress)
                if (failure != null) {
                    return@withContext failure
                }
                target.delete()
                if (!partialFile.renameTo(target)) {
                    return@withContext UpdateFailure.DownloadBroken
                }
                null
            } finally {
                partialFile.delete()
            }
        }

    private suspend fun copyToFile(url: String, partialFile: File, onProgress: (Int) -> Unit): UpdateFailure? {
        val response = try {
            httpClient.newCall(Request.Builder().url(url).build()).execute()
        } catch (connectionError: IOException) {
            return UpdateFailure.NoNetwork
        }
        response.use {
            val body = response.body
            if (!response.isSuccessful || body == null) {
                return UpdateFailure.HttpStatus(response.code)
            }
            val expectedBytes = body.contentLength()
            try {
                var copiedBytes = 0L
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                body.byteStream().use { input ->
                    partialFile.outputStream().use { output ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val readCount = input.read(buffer)
                            if (readCount < 0) {
                                break
                            }
                            output.write(buffer, 0, readCount)
                            copiedBytes += readCount
                            if (expectedBytes > 0) {
                                onProgress((copiedBytes * 100 / expectedBytes).toInt())
                            }
                        }
                    }
                }
                if (expectedBytes >= 0 && copiedBytes != expectedBytes) {
                    return UpdateFailure.DownloadBroken
                }
                return null
            } catch (readError: IOException) {
                return UpdateFailure.DownloadBroken
            }
        }
    }

    private companion object {
        const val COPY_BUFFER_BYTES = 64 * 1024
    }
}
