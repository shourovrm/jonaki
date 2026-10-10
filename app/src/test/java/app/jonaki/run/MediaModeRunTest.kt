package app.jonaki.run

import app.jonaki.core.agent.AgentEvent
import app.jonaki.core.agent.DirectToolRun
import app.jonaki.core.agent.InMemoryStepRecorder
import app.jonaki.core.agent.RunOutcome
import app.jonaki.core.toolapi.GeneratedImages
import app.jonaki.core.toolapi.ImageFailure
import app.jonaki.core.toolapi.ImageGenerator
import app.jonaki.core.toolapi.ImageOutcome
import app.jonaki.core.toolapi.ImageRequest
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.tools.generateimage.GenerateImageTool
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A send in picture mode runs the real generate_image tool through [DirectToolRun]:
 * the user's text is the prompt, and no model turn happens.
 */
class MediaModeRunTest {
    private val threadFolder: File = Files.createTempDirectory("thread").toFile()
    private val toolContext = ToolContext(threadFolder = threadFolder, httpClient = OkHttpClient())
    private val recorder = InMemoryStepRecorder()
    private val requests = mutableListOf<ImageRequest>()

    // A one-pixel PNG header is enough: the tool reads the type from the media type and the first bytes.
    private val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(32)

    private fun toolWith(generator: ImageGenerator) = GenerateImageTool(
        generator = generator,
        modelKeys = listOf("openrouter:black-forest-labs/flux.2-klein-4b"),
        defaultModelKey = "openrouter:black-forest-labs/flux.2-klein-4b",
    )

    private val succeeding = ImageGenerator { request ->
        requests += request
        ImageOutcome.Success(pngBytes, "image/png", costUsd = 0.014)
    }

    @Test
    fun theTypedTextIsTheExactPromptAndTheGeneratorRunsOnce() = runBlocking {
        val typed = "  a blue door, at dawn \n"

        val outcome = DirectToolRun(recorder).run(toolWith(succeeding), MediaModeCall.arguments(typed), toolContext, "call-1")

        assertEquals(RunOutcome.Completed(""), outcome)
        assertEquals(1, requests.size)
        assertEquals("a blue door, at dawn", requests.single().prompt)
        assertEquals("black-forest-labs/flux.2-klein-4b", requests.single().modelId)
        val events = recorder.events
        // The loop sends RequestSent before every model request and streams TextDelta from the reply: neither happens.
        assertFalse(events.any { event -> event is AgentEvent.RequestSent || event is AgentEvent.TextDelta })
        val call = (events.first() as AgentEvent.AssistantMessage).message.toolCalls.single()
        assertEquals("""{"prompt":"a blue door, at dawn"}""", call.argumentsJson)
        val result = events.filterIsInstance<AgentEvent.ToolFinished>().single()
        assertFalse(result.output.isError)
        val savedPath = GeneratedImages.pathIn(result.message.text)
        assertTrue(File(threadFolder, savedPath!!).isFile)
    }

    @Test
    fun aFailingGeneratorGivesAnErrorResultAndNoPicture() = runBlocking {
        val failing = ImageGenerator { request ->
            requests += request
            ImageOutcome.Failed(ImageFailure.BLOCKED, "content policy")
        }

        DirectToolRun(recorder).run(toolWith(failing), MediaModeCall.arguments("a blue door"), toolContext, "call-1")

        assertEquals(1, requests.size)
        val result = recorder.events.filterIsInstance<AgentEvent.ToolFinished>().single()
        assertTrue(result.output.isError)
        assertTrue(result.message.text.startsWith("Error: "))
        assertTrue(result.message.text.contains("content policy"))
        // The chat shows a picture only for a result whose first line names a saved file.
        assertNull(GeneratedImages.pathIn(result.message.text))
        assertFalse(File(threadFolder, "images").exists())
    }

    @Test
    fun stopDuringGenerationEndsTheRunAsStoppedWithoutAResult() = runBlocking {
        val generating = CompletableDeferred<Unit>()
        val slow = ImageGenerator { request ->
            requests += request
            generating.complete(Unit)
            delay(60_000)
            ImageOutcome.Success(pngBytes, "image/png", costUsd = null)
        }

        val job = launch {
            DirectToolRun(recorder).run(toolWith(slow), MediaModeCall.arguments("a blue door"), toolContext, "call-1")
        }
        generating.await()
        job.cancel(CancellationException("Stop"))
        job.join()

        val events = recorder.events
        assertEquals(listOf("AssistantMessage", "ToolStarted", "RunFinished"), events.map { event -> event::class.simpleName })
        assertEquals(RunOutcome.Stopped(""), (events.last() as AgentEvent.RunFinished).outcome)
    }
}
