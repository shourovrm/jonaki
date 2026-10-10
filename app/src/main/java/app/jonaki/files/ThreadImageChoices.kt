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
 * The image model each thread uses for generate_image when the user picked one
 * in the thread's model sheet. A thread without an entry follows the starred
 * default from Settings. Keys are thread ids. A new thread's pick is held by
 * the screen until the first message creates the row, and only then enters
 * here. The entries live in one JSON file of their own, so the database is
 * untouched.
 *
 * Writing blocks on the disk, so call [choose], [clear] and [pruneTo] off the
 * main thread.
 */
class ThreadImageChoices(private val file: File) {
    private val choices = MutableStateFlow(readFile())

    /** Threads whose choice stays in memory only (incognito); these are never written. */
    private val memoryOnlyThreads = mutableSetOf<String>()

    /** Thread key to its chosen image model key, "service:modelId"; only threads that made a choice. */
    val byThread: StateFlow<Map<String, String>> = choices.asStateFlow()

    fun choiceFor(threadKey: String): String? = choices.value[threadKey]

    /**
     * The model generate_image uses by default in this thread: the thread's
     * choice while that model is still among [addedModelKeys], otherwise the
     * starred default, otherwise the first added model (as the tool itself
     * falls back). Null when no image model is added.
     */
    fun effectiveModelKey(threadKey: String, addedModelKeys: List<String>, starredDefault: String?): String? =
        resolve(choiceFor(threadKey), addedModelKeys, starredDefault)

    /**
     * Sets the thread's model. Choosing the starred default removes the entry
     * instead, so a later change of the default in Settings is followed again.
     * With [keepOnDisk] false the entry lives in memory only.
     */
    @Synchronized
    fun choose(threadKey: String, modelKey: String, starredDefault: String?, keepOnDisk: Boolean = true) {
        if (modelKey == starredDefault) {
            clear(threadKey)
            return
        }
        if (keepOnDisk) {
            memoryOnlyThreads -= threadKey
        } else {
            memoryOnlyThreads += threadKey
        }
        choices.value = choices.value + (threadKey to modelKey)
        writeFile()
    }

    @Synchronized
    fun clear(threadKey: String) {
        memoryOnlyThreads -= threadKey
        if (threadKey !in choices.value) {
            return
        }
        choices.value = choices.value - threadKey
        writeFile()
    }

    /** Drops the choices of threads that are gone. */
    @Synchronized
    fun pruneTo(existingThreadIds: Set<String>) {
        val kept = choices.value.filterKeys { key -> key in existingThreadIds }
        if (kept.size == choices.value.size) {
            return
        }
        memoryOnlyThreads.retainAll(kept.keys)
        choices.value = kept
        writeFile()
    }

    /** Writes through a temporary file, so a crash mid-write keeps the previous choices. */
    private fun writeFile() {
        file.parentFile?.mkdirs()
        val json = buildJsonObject {
            for ((threadKey, modelKey) in choices.value) {
                if (threadKey !in memoryOnlyThreads) {
                    put(threadKey, JsonPrimitive(modelKey))
                }
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
            val modelKey = if (primitive != null && primitive.isString) primitive.contentOrNull else null
            if (!modelKey.isNullOrBlank()) {
                result[threadKey] = modelKey
            }
        }
        return result
    }

    companion object {
        /** [effectiveModelKey] for a choice the caller already holds, such as a new thread's pick not yet stored. */
        fun resolve(choice: String?, addedModelKeys: List<String>, starredDefault: String?): String? {
            if (choice != null && choice in addedModelKeys) {
                return choice
            }
            return starredDefault?.takeIf { key -> key in addedModelKeys } ?: addedModelKeys.firstOrNull()
        }
    }
}
