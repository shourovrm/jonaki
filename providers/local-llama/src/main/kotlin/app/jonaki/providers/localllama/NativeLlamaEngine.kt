package app.jonaki.providers.localllama

import java.io.File

/** Called by the JNI layer with the parsed reply so far, as UTF-8 bytes. */
fun interface SnapshotListener {
    fun onSnapshot(content: ByteArray, reasoning: ByteArray)
}

/**
 * llama.cpp through libjonaki_llama.so. Strings cross as UTF-8 bytes because
 * JNI's own string functions use modified UTF-8, which breaks emoji.
 */
class NativeLlamaEngine : LlamaEngine {
    private var handle = 0L

    override fun load(modelFile: File, contextTokens: Int, threads: Int) {
        unload()
        loadLibrary()
        handle = nativeLoad(modelFile.path.toByteArray(Charsets.UTF_8), contextTokens, threads)
    }

    override fun unload() {
        if (handle == 0L) {
            return
        }
        nativeUnload(handle)
        handle = 0L
    }

    override fun generate(requestJson: String, onSnapshot: (content: String, reasoning: String) -> Unit): String {
        check(handle != 0L) { "No local model is loaded" }
        val listener = SnapshotListener { content, reasoning ->
            onSnapshot(String(content, Charsets.UTF_8), String(reasoning, Charsets.UTF_8))
        }
        val result = nativeGenerate(handle, requestJson.toByteArray(Charsets.UTF_8), listener)
        return String(result, Charsets.UTF_8)
    }

    override fun cancel() {
        if (libraryLoaded) {
            nativeCancel()
        }
    }

    override fun clearCancel() {
        if (libraryLoaded) {
            nativeClearCancel()
        }
    }

    private external fun nativeLoad(path: ByteArray, contextTokens: Int, threads: Int): Long

    private external fun nativeUnload(handle: Long)

    private external fun nativeGenerate(handle: Long, request: ByteArray, listener: SnapshotListener): ByteArray

    private external fun nativeCancel()

    private external fun nativeClearCancel()

    private companion object {
        @Volatile
        var libraryLoaded = false

        /**
         * The library is built for one CPU level (see CMakeLists.txt); on a
         * processor without it the first matrix product would crash the app
         * with an illegal instruction, so this refuses to load it instead.
         */
        @Synchronized
        fun loadLibrary() {
            if (libraryLoaded) {
                return
            }
            val missing = CpuFeatures.missing(File("/proc/cpuinfo").readText())
            if (missing.isNotEmpty()) {
                throw IllegalStateException("This phone's processor lacks ${missing.joinToString(", ")}, which local models need")
            }
            System.loadLibrary("jonaki_llama")
            libraryLoaded = true
        }
    }
}
