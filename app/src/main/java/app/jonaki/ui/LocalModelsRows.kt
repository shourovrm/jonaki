package app.jonaki.ui

import app.jonaki.core.localmodels.FitLabel
import app.jonaki.core.localmodels.GgufFiles
import app.jonaki.core.localmodels.HubFile
import app.jonaki.core.localmodels.HubRepo
import app.jonaki.core.localmodels.HubRepoChecks
import app.jonaki.core.localmodels.MemoryFit
import app.jonaki.core.localmodels.MemoryNeed
import app.jonaki.core.localmodels.RecommendedModel
import app.jonaki.core.localmodels.RepoBlock
import app.jonaki.core.ui.UsageFormat
import app.jonaki.feature.settings.ActiveDownloadUi
import app.jonaki.feature.settings.DownloadUi
import app.jonaki.feature.settings.LocalToolUi
import app.jonaki.settings.LocalModelToolList
import app.jonaki.feature.settings.FitUi
import app.jonaki.feature.settings.RecommendedModelUi
import app.jonaki.feature.settings.RepoBlockUi
import app.jonaki.feature.settings.RepoFileUi
import app.jonaki.feature.settings.SearchResultUi
import app.jonaki.localmodels.DownloadState
import java.text.NumberFormat
import java.util.Locale

/**
 * What each download looks like right now: the files in the models folder,
 * the jobs WorkManager knows, and the downloads refused for lack of space.
 */
data class DownloadSnapshot(
    val downloadedNames: Set<String>,
    val states: Map<String, DownloadState>,
    /** File name to the free bytes it needed when Download was refused. */
    val noSpace: Map<String, Long>,
)

/** Turns the local-model facts into the rows of Settings > Local models (D-133); no Android calls, so JVM-tested. */
object LocalModelsRows {
    fun fitOf(need: MemoryNeed, budgetBytes: Long): FitUi = when (MemoryFit.label(need, budgetBytes)) {
        FitLabel.FITS -> FitUi.FITS
        FitLabel.TIGHT -> FitUi.TIGHT
        FitLabel.TOO_BIG -> FitUi.TOO_BIG
    }

    /**
     * Downloads still going, in file-name order, whatever started them; a
     * file already in the models folder is done even if its job record lingers.
     */
    fun downloading(snapshot: DownloadSnapshot): List<ActiveDownloadUi> =
        snapshot.states
            .filter { (fileName, state) ->
                val isActive = state is DownloadState.Waiting || state is DownloadState.Running
                isActive && fileName !in snapshot.downloadedNames
            }
            .toSortedMap()
            .map { (fileName, state) -> ActiveDownloadUi(fileName, downloadUiOf(state)) }

    /** The local tool list in its fixed order (D-133), with each tool's switch and cost. */
    fun tools(choices: List<String>, enabled: Set<String>, tokenCosts: Map<String, Int>): List<LocalToolUi> =
        choices.map { toolName ->
            LocalToolUi(
                name = toolName,
                tokens = tokenCosts[toolName],
                enabled = toolName in enabled,
                onlyWithFiles = toolName in LocalModelToolList.ONLY_WITH_FILES,
            )
        }

    /** A finished file wins over any job record; a running job over an earlier refusal. */
    fun downloadOf(fileName: String, snapshot: DownloadSnapshot): DownloadUi {
        if (fileName in snapshot.downloadedNames) {
            return DownloadUi.Downloaded
        }
        val state = snapshot.states[fileName]
        if (state != null) {
            return downloadUiOf(state)
        }
        val needed = snapshot.noSpace[fileName] ?: return DownloadUi.Ready
        return DownloadUi.NoSpace(UsageFormat.byteSize(needed))
    }

    private fun downloadUiOf(state: DownloadState): DownloadUi = when (state) {
        DownloadState.Waiting -> DownloadUi.Waiting
        is DownloadState.Running -> DownloadUi.Running(
            fraction = (state.downloadedBytes.toDouble() / state.totalBytes).toFloat().coerceIn(0f, 1f),
            downloaded = UsageFormat.byteSize(state.downloadedBytes),
            total = UsageFormat.byteSize(state.totalBytes),
        )
        DownloadState.Failed -> DownloadUi.Failed
        DownloadState.Damaged -> DownloadUi.Damaged
        is DownloadState.NoSpace -> DownloadUi.NoSpace(UsageFormat.byteSize(state.neededBytes))
    }

    fun recommended(models: List<RecommendedModel>, budgetBytes: Long, snapshot: DownloadSnapshot): List<RecommendedModelUi> =
        models.map { model ->
            RecommendedModelUi(
                fileName = model.fileName,
                name = model.name,
                size = UsageFormat.byteSize(model.sizeBytes),
                fit = fitOf(model.memoryNeed, budgetBytes),
                download = downloadOf(model.fileName, snapshot),
            )
        }

    /**
     * [startedFile] is the file a search row's Download button started, if
     * any; [isResolving] is true while its file list is being fetched.
     */
    fun searchResult(repo: HubRepo, budgetBytes: Long, startedFile: String?, isResolving: Boolean, snapshot: DownloadSnapshot): SearchResultUi {
        val download = when {
            startedFile != null -> downloadOf(startedFile, snapshot)
            isResolving -> DownloadUi.Waiting
            else -> DownloadUi.Ready
        }
        return SearchResultUi(
            repoId = repo.id,
            parameters = repo.totalParameters?.let(::parameterCount),
            downloads = NumberFormat.getIntegerInstance(Locale.ENGLISH).format(repo.downloads),
            license = repo.license,
            fit = HubRepoChecks.estimatedNeed(repo)?.let { need -> fitOf(need, budgetBytes) },
            block = blockOf(repo),
            download = download,
        )
    }

    /** Only files that can be checked after download: every model file on Hugging Face is in Git LFS and has a SHA-256. */
    fun files(repo: HubRepo, files: List<HubFile>, budgetBytes: Long, snapshot: DownloadSnapshot): List<RepoFileUi> {
        val suggested = GgufFiles.defaultFile(files)
        return GgufFiles.loadable(files)
            .filter { file -> file.sha256 != null }
            .map { file ->
                RepoFileUi(
                    fileName = file.fileName,
                    size = UsageFormat.byteSize(file.sizeBytes),
                    fit = fitOf(HubRepoChecks.fileNeed(repo, file), budgetBytes),
                    isSuggested = file == suggested,
                    download = downloadOf(file.fileName, snapshot),
                )
            }
    }

    private fun blockOf(repo: HubRepo): RepoBlockUi? = when (HubRepoChecks.blockOf(repo)) {
        RepoBlock.GATED -> RepoBlockUi.GATED
        RepoBlock.NOT_SUPPORTED -> RepoBlockUi.NOT_SUPPORTED
        null -> null
    }

    /** "0.8B", "4.2B", "124.1M": one decimal, as model names write it; from half a billion in billions. */
    fun parameterCount(count: Long): String {
        if (count >= BILLION / 2) {
            return String.format(Locale.ENGLISH, "%.1fB", count / BILLION)
        }
        return String.format(Locale.ENGLISH, "%.1fM", count / MILLION)
    }

    private const val BILLION = 1_000_000_000.0
    private const val MILLION = 1_000_000.0
}
