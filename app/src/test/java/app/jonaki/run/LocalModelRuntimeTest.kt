package app.jonaki.run

import android.content.ComponentCallbacks2
import app.jonaki.localmodels.LocalModelStore
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelRuntimeTest {
    @Test
    fun downloadedFilesAreLocalModelKeysByName() {
        val noBackupFolder = Files.createTempDirectory("no-backup").toFile()
        try {
            val store = LocalModelStore(noBackupFolder)
            store.modelsFolder.mkdirs()
            File(store.modelsFolder, "Qwen3.5-2B-Q4_0.gguf").writeText("weights")
            File(store.modelsFolder, "Qwen3.5-0.8B-Q4_0.gguf").writeText("weights")
            store.partFile("gemma-4-E2B-it-Q4_0.gguf").apply { parentFile!!.mkdirs() }.writeText("half")

            assertEquals(
                listOf("local:Qwen3.5-0.8B-Q4_0.gguf", "local:Qwen3.5-2B-Q4_0.gguf"),
                LocalModelRuntime(store).modelKeys(),
            )
        } finally {
            noBackupFolder.deleteRecursively()
        }
    }

    @Test
    fun localKeysAreRecognised() {
        assertTrue(LocalModelRuntime.isLocal("local:Qwen3.5-2B-Q4_0.gguf"))
        assertFalse(LocalModelRuntime.isLocal("openrouter:qwen/qwen3.5-2b"))
        assertFalse(LocalModelRuntime.isLocal(null))
    }

    @Suppress("DEPRECATION")
    @Test
    fun theModelIsFreedInTheBackgroundOrWhenMemoryIsCritical() {
        assertTrue(LocalModelRuntime.shouldUnload(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND))
        assertTrue(LocalModelRuntime.shouldUnload(ComponentCallbacks2.TRIM_MEMORY_COMPLETE))
        assertTrue(LocalModelRuntime.shouldUnload(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL))
        // Leaving the app's screen while a run goes on must not drop the model mid-run.
        assertFalse(LocalModelRuntime.shouldUnload(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN))
        assertFalse(LocalModelRuntime.shouldUnload(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW))
    }
}
