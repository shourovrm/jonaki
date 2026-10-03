package app.jonaki.core.localmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HubRepoChecksTest {
    private fun repo(id: String = "unsloth/Qwen3.5-2B-GGUF", gated: Boolean = false, architecture: String? = "qwen35", parameters: Long? = 1_881_825_088) =
        HubRepo(id, downloads = 1, isGated = gated, architecture = architecture, totalParameters = parameters, contextLength = null, license = null)

    @Test
    fun gatedComesBeforeNotSupported() {
        assertEquals(RepoBlock.GATED, HubRepoChecks.blockOf(repo(gated = true, architecture = "bert")))
        assertEquals(RepoBlock.NOT_SUPPORTED, HubRepoChecks.blockOf(repo(architecture = "bert")))
        assertEquals(RepoBlock.NOT_SUPPORTED, HubRepoChecks.blockOf(repo(architecture = null)))
        assertNull(HubRepoChecks.blockOf(repo()))
    }

    @Test
    fun theArchitecturesOfTheRecommendedModelsAndCommonFamiliesAreSupported() {
        for (name in listOf("qwen2", "qwen3", "qwen35", "qwen3moe", "gemma2", "gemma3", "gemma3n", "gemma4", "llama", "phi3", "smollm3", "granite", "lfm2", "mistral3")) {
            assertTrue(name, SupportedArchitectures.isSupported(name))
        }
        for (name in listOf("bert", "nomic-bert", "t5encoder", "clip", "wavtokenizer-dec", "eagle3", "gemma-embedding")) {
            assertFalse(name, SupportedArchitectures.isSupported(name))
        }
    }

    @Test
    fun aRecommendedFileUsesItsExactFigures() {
        val file = HubFile("Qwen3.5-2B-Q4_0.gguf", 1_214_873_856, "cd70")
        assertEquals(RecommendedModels.QWEN35_2B.memoryNeed, HubRepoChecks.fileNeed(repo(), file))
    }

    @Test
    fun anyOtherFileUsesTheEstimate() {
        val file = HubFile("Qwen3.5-2B-Q8_0.gguf", 2_012_012_800, "1b04")
        val need = HubRepoChecks.fileNeed(repo(), file)
        assertEquals(2_012_012_800L, need.fileBytes)
        assertEquals(MemoryEstimate.kvCacheBytes(1_881_825_088, "qwen35"), need.kvCacheBytes)
    }

    @Test
    fun aRepositoryWithoutAParameterCountHasNoRowEstimate() {
        assertNull(HubRepoChecks.estimatedNeed(repo(parameters = null)))
    }
}
