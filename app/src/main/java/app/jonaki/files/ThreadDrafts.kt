package app.jonaki.files

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

/**
 * The text typed in each thread's message box and not yet sent. The keys are
 * the ones [AttachmentDrafts] uses: the thread's id, and [NEW_THREAD_KEY] for
 * the new-thread screen, which has no row yet. All drafts live in one JSON
 * file. A blank draft is no draft, and a draft is cut at [MAX_LENGTH].
 *
 * The map is updated at once; the file is rewritten from the whole map each
 * time, so writes from different threads cannot leave an older text on disk.
 * Writing blocks on the disk, so call [set], [clear] and [pruneTo] off the
 * main thread.
 */
class ThreadDrafts(private val file: File) {
    private val drafts = MutableStateFlow(readFile())

    /** Thread key to its draft; only keys that have a non-blank draft. */
    val byThread: StateFlow<Map<String, String>> = drafts.asStateFlow()

    fun textFor(threadKey: String): String = drafts.value[threadKey].orEmpty()

    @Synchronized
    fun set(threadKey: String, text: String) {
        if (text.isBlank()) {
            clear(threadKey)
            return
        }
        drafts.value = drafts.value + (threadKey to text.take(MAX_LENGTH))
        writeFile()
    }

    @Synchronized
    fun clear(threadKey: String) {
        if (threadKey !in drafts.value) {
            return
        }
        drafts.value = drafts.value - threadKey
        writeFile()
    }

    /** Drops the drafts of threads that are gone; the new-thread draft stays. */
    @Synchronized
    fun pruneTo(existingThreadIds: Set<String>) {
        val kept = drafts.value.filterKeys { key -> key == NEW_THREAD_KEY || key in existingThreadIds }
        if (kept.size == drafts.value.size) {
            return
        }
        drafts.value = kept
        writeFile()
    }

    /** Writes through a temporary file, so a crash mid-write keeps the previous drafts. */
    private fun writeFile() {
        file.parentFile?.mkdirs()
        val json = buildJsonObject {
            for ((threadKey, text) in drafts.value) {
                put(threadKey, JsonPrimitive(text))
            }
        }
        val partial = File(file.parentFile, "${file.name}.partial")
        partial.writeText(json.toString())
        if (!partial.renameTo(file)) {
            partial.delete()
        }
    }

    private fun readFile(): Map<String, String> {
        if (!file.isFile) {
            return emptyMap()
        }
        val root = runCatching { Json.parseToJsonElement(file.readText()) as? JsonObject }.getOrNull() ?: return emptyMap()
        val result = mutableMapOf<String, String>()
        for ((threadKey, value) in root) {
            val primitive = value as? JsonPrimitive
            val text = if (primitive != null && primitive.isString) primitive.contentOrNull else null
            if (!text.isNullOrBlank()) {
                result[threadKey] = text.take(MAX_LENGTH)
            }
        }
        return result
    }

    companion object {
        /** The key AttachmentDrafts and the chat screen use for a thread that has no row yet. */
        const val NEW_THREAD_KEY = "new"

        const val MAX_LENGTH = 20_000

        /** The first line of the draft that has text, for the thread list. */
        fun previewLine(text: String): String =
            text.lineSequence().map { line -> line.trim() }.firstOrNull { line -> line.isNotEmpty() }.orEmpty()
    }
}
