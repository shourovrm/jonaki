package app.jonaki.localmodels

import android.content.Context
import app.jonaki.core.localmodels.HuggingFaceClient
import app.jonaki.core.localmodels.ModelDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient

/** Finding, downloading and deleting local models, for Settings > Local models and the download worker (D-133). */
class LocalModels(context: Context, httpClient: OkHttpClient, scope: CoroutineScope) {
    val store = LocalModelStore(context.noBackupFilesDir)
    val hub = HuggingFaceClient(httpClient)
    val downloader = ModelDownloader(httpClient)
    val downloads = ModelDownloads(context, store, scope)

    private val changeCount = MutableStateFlow(0)

    /** Goes up each time a model file appears or is deleted, so screens read the folder again. */
    val changes: StateFlow<Int> = changeCount.asStateFlow()

    fun markChanged() {
        changeCount.update { count -> count + 1 }
    }

    fun delete(fileName: String) {
        store.delete(fileName)
        markChanged()
    }
}
