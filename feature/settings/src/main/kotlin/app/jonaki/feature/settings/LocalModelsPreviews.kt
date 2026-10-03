package app.jonaki.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.ThemeMode

// The D-029 check: a 360 dp wide phone at font scale 1.3, in dark, light and
// Bangla, with 40-character repository and file names.

/** 40 characters each. */
private const val LONG_REPO = "mradermacher/Qwen3.5-9B-Heretic2-i1-GGUF"
private const val LONG_FILE = "Qwen3.5-9B-Heretic2-i1-UD-Q4_K_XL-a.gguf"

private val longRepo = SearchResultUi(
    repoId = LONG_REPO,
    parameters = "9.0B",
    downloads = "67,085",
    license = "apache-2.0",
    fit = FitUi.TOO_BIG,
    block = null,
    download = DownloadUi.Ready,
)

private val sampleState = LocalModelsUiState(
    device = DeviceUi(ram = "7.6 GB", availableMemory = "2.7 GB", freeStorage = "48.3 GB"),
    downloaded = listOf(
        DownloadedModelUi("Qwen3.5-0.8B-Q4_0.gguf", "507.2 MB"),
        DownloadedModelUi(LONG_FILE, "5.6 GB"),
    ),
    recommended = listOf(
        RecommendedModelUi("Qwen3.5-0.8B-Q4_0.gguf", "Qwen3.5-0.8B", "507.2 MB", FitUi.FITS, DownloadUi.Downloaded),
        RecommendedModelUi("Qwen3.5-2B-Q4_0.gguf", "Qwen3.5-2B", "1.2 GB", FitUi.FITS, DownloadUi.Running(0.35f, "425.2 MB", "1.2 GB")),
        RecommendedModelUi("Qwen3.5-4B-Q4_0.gguf", "Qwen3.5-4B", "2.6 GB", FitUi.TOO_BIG, DownloadUi.Ready),
        RecommendedModelUi("gemma-4-E2B-it-Q4_0.gguf", "Gemma 4 E2B", "3.0 GB", FitUi.TOO_BIG, DownloadUi.NoSpace("4.0 GB")),
    ),
    query = "qwen3.5",
    search = HubSearchUi.Results(
        listOf(
            SearchResultUi("unsloth/Qwen3.5-2B-GGUF", "1.9B", "314,271", "apache-2.0", FitUi.TIGHT, null, DownloadUi.Waiting),
            longRepo,
            SearchResultUi("google/gemma-3-4b-it-qat-q4_0-gguf", "3.9B", "2,551", "gemma", FitUi.TIGHT, RepoBlockUi.GATED, DownloadUi.Ready),
            SearchResultUi("KBLab/sentence-bert-swedish-cased", "124.1M", "69,299", "apache-2.0", FitUi.FITS, RepoBlockUi.NOT_SUPPORTED, DownloadUi.Ready),
        ),
    ),
)

private val sampleFiles = RepoFilesUi(
    repo = longRepo,
    files = listOf(
        RepoFileUi("Qwen3.5-9B-Heretic2-i1-IQ2_M.gguf", "3.4 GB", FitUi.TOO_BIG, isSuggested = false, download = DownloadUi.Ready),
        RepoFileUi(LONG_FILE, "5.6 GB", FitUi.TOO_BIG, isSuggested = true, download = DownloadUi.Damaged),
        RepoFileUi("Qwen3.5-9B-Heretic2-i1-Q8_0.gguf", "9.5 GB", FitUi.TOO_BIG, isSuggested = false, download = DownloadUi.Failed),
    ),
)

private val noActions = LocalModelsActions(onBack = {})

@Composable
private fun PageSample(themeMode: ThemeMode) {
    JonakiTheme(themeMode) { LocalModelsScreen(sampleState, noActions) }
}

@Composable
private fun FilesSample(themeMode: ThemeMode) {
    JonakiTheme(themeMode) { LocalModelsScreen(sampleState.copy(openRepo = sampleFiles), noActions) }
}

@Preview(name = "Local models, dark", widthDp = 360, heightDp = 1800, fontScale = 1.3f)
@Composable
private fun LocalModelsDarkPreview() {
    PageSample(ThemeMode.DARK)
}

@Preview(name = "Local models, light", widthDp = 360, heightDp = 1800, fontScale = 1.3f)
@Composable
private fun LocalModelsLightPreview() {
    PageSample(ThemeMode.LIGHT)
}

@Preview(name = "Local models, Bangla", locale = "bn", widthDp = 360, heightDp = 1800, fontScale = 1.3f)
@Composable
private fun LocalModelsBanglaPreview() {
    PageSample(ThemeMode.LIGHT)
}

@Preview(name = "Repository files, dark", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun RepoFilesDarkPreview() {
    FilesSample(ThemeMode.DARK)
}

@Preview(name = "Repository files, Bangla", locale = "bn", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun RepoFilesBanglaPreview() {
    FilesSample(ThemeMode.LIGHT)
}
