package app.jonaki.files

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.IntentCompat
import app.jonaki.core.toolapi.FileTooLargeException
import app.jonaki.core.toolapi.IncomingFiles
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A file that could not be added, and why. */
sealed interface RefusedFile {
    val name: String

    data class TooLarge(override val name: String, val limitBytes: Long) : RefusedFile

    data class Unreadable(override val name: String) : RefusedFile
}

/** What another app shared, copied into the cache, waiting for the user to pick a thread. */
data class PendingShare(
    val text: String?,
    val files: List<StagedFile>,
)

/**
 * Takes files and text from Android's share sheet and the attach button
 * (D-042). Files are copied into the cache at once with the 25 MB limit;
 * a share then waits in [pending] until the user picks a thread, and its
 * text waits in [textFor] until that thread's chat puts it in the field.
 */
class IncomingShares(
    private val contentResolver: ContentResolver,
    private val drafts: AttachmentDrafts,
    private val scope: CoroutineScope,
) {
    private val pendingShare = MutableStateFlow<PendingShare?>(null)
    val pending: StateFlow<PendingShare?> = pendingShare.asStateFlow()

    private val waitingText = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Shared text per thread key, for the chat field. */
    val textFor: StateFlow<Map<String, String>> = waitingText.asStateFlow()

    private val refused = MutableStateFlow<List<RefusedFile>>(emptyList())

    /** Files that were not added, for a short message; [clearRefused] after showing it. */
    val refusedFiles: StateFlow<List<RefusedFile>> = refused.asStateFlow()

    /** Reads a share intent; returns false when the intent is not a share. */
    fun receive(intent: Intent): Boolean {
        val action = intent.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) {
            return false
        }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
        val uris = sharedUris(intent)
        if (text == null && uris.isEmpty()) {
            return false
        }
        scope.launch(Dispatchers.IO) {
            val staged = stageAll(uris)
            // A share with only refused files leaves nothing to place.
            if (text != null || staged.isNotEmpty()) {
                discard()
                pendingShare.value = PendingShare(text, staged)
            }
        }
        return true
    }

    /** Files from the attach button go straight to the thread the chat shows. */
    fun attach(threadKey: String, uris: List<Uri>) {
        scope.launch(Dispatchers.IO) {
            drafts.add(threadKey, stageAll(uris))
        }
    }

    /** The user picked a thread for the pending share. */
    fun deliverTo(threadKey: String) {
        val share = pendingShare.value ?: return
        pendingShare.value = null
        drafts.add(threadKey, share.files)
        val text = share.text ?: return
        waitingText.update { current ->
            val earlier = current[threadKey]
            val joined = if (earlier == null) text else "$earlier\n\n$text"
            current + (threadKey to joined)
        }
    }

    /** The chat put the shared text into its field. */
    fun takeText(threadKey: String): String? {
        val text = waitingText.value[threadKey] ?: return null
        waitingText.update { current -> current - threadKey }
        return text
    }

    /** The user closed the thread picker; the staged copies are deleted. */
    fun discard() {
        val share = pendingShare.value ?: return
        pendingShare.value = null
        for (file in share.files) {
            file.file.parentFile?.deleteRecursively()
        }
    }

    fun clearRefused() {
        refused.value = emptyList()
    }

    private fun sharedUris(intent: Intent): List<Uri> {
        val uris = mutableListOf<Uri>()
        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            uris += IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uris += it }
        }
        if (uris.isEmpty()) {
            // Some apps put the files only in the clip data.
            val clip = intent.clipData
            if (clip != null) {
                for (index in 0 until clip.itemCount) {
                    clip.getItemAt(index).uri?.let { uris += it }
                }
            }
        }
        return uris
    }

    private fun stageAll(uris: List<Uri>): List<StagedFile> {
        val staged = mutableListOf<StagedFile>()
        val refusedNow = mutableListOf<RefusedFile>()
        for (uri in uris) {
            val name = displayNameOf(uri)
            if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
                // A file:// link could name Jonaki's own private files (its keys among them); apps share content:// links.
                refusedNow += RefusedFile.Unreadable(IncomingFiles.safeName(name))
                continue
            }
            try {
                staged += drafts.stage(name) { target -> copy(uri, target) }
            } catch (tooLarge: FileTooLargeException) {
                refusedNow += RefusedFile.TooLarge(IncomingFiles.safeName(name), tooLarge.limitBytes)
            } catch (failure: IOException) {
                refusedNow += RefusedFile.Unreadable(IncomingFiles.safeName(name))
            } catch (failure: SecurityException) {
                refusedNow += RefusedFile.Unreadable(IncomingFiles.safeName(name))
            }
        }
        if (refusedNow.isNotEmpty()) {
            refused.update { current -> current + refusedNow }
        }
        return staged
    }

    private fun copy(uri: Uri, target: java.io.File) {
        val input = contentResolver.openInputStream(uri) ?: throw IOException("could not open $uri")
        input.use { stream -> IncomingFiles.copyWithLimit(stream, target) }
    }

    private fun displayNameOf(uri: Uri): String? {
        val fromProvider = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        return fromProvider ?: uri.lastPathSegment
    }
}
