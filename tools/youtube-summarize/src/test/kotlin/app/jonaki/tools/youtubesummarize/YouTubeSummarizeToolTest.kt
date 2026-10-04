package app.jonaki.tools.youtubesummarize

import app.jonaki.core.toolapi.ToolContext
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeSummarizeToolTest {
    private val context = ToolContext(Files.createTempDirectory("thread").toFile(), OkHttpClient())
    private val requests = mutableListOf<VideoRequest>()
    private var answer: VideoAnswer = VideoAnswer.Success("- point one\n- point two", inputTokens = 105_900)
    private val tool = YouTubeSummarizeTool { request ->
        requests += request
        answer
    }

    private fun arguments(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    @Test
    fun normalisesShortLinksAndUsesTheDefaultPrompt() = runBlocking {
        val output = tool.run(arguments("""{"url":"https://youtu.be/iG9CE55wbtY?si=abc"}"""), context)

        assertFalse(output.isError)
        val request = requests.single()
        assertEquals("https://www.youtube.com/watch?v=iG9CE55wbtY", request.videoUrl)
        assertTrue(request.prompt.isNotBlank())
        assertNull(request.startSeconds)
        assertTrue(output.text.startsWith("Summary of https://www.youtube.com/watch?v=iG9CE55wbtY"))
        assertTrue(output.text.contains("- point one"))
    }

    @Test
    fun acceptsWatchShortsAndMobileLinks() = runBlocking {
        tool.run(arguments("""{"url":"https://m.youtube.com/watch?v=abcdefghijk&t=30s"}"""), context)
        tool.run(arguments("""{"url":"https://www.youtube.com/shorts/abcdefghijk"}"""), context)
        tool.run(arguments("""{"url":"youtube.com/live/abcdefghijk"}"""), context)

        assertEquals(List(3) { "https://www.youtube.com/watch?v=abcdefghijk" }, requests.map { it.videoUrl })
    }

    @Test
    fun passesPromptClipTimesAndResolution() = runBlocking {
        tool.run(
            arguments("""{"url":"https://youtu.be/abcdefghijk","prompt":"List the recipes","start":"1:30","end":"1:02:05","low_resolution":true}"""),
            context,
        )

        val request = requests.single()
        assertEquals("List the recipes", request.prompt)
        assertEquals(90, request.startSeconds)
        assertEquals(3_725, request.endSeconds)
        assertTrue(request.lowMediaResolution)
    }

    @Test
    fun acceptsPlainSeconds() = runBlocking {
        tool.run(arguments("""{"url":"https://youtu.be/abcdefghijk","start":"45"}"""), context)

        assertEquals(45, requests.single().startSeconds)
    }

    @Test
    fun endBeforeStartIsAnError() = runBlocking {
        val output = tool.run(arguments("""{"url":"https://youtu.be/abcdefghijk","start":"10:00","end":"5:00"}"""), context)

        assertTrue(output.isError)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun badTimeIsAnError() = runBlocking {
        val output = tool.run(arguments("""{"url":"https://youtu.be/abcdefghijk","start":"ten"}"""), context)

        assertTrue(output.isError)
        assertTrue(output.text.contains("mm:ss"))
    }

    @Test
    fun nonYouTubeLinkIsAnError() = runBlocking {
        val output = tool.run(arguments("""{"url":"https://vimeo.com/123"}"""), context)

        assertTrue(output.isError)
        assertTrue(output.text.contains("web_fetch"))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun failureFromTheModelIsPassedOnWithAHint() = runBlocking {
        answer = VideoAnswer.Failed("Gemini answered 403 PERMISSION_DENIED: The caller does not have permission", retryable = false)

        val output = tool.run(arguments("""{"url":"https://youtu.be/abcdefghijk"}"""), context)

        assertTrue(output.isError)
        assertTrue(output.text.contains("PERMISSION_DENIED"))
        assertTrue(output.text.contains("private"))
    }

    @Test
    fun overloadSuggestsTryingAgain() = runBlocking {
        answer = VideoAnswer.Failed("Gemini answered 503 UNAVAILABLE: high demand", retryable = true)

        val output = tool.run(arguments("""{"url":"https://youtu.be/abcdefghijk"}"""), context)

        assertTrue(output.isError)
        assertTrue(output.text.contains("again"))
    }

    @Test
    fun theResultIsOutsideContentFromTheLinksHost() {
        assertEquals("youtu.be", tool.outsideContentSourceOf(arguments("""{"url":"https://youtu.be/iG9CE55wbtY"}""")))
    }
}
