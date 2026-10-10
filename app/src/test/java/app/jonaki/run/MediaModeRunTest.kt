package app.jonaki.run

import app.jonaki.core.agent.AgentEvent
import app.jonaki.core.agent.DirectToolRun
import app.jonaki.core.agent.InMemoryStepRecorder
import app.jonaki.core.agent.RunOutcome
import app.jonaki.core.storage.HistoryMapper
import app.jonaki.core.storage.MessageEntity
import app.jonaki.core.storage.StepEntity
import app.jonaki.core.toolapi.GeneratedImages
import app.jonaki.core.toolapi.ImageFailure
import app.jonaki.core.toolapi.ImageGenerator
import app.jonaki.core.toolapi.ImageOutcome
import app.jonaki.core.toolapi.ImageRequest
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.feature.chat.ChatItem
import app.jonaki.tools.generateimage.GenerateImageTool
import app.jonaki.tools.generatevectorimage.GenerateVectorImageTool
import app.jonaki.ui.ChatItems
import app.jonaki.ui.englishStepWords
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

        val run = DirectToolRun(recorder)
        val outcome = run.run(toolWith(succeeding), MediaModeCall.arguments(typed), toolContext, "call-1")
        assertNull(MediaModeCall.failureText(run.lastOutput))

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

        val run = DirectToolRun(recorder)
        run.run(toolWith(failing), MediaModeCall.arguments("a blue door"), toolContext, "call-1")

        // The reason the chat shows: the tool's text without the mark meant for a chat model.
        val shown = MediaModeCall.failureText(run.lastOutput)
        assertTrue(shown!!.contains("content policy"))
        assertFalse(shown.startsWith("Error"))
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

    private val svgBytes = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"><circle r="3" cx="5" cy="5"/></svg>""".toByteArray()

    private fun vectorToolWith(generator: ImageGenerator) = GenerateVectorImageTool(
        generator = generator,
        modelKeys = listOf("openrouter:recraft/recraft-v4-vector"),
        defaultModelKey = "openrouter:recraft/recraft-v4-vector",
    )

    @Test
    fun aVectorSendSendsTheExactTextOnceAndMakesNoChatRequest() = runBlocking {
        val vectorGenerator = ImageGenerator { request ->
            requests += request
            ImageOutcome.Success(svgBytes, "image/svg+xml", costUsd = 0.08)
        }

        val outcome = DirectToolRun(recorder).run(
            vectorToolWith(vectorGenerator), MediaModeCall.arguments("  a firefly logo \n"), toolContext, "call-1",
        )

        assertEquals(RunOutcome.Completed(""), outcome)
        assertEquals(1, requests.size)
        assertEquals("a firefly logo", requests.single().prompt)
        assertEquals("recraft/recraft-v4-vector", requests.single().modelId)
        val events = recorder.events
        assertFalse(events.any { event -> event is AgentEvent.RequestSent || event is AgentEvent.TextDelta })
        val call = (events.first() as AgentEvent.AssistantMessage).message.toolCalls.single()
        assertEquals("generate_vector_image", call.toolName)
        assertEquals("""{"prompt":"a firefly logo"}""", call.argumentsJson)
        val result = events.filterIsInstance<AgentEvent.ToolFinished>().single()
        assertFalse(result.output.isError)
        assertTrue(File(threadFolder, GeneratedImages.pathIn(result.message.text)!!).isFile)
    }

    @Test
    fun aVectorResultMapsToTheVectorCard() = runBlocking {
        val vectorGenerator = ImageGenerator { ImageOutcome.Success(svgBytes, "image/svg+xml", costUsd = null) }
        DirectToolRun(recorder).run(vectorToolWith(vectorGenerator), MediaModeCall.arguments("a firefly logo"), toolContext, "call-1")
        val result = recorder.events.filterIsInstance<AgentEvent.ToolFinished>().single()
        val call = (recorder.events.first() as AgentEvent.AssistantMessage).message.toolCalls.single()
        val rows = listOf(
            MessageEntity("u", "t", 0, "USER", "a firefly logo", "[]", null, true, 0),
            MessageEntity("a", "t", 1, "ASSISTANT", "", HistoryMapper.toolCallsToJson(listOf(call)), null, true, 0),
            MessageEntity("r", "t", 2, "TOOL", result.message.text, "[]", call.id, true, 0),
        )
        val steps = listOf(StepEntity(call.id, "t", call.toolName, call.argumentsJson, "DONE", result.message.text, 0, 1))

        val items = ChatItems.build(rows, steps, isRunning = false, stepWords = englishStepWords)

        val cards = items.filterIsInstance<ChatItem.GeneratedVectorImage>()
        assertEquals(1, cards.size)
        assertTrue(cards.single().path.endsWith(".svg"))
        assertTrue(items.none { item -> item is ChatItem.GeneratedImage })
    }
}
