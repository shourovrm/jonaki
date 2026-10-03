package app.jonaki.run

import android.content.ComponentCallbacks2
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelsTest {
    @Test
    fun finishedGgufFilesAreTheModelsByName() {
        val folder = Files.createTempDirectory("models").toFile()
        try {
            File(folder, "Qwen3.5-2B-Q4_0.gguf").writeText("weights")
            File(folder, "Qwen3.5-0.8B-Q4_0.gguf").writeText("weights")
            File(folder, "gemma-4-E2B-it-Q4_0.gguf.part").writeText("half")
            File(folder, "notes.txt").writeText("text")
            File(folder, "folder.gguf").mkdir()

            val ids = LocalModelFiles.modelIds(folder.listFiles()!!.toList())

            assertEquals(listOf("Qwen3.5-0.8B-Q4_0.gguf", "Qwen3.5-2B-Q4_0.gguf"), ids)
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun localKeysAreRecognised() {
        assertTrue(LocalModels.isLocal("local:Qwen3.5-2B-Q4_0.gguf"))
        assertFalse(LocalModels.isLocal("openrouter:qwen/qwen3.5-2b"))
        assertFalse(LocalModels.isLocal(null))
    }

    @Suppress("DEPRECATION")
    @Test
    fun theModelIsFreedInTheBackgroundOrWhenMemoryIsCritical() {
        assertTrue(LocalModelFiles.shouldUnload(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND))
        assertTrue(LocalModelFiles.shouldUnload(ComponentCallbacks2.TRIM_MEMORY_COMPLETE))
        assertTrue(LocalModelFiles.shouldUnload(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL))
        // Leaving the app's screen while a run goes on must not drop the model mid-run.
        assertFalse(LocalModelFiles.shouldUnload(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN))
        assertFalse(LocalModelFiles.shouldUnload(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW))
    }
}
