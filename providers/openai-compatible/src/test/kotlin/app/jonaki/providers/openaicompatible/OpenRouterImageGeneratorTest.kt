package app.jonaki.providers.openaicompatible

import app.jonaki.core.toolapi.ImageFailure
import app.jonaki.core.toolapi.ImageOutcome
import app.jonaki.core.toolapi.ImageReference
import app.jonaki.core.toolapi.ImageRequest
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenRouterImageGeneratorTest {
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

    private fun recordedAnswer() = File(System.getProperty("jonaki.testdata"), "openrouter/image-answer-flux.2-klein-4b.json").readText()

    private fun generator(key: String? = "sk-or-test", client: OkHttpClient = OkHttpClient()) =
        OpenRouterImageGenerator({ key }, client, server.url("/api/v1").toString())

    private val request = ImageRequest("openrouter", "black-forest-labs/flux.2-klein-4b", "A blue door", "1:1")

    private fun failed(outcome: ImageOutcome): ImageOutcome.Failed = outcome as ImageOutcome.Failed

    @Test
    fun theBodyHoldsModelPromptAndOnlyTheChosenOptions() {
        val full = OpenRouterImageGenerator.requestBody(request)
        assertEquals(
            Json.parseToJsonElement("""{"model":"black-forest-labs/flux.2-klein-4b","prompt":"A blue door","aspect_ratio":"1:1"}"""),
            full,
        )
        val bare = OpenRouterImageGenerator.requestBody(ImageRequest("openrouter", "m/x", "p"))
        assertEquals(setOf("model", "prompt"), bare.keys)
    }

    @Test
    fun qualityResolutionAndReferencesAreSentOnlyWhenSet() {
        val picture = byteArrayOf(1, 2, 3)
        val body = OpenRouterImageGenerator.requestBody(
            ImageRequest(
                "openrouter", "m/x", "p", quality = "high", resolution = "2K",
                references = listOf(ImageReference("images/a.png", "image/png", picture)),
            ),
        )
        assertEquals(
            Json.parseToJsonElement(
                """{"model":"m/x","prompt":"p","quality":"high","resolution":"2K",
                    "input_references":[{"type":"image_url","image_url":{"url":"data:image/png;base64,AQID"}}]}""",
            ),
            body,
        )
        val onlyQuality = OpenRouterImageGenerator.requestBody(ImageRequest("openrouter", "m/x", "p", quality = "high"))
        assertEquals(setOf("model", "prompt", "quality"), onlyQuality.keys)
        val onlyReferences = OpenRouterImageGenerator.requestBody(
            ImageRequest("openrouter", "m/x", "p", references = listOf(ImageReference("a.jpg", "image/jpeg", picture))),
        )
        assertEquals(setOf("model", "prompt", "input_references"), onlyReferences.keys)
    }

    @Test
    fun theRecordedAnswerGivesTheJpegAndItsCost() = runBlocking {
        server.enqueue(MockResponse().setBody(recordedAnswer()))

        val success = generator().generate(request) as ImageOutcome.Success

        assertEquals("image/jpeg", success.mediaType)
        assertEquals(0.014, success.costUsd!!, 1e-9)
        assertEquals(18, success.inputTokens)
        assertEquals(4096, success.outputTokens)
        assertEquals(0xFF.toByte(), success.bytes[0])
        assertEquals(0xD8.toByte(), success.bytes[1])
    }

    @Test
    fun theRequestGoesToImagesWithTheBearerKey() = runBlocking {
        server.enqueue(MockResponse().setBody(recordedAnswer()))

        generator().generate(request)

        val sent = server.takeRequest()
        assertEquals("/api/v1/images", sent.path)
        assertEquals("Bearer sk-or-test", sent.getHeader("Authorization"))
        assertEquals("A blue door", Json.parseToJsonElement(sent.body.readUtf8()).jsonObject["prompt"]!!.toString().trim('"'))
    }

    @Test
    fun noSavedKeyFailsWithoutAnyRequest() = runBlocking {
        val outcome = failed(generator(key = null).generate(request))

        assertEquals(ImageFailure.KEY_PROBLEM, outcome.kind)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun anErrorAnswerReturnsTheServicesText() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(402).setBody("""{"error":{"message":"Insufficient credits","code":402}}"""))

        val outcome = failed(generator().generate(request))

        assertEquals(ImageFailure.OUT_OF_CREDIT, outcome.kind)
        assertEquals("HTTP 402: Insufficient credits", outcome.message)
    }

    @Test
    fun aLimitAtTheCompanyBehindOpenRouterIsNotTheUsersCredit() {
        // Recorded on 2026-10-10 for google/gemini-nano-banana-2.1, whose only provider had used up its own quota.
        val recorded = File("../../testdata/openrouter/image-error-429-upstream-quota.json").readText()

        val outcome = failed(OpenRouterImageGenerator.answerFrom(429, recorded))

        assertEquals(ImageFailure.SERVICE_LIMIT, outcome.kind)
        assertTrue(outcome.message, outcome.message.startsWith("HTTP 429 from Google AI Studio: You exceeded your current quota"))
    }

    @Test
    fun aLimitWithoutAProviderNameIsStillAServiceLimit() {
        val outcome = failed(OpenRouterImageGenerator.answerFrom(429, """{"error":{"message":"Rate limit exceeded","code":429}}"""))

        assertEquals(ImageFailure.SERVICE_LIMIT, outcome.kind)
        assertEquals("HTTP 429: Rate limit exceeded", outcome.message)
    }

    @Test
    fun aRefusedKeyAndABlockedPromptAreTold() {
        assertEquals(
            ImageFailure.KEY_PROBLEM,
            failed(OpenRouterImageGenerator.answerFrom(401, """{"error":{"message":"No auth credentials found","code":401}}""")).kind,
        )
        val blocked = failed(
            OpenRouterImageGenerator.answerFrom(403, """{"error":{"message":"Input was flagged by content moderation","code":403}}"""),
        )
        assertEquals(ImageFailure.BLOCKED, blocked.kind)
        assertTrue(blocked.message.contains("flagged by content moderation"))
    }

    @Test
    fun anAnswerThatIsNotJsonKeepsItsStart() {
        val outcome = failed(OpenRouterImageGenerator.answerFrom(502, "<html>Bad gateway</html>"))

        assertEquals(ImageFailure.OTHER, outcome.kind)
        assertTrue(outcome.message.contains("Bad gateway"))
    }

    @Test
    fun anAnswerWithoutAnImageIsNoImage() {
        val empty = failed(OpenRouterImageGenerator.answerFrom(200, """{"data":[],"usage":{"cost":0.01}}"""))
        assertEquals(ImageFailure.NO_IMAGE, empty.kind)

        val onlyALink = failed(OpenRouterImageGenerator.answerFrom(200, """{"data":[{"url":"https://x/y.png"}]}"""))
        assertEquals(ImageFailure.NO_IMAGE, onlyALink.kind)

        val notJson = failed(OpenRouterImageGenerator.answerFrom(200, "ok"))
        assertEquals(ImageFailure.NO_IMAGE, notJson.kind)
    }

    @Test
    fun anErrorInsideAnOkAnswerIsNotHidden() {
        val outcome = failed(OpenRouterImageGenerator.answerFrom(200, """{"error":{"message":"Provider returned an error","code":502}}"""))
        assertEquals("Provider returned an error", outcome.message)
    }

    @Test
    fun brokenBase64IsNoImage() {
        val outcome = failed(OpenRouterImageGenerator.answerFrom(200, """{"data":[{"b64_json":"@@@@","media_type":"image/png"}]}"""))
        assertEquals(ImageFailure.NO_IMAGE, outcome.kind)
    }

    private val smallSvg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1 1"><path d="M0 0h1v1z"/></svg>"""

    private fun vectorAnswer(imageField: String, mediaType: String? = "image/svg+xml"): String {
        val mediaTypeField = mediaType?.let { ""","media_type":${Json.encodeToString(kotlinx.serialization.serializer<String>(), it)}""" }.orEmpty()
        val encodedField = Json.encodeToString(kotlinx.serialization.serializer<String>(), imageField)
        return """{"data":[{"b64_json":$encodedField$mediaTypeField}],"usage":{"cost":0.08}}"""
    }

    @Test
    fun anSvgSentAsBase64IsDecoded() {
        val base64 = java.util.Base64.getEncoder().encodeToString(smallSvg.toByteArray())
        val success = OpenRouterImageGenerator.answerFrom(200, vectorAnswer(base64)) as ImageOutcome.Success

        assertEquals(smallSvg, String(success.bytes))
        assertEquals("image/svg+xml", success.mediaType)
        assertEquals(0.08, success.costUsd!!, 0.0)
    }

    @Test
    fun anSvgSentAsPlainTextInTheDataFieldIsKeptAsItIs() {
        val success = OpenRouterImageGenerator.answerFrom(200, vectorAnswer("\n  $smallSvg\n")) as ImageOutcome.Success
        assertEquals(smallSvg, String(success.bytes))
    }

    @Test
    fun anSvgSentAsABase64DataUriIsDecoded() {
        val base64 = java.util.Base64.getEncoder().encodeToString(smallSvg.toByteArray())
        val success = OpenRouterImageGenerator.answerFrom(200, vectorAnswer("data:image/svg+xml;base64,$base64")) as ImageOutcome.Success
        assertEquals(smallSvg, String(success.bytes))
    }

    @Test
    fun aMissingMediaTypeOnAnSvgAnswerLeavesTheTypeEmptyForTheToolToJudgeByContent() {
        val base64 = java.util.Base64.getEncoder().encodeToString(smallSvg.toByteArray())
        val success = OpenRouterImageGenerator.answerFrom(200, vectorAnswer(base64, mediaType = null)) as ImageOutcome.Success
        assertEquals("", success.mediaType)
        assertEquals(smallSvg, String(success.bytes))
    }

    @Test
    fun unknownFieldsAndMissingUsageAreTolerated() {
        val body = """{"future":1,"data":[{"b64_json":"/9j/2Q==","media_type":"image/jpeg","revised_prompt":"x"}]}"""
        val success = OpenRouterImageGenerator.answerFrom(200, body) as ImageOutcome.Success
        assertNull(success.costUsd)
        assertEquals(0, success.inputTokens)
    }

    @Test
    fun aServerThatNeverAnswersIsATimeOut() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val quick = OkHttpClient.Builder().readTimeout(200, java.util.concurrent.TimeUnit.MILLISECONDS).build()

        val outcome = failed(generator(client = quick).generate(request))

        assertEquals(ImageFailure.TIMED_OUT, outcome.kind)
    }
}
