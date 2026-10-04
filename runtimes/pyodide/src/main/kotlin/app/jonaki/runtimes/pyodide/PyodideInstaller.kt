package app.jonaki.runtimes.pyodide

import app.jonaki.core.toolapi.await
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.coroutineContext

/**
 * Downloads Python and its packages over HTTPS and keeps only files whose
 * SHA-256 matches: the pinned hash for core files, the lock file's hash for
 * packages. This is the only code that downloads anything for Python;
 * programs themselves have no network (D-069). The settings screen and the
 * just-in-time install card (plan M8 steps 3 and 4) call it.
 */
class PyodideInstaller(
    private val folder: PyodideFolder,
    private val httpClient: OkHttpClient,
) {
    suspend fun installCore(onProgress: (DownloadProgress) -> Unit = {}): InstallResult {
        val release = folder.release
        var doneBytes = 0L
        for (pinned in release.coreFiles) {
            val target = folder.coreFile(pinned.name)
            if (target.isFile && Checksums.sha256Of(target) == pinned.sha256) {
                doneBytes += pinned.sizeBytes
                continue
            }
            val problem = download(release.baseUrl + pinned.name, target, pinned.sha256) { fileBytes ->
                onProgress(DownloadProgress(doneBytes + fileBytes, release.coreDownloadBytes))
            }
            if (problem != null) {
                return InstallResult.Failed("Could not install Python: ${pinned.name} $problem")
            }
            doneBytes += pinned.sizeBytes
        }
        return InstallResult.Installed
    }

    /** Installs packages from the release's lock file, with their dependencies. */
    suspend fun installPackages(names: List<String>, onProgress: (DownloadProgress) -> Unit = {}): InstallResult {
        val lock = withContext(Dispatchers.IO) { if (folder.isCoreInstalled()) folder.lock() else null }
            ?: return InstallResult.Failed("Python is not installed; install it before its packages")
        val unknown = names.filter { name -> lock.find(name) == null }
        if (unknown.isNotEmpty()) {
            return InstallResult.Failed(
                "${unknown.joinToString(", ")} is not available for Pyodide ${folder.release.version}",
            )
        }
        val missing = lock.withDependencies(names).filter { lockPackage -> !folder.packageFile(lockPackage).isFile }
        val refused = missing.firstOrNull { lockPackage -> !isSafeFileName(lockPackage.fileName) }
        if (refused != null) {
            return InstallResult.Failed("${refused.name} is refused: ${refused.fileName} is not a package archive")
        }
        var doneBytes = 0L
        for (lockPackage in missing) {
            val url = folder.release.baseUrl + lockPackage.fileName
            val problem = download(url, folder.packageFile(lockPackage), lockPackage.sha256) { fileBytes ->
                onProgress(DownloadProgress(doneBytes + fileBytes, totalBytes = null))
            }
            if (problem != null) {
                return InstallResult.Failed("Could not install ${lockPackage.name}: ${lockPackage.fileName} $problem")
            }
            doneBytes += folder.packageFile(lockPackage).length()
        }
        return InstallResult.Installed
    }

    /**
     * Installs the release's pinned wheels (the documents add-on's PyPI
     * part): each is fetched from its pinned URL, hashed while it streams
     * and kept only on a match. A wheel already present with the right hash
     * is skipped, a damaged one is fetched again, so this is also Repair.
     */
    suspend fun installWheels(wheels: List<PinnedWheel>, onProgress: (DownloadProgress) -> Unit = {}): InstallResult {
        val refused = wheels.firstOrNull { wheel -> !isSafeFileName(wheel.fileName) }
        if (refused != null) {
            return InstallResult.Failed("${refused.packageName} is refused: ${refused.fileName} is not a package archive")
        }
        var doneBytes = 0L
        for (wheel in wheels) {
            val target = folder.wheelFile(wheel)
            val isIntact = withContext(Dispatchers.IO) { target.isFile && Checksums.sha256Of(target) == wheel.sha256 }
            if (!isIntact) {
                val problem = download(wheel.url, target, wheel.sha256) { fileBytes ->
                    onProgress(DownloadProgress(doneBytes + fileBytes, totalBytes = null))
                }
                if (problem != null) {
                    return InstallResult.Failed("Could not install ${wheel.packageName}: ${wheel.fileName} $problem")
                }
            }
            doneBytes += wheel.sizeBytes
        }
        return InstallResult.Installed
    }

    /**
     * A plain file name of a wheel or zip archive. Native libraries (.so) and
     * Android code (.dex) are never downloaded (planning chat, policy
     * conditions); wheels may hold WebAssembly modules, which run only inside
     * the WebView.
     */
    private fun isSafeFileName(fileName: String): Boolean {
        val isPlainName = !fileName.contains('/') && !fileName.contains('\\') && !fileName.startsWith(".")
        val lowerName = fileName.lowercase()
        return isPlainName && SAFE_EXTENSIONS.any { extension -> lowerName.endsWith(extension) }
    }

    /** Returns null when the file arrived with the expected hash, else what went wrong. */
    private suspend fun download(
        url: String,
        target: File,
        expectedSha256: String,
        onFileBytes: (Long) -> Unit,
    ): String? {
        val partFile = File(target.parentFile, target.name + ".part")
        try {
            val response = httpClient.newCall(Request.Builder().url(url).build()).await()
            response.use {
                if (!response.isSuccessful) {
                    return "could not be downloaded (HTTP ${response.code})"
                }
                val actualSha256 = withContext(Dispatchers.IO) {
                    target.parentFile?.mkdirs()
                    copyHashing(response.body!!.byteStream(), partFile, onFileBytes)
                }
                if (actualSha256 != expectedSha256) {
                    return "has the wrong checksum"
                }
            }
            if (!partFile.renameTo(target)) {
                return "could not be saved"
            }
            return null
        } catch (exception: IOException) {
            return "could not be downloaded (${exception.message ?: exception.javaClass.simpleName})"
        } finally {
            partFile.delete()
        }
    }

    private suspend fun copyHashing(input: java.io.InputStream, partFile: File, onFileBytes: (Long) -> Unit): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var copiedBytes = 0L
        partFile.outputStream().use { output ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                // Lets the user's Cancel stop a large download between chunks.
                coroutineContext.ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
                output.write(buffer, 0, count)
                copiedBytes += count
                onFileBytes(copiedBytes)
            }
        }
        return Checksums.hex(digest.digest())
    }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
        val SAFE_EXTENSIONS = listOf(".whl", ".zip", ".tar", ".tar.gz", ".tgz")
    }
}

sealed interface InstallResult {
    data object Installed : InstallResult

    data class Failed(val message: String) : InstallResult
}

/** [totalBytes] is null for packages, whose sizes the lock file does not give. */
data class DownloadProgress(
    val doneBytes: Long,
    val totalBytes: Long?,
)
