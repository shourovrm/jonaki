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
 * The image models each thread uses when the user picked them in the thread's
 * model sheet: one raster model for generate_image and one vector model for
 * generate_vector_image, so picking one kind does not undo the other. A
 * thread without an entry follows the starred default from Settings (raster)
 * or the first vector model. Keys are thread ids; the vector pick is kept
 * under [vectorKeyOf] of the thread id. A new thread's pick is held by
 * the screen until the first message creates the row, and only then enters
 * here. The entries live in one JSON file of their own, so the database is
 * untouched.
 *
 * Writing blocks on the disk, so call [choose], [clear] and [pruneTo] off the
 * main thread.
 */
class ThreadImageChoices(private val file: File) {
    private val choices = MutableStateFlow(readFile())

    /** Entries (thread keys and [vectorKeyOf] keys) that stay in memory only (incognito); these are never written. */
    private val memoryOnlyThreads = mutableSetOf<String>()

    /** Entry key to its chosen image model key, "service:modelId"; only threads that made a choice. */
    val byThread: StateFlow<Map<String, String>> = choices.asStateFlow()

    fun choiceFor(threadKey: String): String? = choices.value[threadKey]

    fun vectorChoiceFor(threadKey: String): String? = choices.value[vectorKeyOf(threadKey)]

    /**
     * The model generate_vector_image uses by default in this thread: the
     * thread's vector pick while that model is still among [vectorModelKeys],
     * otherwise the first of them. The star in Settings belongs to
     * generate_image only. Null when no vector model is usable.
     */
    fun effectiveVectorModelKey(threadKey: String, vectorModelKeys: List<String>): String? =
        resolve(vectorChoiceFor(threadKey), vectorModelKeys, starredDefault = null)

    /**
     * The model generate_image uses by default in this thread: the thread's
     * choice while that model is still among [addedModelKeys], otherwise the
     * starred default, otherwise the first added model (as the tool itself
     * falls back). Null when no image model is added.
     */
    fun effectiveModelKey(threadKey: String, addedModelKeys: List<String>, starredDefault: String?): String? =
        resolve(choiceFor(threadKey), addedModelKeys, starredDefault)

    /**
     * Sets the thread's model of one kind: the vector pick when [isVector],
     * else the raster pick. Choosing the default of that kind removes the
     * entry instead, so a later change of the default is followed again;
     * [starredDefault] is the starred raster model, or for a vector pick the
     * first vector model. With [keepOnDisk] false the entry lives in memory only.
     */
    @Synchronized
    fun choose(threadKey: String, modelKey: String, starredDefault: String?, keepOnDisk: Boolean = true, isVector: Boolean = false) {
        val entryKey = if (isVector) vectorKeyOf(threadKey) else threadKey
        if (modelKey == starredDefault) {
            clearEntry(entryKey)
            return
        }
        if (keepOnDisk) {
            memoryOnlyThreads -= entryKey
        } else {
            memoryOnlyThreads += entryKey
        }
        choices.value = choices.value + (entryKey to modelKey)
        writeFile()
    }

    /** Removes both picks of the thread. */
    @Synchronized
    fun clear(threadKey: String) {
        clearEntry(threadKey)
        clearEntry(vectorKeyOf(threadKey))
    }

    private fun clearEntry(entryKey: String) {
        memoryOnlyThreads -= entryKey
        if (entryKey !in choices.value) {
            return
        }
        choices.value = choices.value - entryKey
        writeFile()
    }

    /** Drops the choices of threads that are gone. */
    @Synchronized
    fun pruneTo(existingThreadIds: Set<String>) {
        val kept = choices.value.filterKeys { key -> key.removeSuffix(VECTOR_SUFFIX) in existingThreadIds }
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
        private const val VECTOR_SUFFIX = "#vector"

        /** The entry key of a thread's vector pick in [byThread]. */
        fun vectorKeyOf(threadKey: String): String = threadKey + VECTOR_SUFFIX

        /** [effectiveModelKey] for a choice the caller already holds, such as a new thread's pick not yet stored. */
        fun resolve(choice: String?, addedModelKeys: List<String>, starredDefault: String?): String? {
            if (choice != null && choice in addedModelKeys) {
                return choice
            }
            return starredDefault?.takeIf { key -> key in addedModelKeys } ?: addedModelKeys.firstOrNull()
        }
    }
}
