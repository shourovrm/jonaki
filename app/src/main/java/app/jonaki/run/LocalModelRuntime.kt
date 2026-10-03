package app.jonaki.run

import android.content.ComponentCallbacks2
import app.jonaki.core.modelcatalog.ModelKey
import app.jonaki.localmodels.LocalModelStore
import app.jonaki.providers.localllama.LocalLlamaProvider
import app.jonaki.providers.localllama.NativeLlamaEngine
import app.jonaki.settings.ChatService

/**
 * Runs the downloaded GGUF models (D-133). Every finished file that
 * [store] lists is a model of the Local service, named by its file name.
 * One provider serves every thread, because it keeps one model loaded.
 */
class LocalModelRuntime(private val store: LocalModelStore) {
    private val providerOnFirstUse = lazy {
        LocalLlamaProvider(store.modelsFolder, NativeLlamaEngine(), LocalLlamaProvider.newEngineDispatcher())
    }

    val provider: LocalLlamaProvider by providerOnFirstUse

    /** "local:<file name>" for each downloaded model, by name. */
    fun modelKeys(): List<String> =
        store.downloaded().map { file -> ModelKey.of(ChatService.LOCAL.key, file.name) }

    /** Frees the loaded model, if a local model ever ran, once any running turn has ended. */
    suspend fun unload() {
        if (providerOnFirstUse.isInitialized()) {
            provider.unload()
        }
    }

    companion object {
        fun isLocal(modelKey: String?): Boolean =
            modelKey != null && ModelKey.serviceOf(modelKey) == ChatService.LOCAL.key

        /**
         * Whether a memory warning should free the loaded model. A run in the
         * foreground service gets none of the background levels, so the model
         * is only freed while no run needs it, or when memory is critical.
         */
        @Suppress("DEPRECATION") // The RUNNING_ levels still arrive on Android 13 and older.
        fun shouldUnload(trimLevel: Int): Boolean =
            trimLevel == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                trimLevel >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
    }
}
