package app.jonaki.files

import app.jonaki.core.toolapi.IncomingFiles
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A copy of a picked or shared file, waiting in the app's cache for the next message. */
data class StagedFile(
    val id: String,
    /** The name it gets in inbox/, unless that name is taken. */
    val name: String,
    val file: File,
)

/**
 * Files attached to a thread's next message, shown as chips (D-042). A
 * picked or shared file is copied into the cache at once, because the
 * permission to read it can end with the activity; it moves into the
 * thread's inbox/ when the message is sent. A new thread that has no
 * folder yet keeps its files under the key the chat screen uses for it.
 */
class AttachmentDrafts(private val stagingRoot: File) {
    private val waiting = MutableStateFlow<Map<String, List<StagedFile>>>(emptyMap())

    /** Thread key to its waiting files, in the order they were added. */
    val byThread: StateFlow<Map<String, List<StagedFile>>> = waiting.asStateFlow()

    /**
     * Makes a staged file named [displayName]; [copy] writes its content.
     * Each file gets its own folder, so two files with one name can wait
     * together. Call off the main thread.
     */
    fun stage(displayName: String?, copy: (target: File) -> Unit): StagedFile {
        val id = UUID.randomUUID().toString()
        val name = IncomingFiles.safeName(displayName)
        val folder = File(stagingRoot, id)
        folder.mkdirs()
        val target = File(folder, name)
        try {
            copy(target)
        } catch (failure: IOException) {
            folder.deleteRecursively()
            throw failure
        } catch (failure: SecurityException) {
            folder.deleteRecursively()
            throw failure
        }
        return StagedFile(id, name, target)
    }

    fun add(threadKey: String, files: List<StagedFile>) {
        if (files.isEmpty()) {
            return
        }
        waiting.update { current -> current + (threadKey to current[threadKey].orEmpty() + files) }
    }

    fun remove(threadKey: String, stagedId: String) {
        val removed = waiting.value[threadKey].orEmpty().firstOrNull { it.id == stagedId } ?: return
        waiting.update { current -> withoutFile(current, threadKey, stagedId) }
        removed.file.parentFile?.deleteRecursively()
    }

    /**
     * Moves the thread's waiting files into [threadFolder]/inbox/ and returns
     * their new paths ("inbox/sales.csv"). A name already in the inbox gets
     * " (2)", so no earlier file is replaced. Call off the main thread.
     */
    fun moveIntoInbox(threadKey: String, threadFolder: File): List<String> {
        val files = waiting.value[threadKey].orEmpty()
        waiting.update { current -> current - threadKey }
        val inbox = File(threadFolder, INBOX)
        inbox.mkdirs()
        val paths = mutableListOf<String>()
        for (staged in files) {
            val target = IncomingFiles.freeFileIn(inbox, staged.name)
            moveFile(staged.file, target)
            staged.file.parentFile?.deleteRecursively()
            paths += "$INBOX/${target.name}"
        }
        return paths
    }

    /** Waiting files do not survive the process, so their staged copies are left over. */
    fun deleteLeftovers() {
        stagingRoot.deleteRecursively()
    }

    /** Within the app's own storage a rename works; across file systems it falls back to a copy. */
    private fun moveFile(source: File, target: File) {
        if (source.renameTo(target)) {
            return
        }
        source.copyTo(target)
        source.delete()
    }

    private fun withoutFile(
        current: Map<String, List<StagedFile>>,
        threadKey: String,
        stagedId: String,
    ): Map<String, List<StagedFile>> {
        val remaining = current[threadKey].orEmpty().filter { it.id != stagedId }
        return if (remaining.isEmpty()) current - threadKey else current + (threadKey to remaining)
    }

    companion object {
        private const val INBOX = "inbox"

        /** The sent message names the files' paths, so the model knows where they are. */
        fun messageWith(text: String, inboxPaths: List<String>): String {
            if (inboxPaths.isEmpty()) {
                return text
            }
            val line = "Attached: " + inboxPaths.joinToString(", ")
            if (text.isBlank()) {
                return line
            }
            return text.trimEnd() + "\n\n" + line
        }
    }
}
