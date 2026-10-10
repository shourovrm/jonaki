package app.jonaki.providers.openaicompatible

import app.jonaki.core.toolapi.VideoCheckOutcome
import app.jonaki.core.toolapi.VideoDownloadOutcome
import app.jonaki.core.toolapi.VideoFailure
import app.jonaki.core.toolapi.VideoRequest
import app.jonaki.core.toolapi.VideoStartOutcome
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OpenRouterVideoGeneratorTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var server: MockWebServer

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        server.shutdown()
    }

    private fun generator(key: String? = "sk-or-test") =
        OpenRouterVideoGenerator({ key }, OkHttpClient(), server.url("/api/v1").toString())

    private val request = VideoRequest("openrouter", "google/veo-3.1-lite", "A boat at dawn", 4, "720p", "16:9", false)

    @Test
    fun theBodyHoldsModelPromptAndOnlyTheChosenOptions() {
        assertEquals(
            Json.parseToJsonElement(
                """{"model":"google/veo-3.1-lite","prompt":"A boat at dawn","duration":4,"resolution":"720p","aspect_ratio":"16:9","generate_audio":false}""",
            ),
            OpenRouterVideoGenerator.requestBody(request),
        )
        val bare = OpenRouterVideoGenerator.requestBody(VideoRequest("openrouter", "m/x", "p"))
        assertEquals(setOf("model", "prompt"), bare.keys)
    }

    @Test
    fun anAcceptedStartGivesTheJobIdAndSendsTheKeyToVideos() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(202).setBody(
                """{"id":"job-1","polling_url":"https://openrouter.ai/api/v1/videos/job-1","status":"pending"}""",
            ),
        )

        val outcome = generator().start(request)

        assertEquals(VideoStartOutcome.Started("job-1"), outcome)
        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/api/v1/videos", sent.path)
        assertEquals("Bearer sk-or-test", sent.getHeader("Authorization"))
        assertTrue(sent.body.readUtf8().contains("\"prompt\":\"A boat at dawn\""))
    }

    @Test
    fun withoutAKeyNothingIsSent() = runBlocking {
        val outcome = generator(key = null).start(request) as VideoStartOutcome.Failed

        assertEquals(VideoFailure.KEY_PROBLEM, outcome.kind)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun startErrorsNameTheKindAndKeepTheServicesText() {
        val credit = OpenRouterVideoGenerator.startOutcomeFrom(402, """{"error":{"message":"Insufficient credits","code":402}}""") as VideoStartOutcome.Failed
        assertEquals(VideoFailure.OUT_OF_CREDIT, credit.kind)
        assertEquals("HTTP 402: Insufficient credits", credit.message)

        val limit = OpenRouterVideoGenerator.startOutcomeFrom(429, recorded("image-error-429-upstream-quota.json")) as VideoStartOutcome.Failed
        assertEquals(VideoFailure.SERVICE_LIMIT, limit.kind)
        assertTrue(limit.message, limit.message.startsWith("HTTP 429 from Google AI Studio: You exceeded your current quota"))

        val blocked = OpenRouterVideoGenerator.startOutcomeFrom(400, """{"error":{"message":"Prompt was flagged by the safety filter","code":400}}""") as VideoStartOutcome.Failed
        assertEquals(VideoFailure.BLOCKED, blocked.kind)

        val key = OpenRouterVideoGenerator.startOutcomeFrom(401, """{"error":{"message":"No auth credentials found","code":401}}""") as VideoStartOutcome.Failed
        assertEquals(VideoFailure.KEY_PROBLEM, key.kind)
    }

    @Test
    fun aBodyThatIsNotJsonBecomesAFailureWithItsText() {
        val html = OpenRouterVideoGenerator.startOutcomeFrom(502, "<html>Bad gateway</html>") as VideoStartOutcome.Failed
        assertEquals(VideoFailure.OTHER, html.kind)
        assertEquals("HTTP 502: <html>Bad gateway</html>", html.message)

        val garbled = OpenRouterVideoGenerator.startOutcomeFrom(202, "not json") as VideoStartOutcome.Failed
        assertTrue(garbled.message, "not readable JSON" in garbled.message)

        val noId = OpenRouterVideoGenerator.startOutcomeFrom(202, """{"status":"pending"}""") as VideoStartOutcome.Failed
        assertEquals("the answer had no job id", noId.message)

        val checkGarbled = OpenRouterVideoGenerator.checkOutcomeFrom(200, "<html>") as VideoCheckOutcome.Failed
        assertEquals(VideoFailure.OTHER, checkGarbled.kind)
    }

    @Test
    fun aTwoHundredWithAnErrorObjectIsAFailure() {
        val outcome = OpenRouterVideoGenerator.startOutcomeFrom(200, """{"error":{"message":"Provider returned an error","code":502}}""") as VideoStartOutcome.Failed
        assertEquals("Provider returned an error", outcome.message)
    }

    @Test
    fun pendingAndInProgressAreStillWorking() {
        assertEquals(VideoCheckOutcome.Working("pending"), OpenRouterVideoGenerator.checkOutcomeFrom(200, """{"id":"j","status":"pending"}"""))
        assertEquals(VideoCheckOutcome.Working("in_progress"), OpenRouterVideoGenerator.checkOutcomeFrom(200, """{"id":"j","status":"in_progress"}"""))
    }

    @Test
    fun completedGivesTheLinksAsWrittenAndTheCost() {
        val relative = OpenRouterVideoGenerator.checkOutcomeFrom(
            200,
            """{"id":"j","status":"completed","unsigned_urls":["/api/v1/videos/j/content?index=0"],"usage":{"cost":0.4}}""",
        ) as VideoCheckOutcome.Completed
        assertEquals(listOf("/api/v1/videos/j/content?index=0"), relative.contentUrls)
        assertEquals(0.4, relative.costUsd!!, 1e-9)

        val full = OpenRouterVideoGenerator.checkOutcomeFrom(
            200,
            """{"id":"j","status":"completed","unsigned_urls":["https://openrouter.ai/api/v1/videos/j/content?index=0"]}""",
        ) as VideoCheckOutcome.Completed
        assertEquals(listOf("https://openrouter.ai/api/v1/videos/j/content?index=0"), full.contentUrls)
        assertNull(full.costUsd)
    }

    @Test
    fun completedWithoutLinksIsAnEndedJob() {
        val outcome = OpenRouterVideoGenerator.checkOutcomeFrom(200, """{"id":"j","status":"completed","unsigned_urls":[]}""")
        assertTrue(outcome is VideoCheckOutcome.JobEnded)
    }

    @Test
    fun failedCancelledAndExpiredJobsEndWithTheServicesReason() {
        val failed = OpenRouterVideoGenerator.checkOutcomeFrom(200, """{"id":"j","status":"failed","error":"Content policy violation"}""") as VideoCheckOutcome.JobEnded
        assertEquals("failed", failed.status)
        assertEquals("Content policy violation", failed.reason)

        val objectError = OpenRouterVideoGenerator.checkOutcomeFrom(200, """{"status":"failed","error":{"message":"Model overloaded"}}""") as VideoCheckOutcome.JobEnded
        assertEquals("Model overloaded", objectError.reason)

        val expired = OpenRouterVideoGenerator.checkOutcomeFrom(200, """{"status":"expired"}""") as VideoCheckOutcome.JobEnded
        assertEquals("expired", expired.status)
        assertEquals("the service gave no reason", expired.reason)

        assertTrue(OpenRouterVideoGenerator.checkOutcomeFrom(200, """{"status":"cancelled"}""") is VideoCheckOutcome.JobEnded)
    }

    @Test
    fun anUnknownStatusIsTreatedAsStillWorking() {
        assertEquals(VideoCheckOutcome.Working("queued"), OpenRouterVideoGenerator.checkOutcomeFrom(200, """{"status":"queued"}"""))
    }

    @Test
    fun aPollAsksTheJobsOwnAddressWithTheKey() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":"job-1","status":"in_progress"}"""))

        val outcome = generator().check("openrouter", "job-1")

        assertEquals(VideoCheckOutcome.Working("in_progress"), outcome)
        val sent = server.takeRequest()
        assertEquals("GET", sent.method)
        assertEquals("/api/v1/videos/job-1", sent.path)
        assertEquals("Bearer sk-or-test", sent.getHeader("Authorization"))
    }

    @Test
    fun aPollRefusedWith429IsAServiceLimit() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody(recorded("image-error-429-upstream-quota.json")))

        val outcome = generator().check("openrouter", "job-1") as VideoCheckOutcome.Failed

        assertEquals(VideoFailure.SERVICE_LIMIT, outcome.kind)
    }

    @Test
    fun aRelativeLinkIsDownloadedFromTheServicesHostWithTheKey() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "video/mp4").setBody(Buffer().write(ByteArray(200_000) { 7 })))
        val target = File(folder.root, "out.part")

        val outcome = generator().download("openrouter", "/api/v1/videos/job-1/content?index=0", target)

        assertEquals(VideoDownloadOutcome.Saved("video/mp4", 200_000L), outcome)
        assertEquals(200_000L, target.length())
        val sent = server.takeRequest()
        assertEquals("/api/v1/videos/job-1/content?index=0", sent.path)
        assertEquals("Bearer sk-or-test", sent.getHeader("Authorization"))
    }

    @Test
    fun aFullLinkOnTheSameHostIsDownloadedToo() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "video/webm; codecs=vp9").setBody("data"))
        val target = File(folder.root, "out.part")

        val outcome = generator().download("openrouter", server.url("/api/v1/videos/job-1/content?index=0").toString(), target)

        assertEquals(VideoDownloadOutcome.Saved("video/webm", 4L), outcome)
    }

    @Test
    fun aFullLinkToAnotherHostNeverGetsTheKey() {
        val url = OpenRouterVideoGenerator.resolveContentUrl("https://openrouter.ai/api/v1", "https://cdn.example.com/v.mp4")!!
        assertEquals("cdn.example.com", url.host)
    }

    @Test
    fun aRefusedDownloadLeavesNoFileAndKeepsTheServicesText() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":{"message":"Video not found","code":404}}"""))
        val target = File(folder.root, "out.part")

        val outcome = generator().download("openrouter", "/api/v1/videos/job-1/content?index=0", target) as VideoDownloadOutcome.Failed

        assertEquals("HTTP 404: Video not found", outcome.message)
        assertFalse(target.exists())
    }

    private fun recorded(name: String) = File(System.getProperty("jonaki.testdata"), "openrouter/$name").readText()
}
