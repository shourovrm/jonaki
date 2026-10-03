package app.jonaki.ui

import app.jonaki.core.localmodels.HubFile
import app.jonaki.core.localmodels.HubRepo
import app.jonaki.core.localmodels.MemoryFit
import app.jonaki.core.localmodels.RecommendedModels
import app.jonaki.feature.settings.DownloadUi
import app.jonaki.feature.settings.FitUi
import app.jonaki.feature.settings.RepoBlockUi
import app.jonaki.localmodels.DownloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalModelsRowsTest {
    /** The A059 on 2026-10-03: 2.7 GB available of 7.6 GB. */
    private val budget = MemoryFit.budgetBytes(availableBytes = 2_700_000_000, totalBytes = 7_600_000_000)
    private val nothing = DownloadSnapshot(downloadedNames = emptySet(), states = emptyMap(), noSpace = emptyMap())

    private val qwen2b = HubRepo(
        id = "unsloth/Qwen3.5-2B-GGUF",
        downloads = 314_271,
        isGated = false,
        architecture = "qwen35",
        totalParameters = 1_881_825_088,
        contextLength = 262_144,
        license = "apache-2.0",
    )

    @Test
    fun recommendedRowsCarryTheExactFitOnTheTestPhone() {
        val rows = LocalModelsRows.recommended(RecommendedModels.ALL, budget, nothing)
        assertEquals(listOf("Qwen3.5-0.8B", "Qwen3.5-2B", "Qwen3.5-4B", "Gemma 4 E2B"), rows.map { row -> row.name })
        assertEquals(listOf(FitUi.FITS, FitUi.FITS, FitUi.TOO_BIG, FitUi.TOO_BIG), rows.map { row -> row.fit })
        assertEquals("1.2 GB", rows[1].size)
    }

    @Test
    fun aFinishedFileWinsOverItsJobAndARunningJobShowsProgress() {
        val snapshot = DownloadSnapshot(
            downloadedNames = setOf("a.gguf"),
            states = mapOf("a.gguf" to DownloadState.Failed, "b.gguf" to DownloadState.Running(300_000_000, 1_200_000_000)),
            noSpace = mapOf("b.gguf" to 5_000_000_000, "c.gguf" to 2_100_000_000),
        )
        assertEquals(DownloadUi.Downloaded, LocalModelsRows.downloadOf("a.gguf", snapshot))
        assertEquals(DownloadUi.Running(0.25f, "300.0 MB", "1.2 GB"), LocalModelsRows.downloadOf("b.gguf", snapshot))
        assertEquals(DownloadUi.NoSpace("2.1 GB"), LocalModelsRows.downloadOf("c.gguf", snapshot))
        assertEquals(DownloadUi.Ready, LocalModelsRows.downloadOf("d.gguf", snapshot))
    }

    @Test
    fun aSearchResultShowsItsNumbersAndAnEstimatedFit() {
        val row = LocalModelsRows.searchResult(qwen2b, budget, startedFile = null, isResolving = false, snapshot = nothing)
        assertEquals("1.9B", row.parameters)
        assertEquals("314,271", row.downloads)
        assertEquals("apache-2.0", row.license)
        // The estimate leans high: 2.41 GB against a 2.7 GB budget is within the last 15 %.
        assertEquals(FitUi.TIGHT, row.fit)
        assertNull(row.block)
        assertEquals(DownloadUi.Ready, row.download)
    }

    @Test
    fun aSearchRowFollowsTheDownloadItStarted() {
        val resolving = LocalModelsRows.searchResult(qwen2b, budget, startedFile = null, isResolving = true, snapshot = nothing)
        assertEquals(DownloadUi.Waiting, resolving.download)
        val started = DownloadSnapshot(emptySet(), mapOf("Qwen3.5-2B-Q4_K_M.gguf" to DownloadState.Waiting), emptyMap())
        val row = LocalModelsRows.searchResult(qwen2b, budget, startedFile = "Qwen3.5-2B-Q4_K_M.gguf", isResolving = false, snapshot = started)
        assertEquals(DownloadUi.Waiting, row.download)
    }

    @Test
    fun gatedAndUnknownArchitecturesAreBlocked() {
        val gated = LocalModelsRows.searchResult(qwen2b.copy(isGated = true), budget, null, false, nothing)
        assertEquals(RepoBlockUi.GATED, gated.block)
        val bert = LocalModelsRows.searchResult(qwen2b.copy(architecture = "bert"), budget, null, false, nothing)
        assertEquals(RepoBlockUi.NOT_SUPPORTED, bert.block)
    }

    @Test
    fun theFileListMarksTheSuggestedFileAndUsesExactFiguresForRecommendedOnes() {
        val files = listOf(
            HubFile("Qwen3.5-2B-Q4_0.gguf", 1_214_873_856, "cd70"),
            HubFile("Qwen3.5-2B-Q4_K_M.gguf", 1_280_835_840, "aaf4"),
            HubFile("Qwen3.5-2B-Q8_0.gguf", 2_012_012_800, "1b04"),
            HubFile("mmproj-F16.gguf", 668_227_264, "7035"),
            HubFile("README.md", 64_059, null),
        )
        val rows = LocalModelsRows.files(qwen2b, files, budget, nothing)
        assertEquals(listOf("Qwen3.5-2B-Q4_0.gguf", "Qwen3.5-2B-Q4_K_M.gguf", "Qwen3.5-2B-Q8_0.gguf"), rows.map { row -> row.fileName })
        assertEquals(listOf(false, true, false), rows.map { row -> row.isSuggested })
        assertEquals(FitUi.FITS, rows[0].fit)
        assertEquals(FitUi.TOO_BIG, rows[2].fit)
    }

    @Test
    fun parameterCountsHaveOneDecimal() {
        assertEquals("0.8B", LocalModelsRows.parameterCount(752_393_024))
        assertEquals("4.2B", LocalModelsRows.parameterCount(4_205_751_296))
        assertEquals("124.1M", LocalModelsRows.parameterCount(124_100_352))
    }
}
