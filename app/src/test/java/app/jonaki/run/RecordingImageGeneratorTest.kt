package app.jonaki.run

import app.jonaki.core.providerapi.Usage
import app.jonaki.core.toolapi.ImageFailure
import app.jonaki.core.toolapi.ImageGenerator
import app.jonaki.core.toolapi.ImageOutcome
import app.jonaki.core.toolapi.ImageRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingImageGeneratorTest {
    private class Saved(val threadId: String, val modelKey: String, val usage: Usage, val costUsd: Double?)

    private val saved = mutableListOf<Saved>()
    private val request = ImageRequest("openrouter", "black-forest-labs/flux.2-klein-4b", "a door")

    private fun recording(outcome: ImageOutcome) = RecordingImageGenerator(
        generator = ImageGenerator { outcome },
        threadId = "thread-1",
        saveUsage = { threadId, modelKey, usage, costUsd -> saved += Saved(threadId, modelKey, usage, costUsd) },
    )

    @Test
    fun aPaidPictureSavesItsCostUnderTheImageModel() = runBlocking {
        val outcome = ImageOutcome.Success(ByteArray(1), "image/jpeg", costUsd = 0.014, inputTokens = 18, outputTokens = 4096)

        val returned = recording(outcome).generate(request)

        assertTrue(returned === outcome)
        val row = saved.single()
        assertEquals("thread-1", row.threadId)
        assertEquals("openrouter:black-forest-labs/flux.2-klein-4b", row.modelKey)
        assertEquals(0.014, row.costUsd!!, 1e-9)
        assertEquals(18, row.usage.inputTokens)
        assertEquals(4096, row.usage.outputTokens)
    }

    @Test
    fun aFailureOrAnUnpricedPictureSavesNothing() = runBlocking {
        recording(ImageOutcome.Failed(ImageFailure.OTHER, "x")).generate(request)
        recording(ImageOutcome.Success(ByteArray(1), "image/png", costUsd = null)).generate(request)

        assertTrue(saved.isEmpty())
    }
}
