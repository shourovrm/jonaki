package app.jonaki.core.localmodels

import app.jonaki.core.toolapi.await
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** One model file to fetch: where from, where to, and what it must hash to. */
data class DownloadRequest(
    /** The huggingface.co resolve address; asked again on every attempt (see [HuggingFaceClient.downloadUrl]). */
    val url: String,
    val expectedBytes: Long,
    /** Lower-case hex. */
    val expectedSha256: String,
    /** "<file>.part", kept between attempts so a later attempt resumes. */
    val partFile: File,
    /** Where the finished file goes; the local-model provider lists this folder. */
    val targetFile: File,
)

sealed interface DownloadOutcome {
    data object Finished : DownloadOutcome

    /** The bytes did not hash to the expected SHA-256; the partial file was deleted. */
    data object HashMismatch : DownloadOutcome

    /** The server answered with an error the next attempt would get as well (for example 404). */
    data class Refused(val httpCode: Int) : DownloadOutcome
}

/**
 * Downloads one file with resume and a SHA-256 check (D-133). The bytes
 * already in the part file are hashed first, then the rest is requested
 * with a Range header and hashed as it is written, so the whole file is
 * never read twice. Only a matching file is renamed to its target; a
 * mismatch deletes the part file. Network errors are thrown as
 * [IOException] and leave the part file for the next attempt; a cancelled
 * coroutine stops between two chunks.
 */
class ModelDownloader(private val httpClient: OkHttpClient) {
    suspend fun download(request: DownloadRequest, onProgress: (downloadedBytes: Long) -> Unit): DownloadOutcome =
        withContext(Dispatchers.IO) { fetchAndCheck(request, onProgress) }

    private suspend fun fetchAndCheck(request: DownloadRequest, onProgress: (Long) -> Unit): DownloadOutcome {
        request.partFile.parentFile?.mkdirs()
        request.targetFile.parentFile?.mkdirs()
        if (request.partFile.length() > request.expectedBytes) {
            // Longer than the real file: not from this file, so it cannot be resumed.
            request.partFile.delete()
        }
        val digest = MessageDigest.getInstance("SHA-256")
        var downloaded = hashExisting(request.partFile, digest)
        if (downloaded < request.expectedBytes) {
            val httpRequest = Request.Builder()
                .url(request.url)
                .apply { if (downloaded > 0) header("Range", "bytes=$downloaded-") }
                .build()
            httpClient.newCall(httpRequest).await().use { response ->
                when {
                    response.code == HTTP_PARTIAL_CONTENT -> Unit
                    response.code == HTTP_OK -> {
                        // The server ignored the range and sends the whole file from the first byte.
                        digest.reset()
                        downloaded = 0
                    }
                    response.code == HTTP_RANGE_NOT_SATISFIABLE -> {
                        request.partFile.delete()
                        throw IOException("the server refused to resume; starting again on the next attempt")
                    }
                    response.code in REFUSALS_THAT_REPEAT -> return DownloadOutcome.Refused(response.code)
                    else -> throw IOException("server error ${response.code}")
                }
                val body = response.body ?: throw IOException("the server sent nothing")
                downloaded = append(body.byteStream(), request.partFile, append = downloaded > 0, digest, downloaded, onProgress)
            }
        }
        if (downloaded != request.expectedBytes) {
            throw IOException("got $downloaded of ${request.expectedBytes} bytes")
        }
        return finish(request, digest)
    }

    private suspend fun hashExisting(partFile: File, digest: MessageDigest): Long {
        if (!partFile.exists()) {
            return 0
        }
        var total = 0L
        partFile.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
                total += count
            }
        }
        return total
    }

    private suspend fun append(
        input: InputStream,
        partFile: File,
        append: Boolean,
        digest: MessageDigest,
        startBytes: Long,
        onProgress: (Long) -> Unit,
    ): Long {
        var total = startBytes
        FileOutputStream(partFile, append).use { output ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                digest.update(buffer, 0, count)
                total += count
                onProgress(total)
            }
        }
        return total
    }

    private fun finish(request: DownloadRequest, digest: MessageDigest): DownloadOutcome {
        val actual = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        if (actual != request.expectedSha256.lowercase()) {
            request.partFile.delete()
            return DownloadOutcome.HashMismatch
        }
        request.targetFile.delete()
        if (!request.partFile.renameTo(request.targetFile)) {
            throw IOException("could not move the finished file into place")
        }
        return DownloadOutcome.Finished
    }

    private companion object {
        const val BUFFER_BYTES = 256 * 1024
        const val HTTP_OK = 200
        const val HTTP_PARTIAL_CONTENT = 206
        const val HTTP_RANGE_NOT_SATISFIABLE = 416

        /** Gone, not found or not allowed: retrying the same address cannot help. */
        val REFUSALS_THAT_REPEAT = setOf(401, 403, 404, 410)
    }
}
