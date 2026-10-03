package app.jonaki.feature.settings

import androidx.compose.runtime.Immutable

/** For the first page's row: "2 downloaded · Qwen3.5-2B fits" (D-133). */
@Immutable
data class LocalModelsSummaryUi(
    val downloadedCount: Int = 0,
    /** The largest recommended model that fits now; null when none does. */
    val largestFittingName: String? = null,
)

/** The memory label of a model on this phone (D-133). */
enum class FitUi {
    FITS,
    TIGHT,
    TOO_BIG,
}

/** Why a Hugging Face repository has no Download button. */
enum class RepoBlockUi {
    GATED,
    NOT_SUPPORTED,
}

/** Where one file's download stands; the row shows a button, a progress bar or a status for it. */
@Immutable
sealed interface DownloadUi {
    data object Ready : DownloadUi

    /** Queued, or looking up the file before the bytes start. */
    data object Waiting : DownloadUi

    data class Running(val fraction: Float, val downloaded: String, val total: String) : DownloadUi

    data object Downloaded : DownloadUi

    data object Failed : DownloadUi

    /** The SHA-256 did not match, so the file was deleted. */
    data object Damaged : DownloadUi

    /** [needed] is formatted, for example "2.1 GB". */
    data class NoSpace(val needed: String) : DownloadUi
}

/** Sizes already formatted by the app, for example "7.6 GB". */
@Immutable
data class DeviceUi(
    val ram: String,
    val availableMemory: String,
    val freeStorage: String,
)

@Immutable
data class DownloadedModelUi(
    /** The file name is the model's id for the local provider. */
    val fileName: String,
    val size: String,
)

@Immutable
data class RecommendedModelUi(
    /** The file name; the key the callbacks receive. */
    val fileName: String,
    val name: String,
    val size: String,
    val fit: FitUi,
    val download: DownloadUi,
)

@Immutable
data class SearchResultUi(
    val repoId: String,
    /** "4.2B"; null when the repository gives no count. */
    val parameters: String?,
    /** "1,024,272". */
    val downloads: String,
    val license: String?,
    /** An estimate for a typical 4-bit file; null without a parameter count. */
    val fit: FitUi?,
    val block: RepoBlockUi?,
    val download: DownloadUi,
)

@Immutable
data class RepoFileUi(
    val fileName: String,
    val size: String,
    val fit: FitUi,
    /** The file a search result's Download button takes. */
    val isSuggested: Boolean,
    val download: DownloadUi,
)

/** The search under the recommended list. */
@Immutable
sealed interface HubSearchUi {
    data object Idle : HubSearchUi

    data object Loading : HubSearchUi

    data object Failed : HubSearchUi

    data class Results(val results: List<SearchResultUi>) : HubSearchUi
}

/** One repository's file list, opened by tapping a search result. */
@Immutable
data class RepoFilesUi(
    val repo: SearchResultUi,
    val files: List<RepoFileUi>,
    val isLoading: Boolean = false,
    val failed: Boolean = false,
)

/** Everything Settings > Local models shows (D-133). */
@Immutable
data class LocalModelsUiState(
    val device: DeviceUi,
    val downloaded: List<DownloadedModelUi> = emptyList(),
    val recommended: List<RecommendedModelUi> = emptyList(),
    val query: String = "",
    val search: HubSearchUi = HubSearchUi.Idle,
    /** Non-null shows the file list in place of the page. */
    val openRepo: RepoFilesUi? = null,
)

class LocalModelsActions(
    val onBack: () -> Unit,
    val onQueryChange: (String) -> Unit = {},
    val onSearch: () -> Unit = {},
    val onDownloadRecommended: (fileName: String) -> Unit = {},
    /** Downloads the repository's suggested file, or opens its file list when it has none. */
    val onDownloadRepo: (repoId: String) -> Unit = {},
    val onOpenRepo: (repoId: String) -> Unit = {},
    val onCloseRepo: () -> Unit = {},
    val onDownloadFile: (repoId: String, fileName: String) -> Unit = { _, _ -> },
    val onCancelDownload: (fileName: String) -> Unit = {},
    val onCancelRepoDownload: (repoId: String) -> Unit = {},
    val onDelete: (fileName: String) -> Unit = {},
)
