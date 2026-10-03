package app.jonaki.run

import android.content.ComponentCallbacks2
import android.content.Context
import app.jonaki.core.modelcatalog.ModelKey
import app.jonaki.providers.localllama.LocalLlamaProvider
import app.jonaki.providers.localllama.NativeLlamaEngine
import app.jonaki.settings.ChatService
import java.io.File

/**
 * GGUF models on this phone (D-133). Downloads land in [folder] (the Local
 * models page and its worker write them); every finished .gguf file there is
 * a model of the Local service, named by its file name. One provider serves
 * every thread, because it keeps one model loaded.
 */
class LocalModels(context: Context) {
    /** No-backup storage: a model is gigabytes and can be downloaded again. */
    val folder = File(context.noBackupFilesDir, "models")

    private val providerOnFirstUse = lazy {
        LocalLlamaProvider(folder, NativeLlamaEngine(), LocalLlamaProvider.newEngineDispatcher())
    }

    val provider: LocalLlamaProvider by providerOnFirstUse

    /** Frees the loaded model, if a local model ever ran, once any running turn has ended. */
    suspend fun unload() {
        if (providerOnFirstUse.isInitialized()) {
            provider.unload()
        }
    }

    /** "local:<file name>" for each model file, by name. */
    fun modelKeys(): List<String> =
        LocalModelFiles.modelIds(folder.listFiles()?.toList().orEmpty())
            .map { modelId -> ModelKey.of(ChatService.LOCAL.key, modelId) }

    companion object {
        fun isLocal(modelKey: String?): Boolean =
            modelKey != null && ModelKey.serviceOf(modelKey) == ChatService.LOCAL.key
    }
}

object LocalModelFiles {
    /** Finished model files only; a download in progress has another extension until it is checked. */
    fun modelIds(files: List<File>): List<String> = files
        .filter { file -> file.isFile && file.name.endsWith(".gguf") }
        .map { file -> file.name }
        .sorted()

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
