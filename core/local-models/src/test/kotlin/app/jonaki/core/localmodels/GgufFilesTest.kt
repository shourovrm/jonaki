package app.jonaki.core.localmodels

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GgufFilesTest {
    private fun recordedTree(name: String): List<HubFile> =
        HuggingFaceJson.parseTree(File(System.getProperty("jonaki.testdata"), "huggingface/$name").readText())

    private fun file(path: String, sizeBytes: Long = 1_000) = HubFile(path, sizeBytes, sha256 = "00")

    @Test
    fun theDefaultIsQ4KM() {
        val files = recordedTree("tree-unsloth-qwen3.5-0.8b.json")
        assertEquals("Qwen3.5-0.8B-Q4_K_M.gguf", GgufFiles.defaultFile(files)?.path)
    }

    @Test
    fun withoutQ4KMTheDefaultIsQ40() {
        val files = listOf(file("m-Q8_0.gguf", 900), file("m-Q4_0.gguf", 500), file("m-Q4_K_S.gguf", 480))
        assertEquals("m-Q4_0.gguf", GgufFiles.defaultFile(files)?.path)
    }

    @Test
    fun withoutEitherTheDefaultIsTheSmallestQ4() {
        val files = listOf(file("m-Q4_1.gguf", 540), file("m-Q4_K_S.gguf", 480), file("m-IQ4_XS.gguf", 450), file("m-Q3_K_M.gguf", 400))
        assertEquals("m-Q4_K_S.gguf", GgufFiles.defaultFile(files)?.path)
    }

    @Test
    fun unquantizedFilesAreNeverTheDefault() {
        assertNull(GgufFiles.defaultFile(listOf(file("m-BF16.gguf", 100), file("m-F16.gguf", 100), file("m-Q8_0.gguf", 500))))
    }

    @Test
    fun projectorsMatricesDraftsAndSplitPartsAreNotOffered() {
        assertFalse(GgufFiles.isLoadableAlone(file("mmproj-F16.gguf")))
        assertFalse(GgufFiles.isLoadableAlone(file("Qwen_Qwen3.5-35B-A3B-imatrix.gguf")))
        assertFalse(GgufFiles.isLoadableAlone(file("imatrix_unsloth.gguf_file")))
        assertFalse(GgufFiles.isLoadableAlone(file("mtp-gemma-4-E2B-it.gguf")))
        assertFalse(GgufFiles.isLoadableAlone(file("eagle3-gpt-oss-120b-Q8_0.gguf")))
        assertFalse(GgufFiles.isLoadableAlone(file("Q4_K_M/model-Q4_K_M-00001-of-00003.gguf")))
        assertFalse(GgufFiles.isLoadableAlone(file("README.md")))
        assertTrue(GgufFiles.isLoadableAlone(file("Qwen3.5-0.8B-BF16.gguf")))
    }

    @Test
    fun theRecordedTreesListOnlyModelFilesSmallestFirst() {
        val gemma = GgufFiles.loadable(recordedTree("tree-unsloth-gemma-4-e2b-it.json"))
        assertEquals(21, gemma.size)
        assertTrue(gemma.none { file -> file.path.startsWith("mmproj") || file.path.startsWith("mtp-") })
        assertEquals(gemma.sortedBy { file -> file.sizeBytes }, gemma)
        assertEquals("gemma-4-E2B-it-Q4_K_M.gguf", GgufFiles.defaultFile(gemma)?.path)

        val bartowski = recordedTree("tree-bartowski-qwen3.5-35b-a3b.json")
        assertTrue(GgufFiles.loadable(bartowski).none { file -> "imatrix" in file.path || "mmproj" in file.path })
        assertEquals("Qwen_Qwen3.5-35B-A3B-Q4_K_M.gguf", GgufFiles.defaultFile(bartowski)?.path)
    }

    @Test
    fun theQuantizationIsReadFromTheName() {
        assertEquals("Q4_K_M", GgufFiles.quantizationOf(file("Qwen3.5-2B-Q4_K_M.gguf")))
        assertEquals("Q4_K_XL", GgufFiles.quantizationOf(file("Qwen3.5-2B-UD-Q4_K_XL.gguf")))
        assertEquals("IQ4_XS", GgufFiles.quantizationOf(file("Qwen3.5-2B-IQ4_XS.gguf")))
        assertEquals("Q4_0", GgufFiles.quantizationOf(file("gemma-4-E2B-it-Q4_0.gguf")))
        assertEquals("BF16", GgufFiles.quantizationOf(file("Qwen3.5-0.8B-BF16.gguf")))
        assertEquals("Q8_0", GgufFiles.quantizationOf(file("phi-3-mini-4k-instruct.q8_0.gguf")))
        assertNull(GgufFiles.quantizationOf(file("Qwen3.5-2B.gguf")))
    }
}
