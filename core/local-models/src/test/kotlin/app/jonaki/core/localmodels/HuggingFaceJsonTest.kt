package app.jonaki.core.localmodels

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuggingFaceJsonTest {
    private fun recorded(name: String): String = File(System.getProperty("jonaki.testdata"), "huggingface/$name").readText()

    @Test
    fun aSearchResultCarriesTheGgufMetadataAndLicence() {
        val repos = HuggingFaceJson.parseSearch(recorded("search-qwen3.5.json"))
        assertEquals(8, repos.size)
        val unsloth4b = repos.single { repo -> repo.id == "unsloth/Qwen3.5-4B-GGUF" }
        assertEquals(1_024_272L, unsloth4b.downloads)
        assertFalse(unsloth4b.isGated)
        assertEquals("qwen35", unsloth4b.architecture)
        assertEquals(4_205_751_296L, unsloth4b.totalParameters)
        assertEquals(262_144L, unsloth4b.contextLength)
        assertEquals("apache-2.0", unsloth4b.license)
    }

    @Test
    fun manualGatingCountsAsGated() {
        val repos = HuggingFaceJson.parseSearch(recorded("search-google-qat.json"))
        val gemma3 = repos.single { repo -> repo.id == "google/gemma-3-4b-it-qat-q4_0-gguf" }
        assertTrue(gemma3.isGated)
        assertEquals("gemma", gemma3.license)
        val gemma4 = repos.single { repo -> repo.id == "google/gemma-4-31B-it-qat-q4_0-gguf" }
        assertFalse(gemma4.isGated)
    }

    @Test
    fun aMissingLicenceIsNull() {
        val repos = HuggingFaceJson.parseSearch(recorded("search-bert.json"))
        val noLicence = repos.single { repo -> repo.id == "RichardErkhov/Q-bert_-_MetaMath-Cybertron-Starling-gguf" }
        assertNull(noLicence.license)
        assertEquals("llama", noLicence.architecture)
    }

    @Test
    fun theTreeListsFilesWithSizeAndSha256() {
        val files = HuggingFaceJson.parseTree(recorded("tree-unsloth-qwen3.5-0.8b.json"))
        val q40 = files.single { file -> file.path == "Qwen3.5-0.8B-Q4_0.gguf" }
        assertEquals(507_154_688L, q40.sizeBytes)
        assertEquals("444406ddd926550c724ec18d5120a9d40ded44908a063b0e66e9a7e5464c652c", q40.sha256)
        // Small files are kept in Git, not LFS, so they have no SHA-256.
        assertNull(files.single { file -> file.path == "README.md" }.sha256)
    }

    @Test
    fun foldersAreLeftOut() {
        val files = HuggingFaceJson.parseTree(recorded("tree-bartowski-qwen3.5-35b-a3b.json"))
        assertTrue(files.none { file -> file.path == "kld_results" || file.path == "Qwen_Qwen3.5-35B-A3B-bf16" })
        assertEquals(32, files.size)
    }

    @Test
    fun anythingButAListGivesNothing() {
        assertEquals(emptyList<HubRepo>(), HuggingFaceJson.parseSearch("""{"error":"Invalid credentials"}"""))
        assertEquals(emptyList<HubFile>(), HuggingFaceJson.parseTree("""{"error":"Repository not found"}"""))
    }
}
