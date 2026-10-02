package app.jonaki.providers.gemini

import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.providerapi.ChatRequest
import app.jonaki.core.providerapi.FinishReason
import app.jonaki.core.providerapi.StreamEvent
import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GeminiProviderTest {
    private lateinit var server: MockWebServer
    private lateinit var provider: GeminiProvider

    private fun recording(name: String): String =
        File(System.getProperty("jonaki.testdata"), "gemini/$name").readText()

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
        provider = GeminiProvider(apiKey = "test-key", httpClient = OkHttpClient(), baseUrl = server.url("/v1beta").toString())
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun streamsTextFromServerSentEvents() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hi\"}]}}]}\r\n\r\n" +
                    "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"!\"}]},\"finishReason\":\"STOP\"}]," +
                    "\"usageMetadata\":{\"promptTokenCount\":5,\"candidatesTokenCount\":2}}\r\n\r\n",
            ),
        )

        val events = provider.stream(ChatRequest("gemini-3.8-flash", "system", listOf(Message(Role.USER, "hello")))).toList()

        assertEquals(listOf(StreamEvent.TextDelta("Hi"), StreamEvent.TextDelta("!")), events.take(2))
        assertEquals(FinishReason.STOP, (events[2] as StreamEvent.Finished).reason)
        val recorded = server.takeRequest()
        assertEquals("/v1beta/models/gemini-3.8-flash:streamGenerateContent?alt=sse", recorded.path)
        assertEquals("test-key", recorded.getHeader("x-goog-api-key"))
    }

    @Test
    fun retiredModelFailsWithTheServiceMessage() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody(recording("error-404-model-retired.json")))

        val failure = provider.stream(ChatRequest("gemini-2.5-flash", "system", listOf(Message(Role.USER, "hi")))).toList().single() as StreamEvent.Failed

        assertFalse(failure.retryable)
        assertTrue(failure.message, failure.message.contains("404 NOT_FOUND"))
        assertTrue(failure.message.contains("gemini-3.8-flash"))
    }

    @Test
    fun videoSummaryReturnsTheTextAndUsageOfTheRecording() = runBlocking {
        server.enqueue(MockResponse().setBody(recording("youtube-summary-200.json")))

        val outcome = provider.summarizeVideo(
            VideoSummaryRequest(
                videoUrl = "https://www.youtube.com/watch?v=iG9CE55wbtY",
                prompt = "Summarise this talk.",
                startSeconds = 60,
                endSeconds = 300,
                lowMediaResolution = true,
            ),
        ) as VideoSummaryOutcome.Success

        assertTrue(outcome.text.startsWith("### Summary"))
        assertEquals(105_900, outcome.usage?.inputTokens)
        assertEquals(383 + 414, outcome.usage?.outputTokens)

        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val videoPart = body["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray[0].jsonObject
        assertEquals("https://www.youtube.com/watch?v=iG9CE55wbtY", videoPart["fileData"]!!.jsonObject["fileUri"]!!.jsonPrimitive.content)
        assertEquals("60s", videoPart["videoMetadata"]!!.jsonObject["startOffset"]!!.jsonPrimitive.content)
        assertEquals("MEDIA_RESOLUTION_LOW", body["generationConfig"]!!.jsonObject["mediaResolution"]!!.jsonPrimitive.content)
    }

    @Test
    fun overloadedVideoRequestIsRetryable() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody(recording("error-503-overloaded.json")))

        val outcome = provider.summarizeVideo(VideoSummaryRequest("https://youtu.be/x", "Summarise.")) as VideoSummaryOutcome.Failed

        assertTrue(outcome.retryable)
        assertTrue(outcome.message.contains("high demand"))
    }

    @Test
    fun unavailableVideoIsNotRetryable() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody(recording("error-403-video-unavailable.json")))

        val outcome = provider.summarizeVideo(VideoSummaryRequest("https://youtu.be/missing", "Summarise.")) as VideoSummaryOutcome.Failed

        assertFalse(outcome.retryable)
        assertTrue(outcome.message.contains("PERMISSION_DENIED"))
    }
}
