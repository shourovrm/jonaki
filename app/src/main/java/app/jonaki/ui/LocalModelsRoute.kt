package app.jonaki.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.jonaki.JonakiApplication
import app.jonaki.settings.LocalModelToolList
import app.jonaki.core.localmodels.GgufFiles
import app.jonaki.core.localmodels.HubFile
import app.jonaki.core.localmodels.HubRepo
import app.jonaki.core.localmodels.HuggingFaceClient
import app.jonaki.core.localmodels.MemoryFit
import app.jonaki.core.localmodels.RecommendedModels
import app.jonaki.core.ui.UsageFormat
import app.jonaki.feature.settings.DeviceUi
import app.jonaki.feature.settings.DownloadedModelUi
import app.jonaki.feature.settings.HubSearchUi
import app.jonaki.feature.settings.LocalModelsActions
import app.jonaki.feature.settings.LocalModelsScreen
import app.jonaki.feature.settings.LocalModelsSummaryUi
import app.jonaki.feature.settings.LocalModelsUiState
import app.jonaki.feature.settings.RepoFilesUi
import app.jonaki.localmodels.DeviceResources
import app.jonaki.localmodels.DownloadSpec
import app.jonaki.localmodels.LocalModels
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The pause after typing before the search runs, so each key press does not send a request. */
private const val SEARCH_DELAY_MILLIS = 600L

/** A repository's file list as fetched for the page. */
private sealed interface FilesFetch {
    data object Loading : FilesFetch

    data object Failed : FilesFetch

    data class Done(val files: List<HubFile>) : FilesFetch
}

/** The search's own phases; the rows are built from [HubRepo]s each time the page draws. */
private sealed interface SearchFetch {
    data object Idle : SearchFetch

    data object Loading : SearchFetch

    data object Failed : SearchFetch

    data class Done(val repos: List<HubRepo>) : SearchFetch
}

/**
 * What the page remembers while it is open: the search, fetched file
 * lists, which repository's files are showing, and downloads it started.
 */
private class LocalModelsPageMemory(private val localModels: LocalModels, private val scope: CoroutineScope) {
    var search: SearchFetch by mutableStateOf(SearchFetch.Idle)
    val fileLists = mutableStateMapOf<String, FilesFetch>()

    /** File name to the free bytes it needed when Download was refused. */
    val noSpace = mutableStateMapOf<String, Long>()

    /** Repository to the file its search row's Download button started. */
    val startedFromRepo = mutableStateMapOf<String, String>()

    /** Repositories whose file list is being fetched for a Download tap. */
    val resolving = mutableStateListOf<String>()
    private var searchJob: Job? = null

    fun repo(repoId: String): HubRepo? = (search as? SearchFetch.Done)?.repos?.firstOrNull { repo -> repo.id == repoId }

    fun searchSoon(query: String) = runSearch(query, delayMillis = SEARCH_DELAY_MILLIS)

    fun searchNow(query: String) = runSearch(query, delayMillis = 0)

    private fun runSearch(query: String, delayMillis: Long) {
        searchJob?.cancel()
        if (query.isBlank()) {
            search = SearchFetch.Idle
            return
        }
        searchJob = scope.launch {
            delay(delayMillis)
            search = SearchFetch.Loading
            search = try {
                SearchFetch.Done(localModels.hub.search(query))
            } catch (failure: IOException) {
                SearchFetch.Failed
            }
        }
    }

    /** Fetches the list once per page visit; a failed fetch is tried again on the next tap. */
    suspend fun filesOf(repoId: String): List<HubFile>? {
        val known = fileLists[repoId]
        if (known is FilesFetch.Done) {
            return known.files
        }
        fileLists[repoId] = FilesFetch.Loading
        return try {
            val files = localModels.hub.files(repoId)
            fileLists[repoId] = FilesFetch.Done(files)
            files
        } catch (failure: IOException) {
            fileLists[repoId] = FilesFetch.Failed
            null
        }
    }

    fun start(spec: DownloadSpec) {
        scope.launch {
            val refused = withContext(Dispatchers.IO) { localModels.downloads.start(spec) }
            if (refused == null) {
                noSpace.remove(spec.fileName)
            } else {
                noSpace[spec.fileName] = refused.neededBytes
            }
        }
    }
}

/** Settings > Local models (D-133); the app's side of [LocalModelsScreen]. */
@Composable
internal fun LocalModelsRoute(application: JonakiApplication, onBack: () -> Unit) {
    val localModels = application.localModels
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val memory = remember { LocalModelsPageMemory(localModels, scope) }
    var query by rememberSaveable { mutableStateOf("") }
    var openRepoId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        // The query survives the process; its results do not, so they are fetched again.
        memory.searchNow(query)
    }
    val changes by localModels.changes.collectAsState()
    val downloadStates by remember { localModels.downloads.states }.collectAsState(initial = emptyMap())
    // Read again on every resume, after every finished download and every delete.
    var deviceCheck by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { deviceCheck += 1 }
    var deviceMemory by remember { mutableStateOf(DeviceResources.memory(context)) }
    var freeStorage by remember { mutableStateOf<Long?>(null) }
    var downloadedFiles by remember { mutableStateOf(emptyList<File>()) }
    LaunchedEffect(deviceCheck, changes) {
        deviceMemory = DeviceResources.memory(context)
        withContext(Dispatchers.IO) {
            freeStorage = DeviceResources.allocatableBytes(context, context.noBackupFilesDir)
            downloadedFiles = localModels.store.downloaded()
        }
    }
    val settingsSnapshot by application.settings.snapshot.collectAsState()
    var toolCosts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    LaunchedEffect(Unit) {
        // Builds the tools with the saved keys, so it runs off the main thread.
        toolCosts = withContext(Dispatchers.IO) { application.localModelTools.tokenCosts() }
    }
    val budget = MemoryFit.budgetBytes(deviceMemory.availableBytes, deviceMemory.totalBytes)
    val snapshot = DownloadSnapshot(
        downloadedNames = downloadedFiles.map { file -> file.name }.toSet(),
        states = downloadStates,
        noSpace = memory.noSpace.toMap(),
    )
    val openRepo = openRepoId?.let { repoId -> memory.repo(repoId) }
    if (openRepo != null) {
        BackHandler { openRepoId = null }
    }
    val state = LocalModelsUiState(
        device = DeviceUi(
            ram = UsageFormat.byteSize(deviceMemory.totalBytes),
            availableMemory = UsageFormat.byteSize(deviceMemory.availableBytes),
            freeStorage = freeStorage?.let(UsageFormat::byteSize) ?: "…",
        ),
        downloading = LocalModelsRows.downloading(snapshot),
        downloaded = downloadedFiles.map { file -> DownloadedModelUi(file.name, UsageFormat.byteSize(file.length())) },
        tools = LocalModelsRows.tools(
            choices = application.localModelTools.choices,
            enabled = LocalModelToolList.offered(settingsSnapshot.localModelTools),
            tokenCosts = toolCosts,
        ),
        recommended = LocalModelsRows.recommended(RecommendedModels.ALL, budget, snapshot),
        query = query,
        search = searchUi(memory, budget, snapshot),
        openRepo = openRepo?.let { repo -> filesUi(repo, memory, budget, snapshot) },
    )
    val actions = LocalModelsActions(
        onBack = onBack,
        onQueryChange = { text ->
            query = text
            memory.searchSoon(text)
        },
        onSearch = { memory.searchNow(query) },
        onDownloadRecommended = { fileName ->
            val model = RecommendedModels.ALL.firstOrNull { candidate -> candidate.fileName == fileName }
            if (model != null) {
                memory.start(DownloadSpec(model.downloadUrl, model.fileName, model.sizeBytes, model.sha256))
            }
        },
        onDownloadRepo = { repoId ->
            scope.launch { downloadSuggested(repoId, memory, onNoSuggestion = { openRepoId = repoId }) }
        },
        onOpenRepo = { repoId ->
            openRepoId = repoId
            scope.launch { memory.filesOf(repoId) }
        },
        onCloseRepo = { openRepoId = null },
        onDownloadFile = { repoId, fileName ->
            val files = (memory.fileLists[repoId] as? FilesFetch.Done)?.files.orEmpty()
            val file = files.firstOrNull { candidate -> candidate.fileName == fileName }
            if (file != null) {
                memory.start(specOf(repoId, file))
            }
        },
        onCancelDownload = { fileName -> localModels.downloads.cancel(fileName) },
        onCancelRepoDownload = { repoId ->
            val fileName = memory.startedFromRepo[repoId]
            if (fileName != null) {
                localModels.downloads.cancel(fileName)
            }
        },
        onDelete = { fileName -> scope.launch(Dispatchers.IO) { localModels.delete(fileName) } },
        onToolChange = { toolName, enabled -> application.localModelTools.setEnabled(toolName, enabled) },
    )
    LocalModelsScreen(state, actions)
}

/** A search row's Download: the suggested file, or the file list when the repository has none. */
private suspend fun downloadSuggested(repoId: String, memory: LocalModelsPageMemory, onNoSuggestion: () -> Unit) {
    memory.resolving += repoId
    val files = try {
        memory.filesOf(repoId)
    } finally {
        memory.resolving -= repoId
    }
    if (files == null) {
        onNoSuggestion()
        return
    }
    val suggested = GgufFiles.defaultFile(files)
    if (suggested?.sha256 == null) {
        onNoSuggestion()
        return
    }
    memory.startedFromRepo[repoId] = suggested.fileName
    memory.start(specOf(repoId, suggested))
}

/**
 * A searched file downloads from the main branch, not a pinned commit:
 * the tree listing gives no commit. If the file changes on Hugging Face
 * during the download, the SHA-256 check fails and the file is deleted.
 */
private fun specOf(repoId: String, file: HubFile): DownloadSpec = DownloadSpec(
    url = HuggingFaceClient.downloadUrl(repoId, revision = "main", path = file.path),
    fileName = file.fileName,
    sizeBytes = file.sizeBytes,
    sha256 = file.sha256.orEmpty(),
)

private fun searchUi(memory: LocalModelsPageMemory, budgetBytes: Long, snapshot: DownloadSnapshot): HubSearchUi =
    when (val search = memory.search) {
        SearchFetch.Idle -> HubSearchUi.Idle
        SearchFetch.Loading -> HubSearchUi.Loading
        SearchFetch.Failed -> HubSearchUi.Failed
        is SearchFetch.Done -> HubSearchUi.Results(
            search.repos.map { repo ->
                LocalModelsRows.searchResult(
                    repo = repo,
                    budgetBytes = budgetBytes,
                    startedFile = memory.startedFromRepo[repo.id],
                    isResolving = repo.id in memory.resolving,
                    snapshot = snapshot,
                )
            },
        )
    }

private fun filesUi(repo: HubRepo, memory: LocalModelsPageMemory, budgetBytes: Long, snapshot: DownloadSnapshot): RepoFilesUi {
    val header = LocalModelsRows.searchResult(repo, budgetBytes, startedFile = null, isResolving = false, snapshot = snapshot)
    return when (val fetch = memory.fileLists[repo.id]) {
        null, FilesFetch.Loading -> RepoFilesUi(header, emptyList(), isLoading = true)
        FilesFetch.Failed -> RepoFilesUi(header, emptyList(), failed = true)
        is FilesFetch.Done -> RepoFilesUi(header, LocalModelsRows.files(repo, fetch.files, budgetBytes, snapshot))
    }
}

/**
 * The first Settings page's row: how many models are downloaded, and the
 * largest recommended one that fits now, by the same budget as the page.
 * Lists a folder, so call it off the main thread.
 */
internal fun localModelsSummaryOf(localModels: LocalModels, context: Context): LocalModelsSummaryUi {
    val memory = DeviceResources.memory(context)
    val budget = MemoryFit.budgetBytes(memory.availableBytes, memory.totalBytes)
    return LocalModelsSummaryUi(
        downloadedCount = localModels.store.downloaded().size,
        largestFittingName = RecommendedModels.largestThatFits(budget)?.name,
    )
}
