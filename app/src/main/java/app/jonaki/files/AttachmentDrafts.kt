package app.jonaki.files

import app.jonaki.core.agent.AttachmentLine
import app.jonaki.core.toolapi.IncomingFiles
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

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
 *
 * Android may stop the app while chips wait, so the list is saved in
 * [MANIFEST_NAME] inside the staging folder after every change and read
 * back by [restore] at start.
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
        saveManifest()
    }

    fun remove(threadKey: String, stagedId: String) {
        val removed = waiting.value[threadKey].orEmpty().firstOrNull { it.id == stagedId } ?: return
        waiting.update { current -> withoutFile(current, threadKey, stagedId) }
        saveManifest()
        removed.file.parentFile?.deleteRecursively()
    }

    /** Deletes every file waiting for [threadKey]. Call off the main thread. */
    fun discardAll(threadKey: String) {
        val files = waiting.value[threadKey].orEmpty()
        waiting.update { current -> current - threadKey }
        saveManifest()
        for (staged in files) {
            staged.file.parentFile?.deleteRecursively()
        }
    }

    /**
     * Moves the thread's waiting files into [threadFolder]/inbox/ and returns
     * their new paths ("inbox/sales.csv"). A name already in the inbox gets
     * " (2)", so no earlier file is replaced. Call off the main thread.
     */
    fun moveIntoInbox(threadKey: String, threadFolder: File): List<String> {
        val files = waiting.value[threadKey].orEmpty()
        waiting.update { current -> current - threadKey }
        saveManifest()
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

    /**
     * Brings back the chips saved before the process ended. A chip whose
     * staged copy is gone is dropped. Returns the staged folders no chip
     * names (a share whose thread was never picked, a copy cut off midway);
     * the caller deletes them off the main thread. Call once at start,
     * before anything is staged, so a fresh copy is never taken for a
     * leftover.
     */
    fun restore(): List<File> {
        val saved = readManifest()
        val restored = mutableMapOf<String, List<StagedFile>>()
        for ((threadKey, files) in saved) {
            val present = files.filter { staged -> staged.file.isFile }
            if (present.isNotEmpty()) {
                restored[threadKey] = present
            }
        }
        waiting.value = restored
        saveManifest()
        val namedFolders = restored.values.flatten().map { staged -> staged.file.parentFile }.toSet()
        val folders = stagingRoot.listFiles { file -> file.isDirectory }.orEmpty()
        return folders.filter { folder -> folder !in namedFolders }.sortedBy { folder -> folder.name }
    }

    /** Writes the waiting chips through a temporary file, so a crash mid-write keeps the previous list. */
    @Synchronized
    private fun saveManifest() {
        stagingRoot.mkdirs()
        val manifest = buildJsonObject {
            for ((threadKey, files) in waiting.value) {
                put(
                    threadKey,
                    buildJsonArray {
                        for (staged in files) {
                            add(buildJsonObject {
                                put(ID_KEY, JsonPrimitive(staged.id))
                                put(NAME_KEY, JsonPrimitive(staged.name))
                            })
                        }
                    },
                )
            }
        }
        val partial = File(stagingRoot, "$MANIFEST_NAME.partial")
        partial.writeText(manifest.toString())
        if (!partial.renameTo(File(stagingRoot, MANIFEST_NAME))) {
            partial.delete()
        }
    }

    private fun readManifest(): Map<String, List<StagedFile>> {
        val file = File(stagingRoot, MANIFEST_NAME)
        if (!file.isFile) {
            return emptyMap()
        }
        val root = runCatching { Json.parseToJsonElement(file.readText()) as? JsonObject }.getOrNull() ?: return emptyMap()
        val result = mutableMapOf<String, List<StagedFile>>()
        for ((threadKey, entries) in root) {
            val files = (entries as? JsonArray).orEmpty().mapNotNull(::stagedFileOf)
            result[threadKey] = files
        }
        return result
    }

    private fun stagedFileOf(entry: JsonElement): StagedFile? {
        val fields = entry as? JsonObject ?: return null
        val id = (fields[ID_KEY] as? JsonPrimitive)?.contentOrNull ?: return null
        val name = (fields[NAME_KEY] as? JsonPrimitive)?.contentOrNull ?: return null
        // A saved id or name that tries to leave the staging folder is not trusted.
        if (id != File(id).name || name != IncomingFiles.safeName(name)) {
            return null
        }
        return StagedFile(id, name, File(File(stagingRoot, id), name))
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
        private const val MANIFEST_NAME = "waiting.json"
        private const val ID_KEY = "id"
        private const val NAME_KEY = "name"

        /** The sent message names the files' paths, so the model knows where they are. */
        fun messageWith(text: String, inboxPaths: List<String>): String = AttachmentLine.appendTo(text, inboxPaths)
    }
}
