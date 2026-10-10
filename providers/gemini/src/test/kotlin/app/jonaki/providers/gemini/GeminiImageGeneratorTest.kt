package app.jonaki.providers.gemini

import app.jonaki.core.toolapi.ImageFailure
import app.jonaki.core.toolapi.ImageOutcome
import app.jonaki.core.toolapi.ImageRequest
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * No real request is made. The bodies below are made up to follow the shape
 * documented at https://ai.google.dev/api/generate-content (checked 2026-10-10):
 * candidates[].content.parts[].inlineData {mimeType, data}, promptFeedback.blockReason,
 * candidates[].finishReason, usageMetadata, and the error object.
 */
class GeminiImageGeneratorTest {
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

    private val pictureBytes = byteArrayOf(1, 2, 3, 4, 5)
    private val pictureBase64 = Base64.getEncoder().encodeToString(pictureBytes)
    private val request = ImageRequest("gemini", "gemini-2.5-flash-image", "A blue door", "16:9")

    private fun generator(key: String? = "AIza-test") =
        GeminiImageGenerator({ key }, OkHttpClient(), server.url("/v1beta").toString())

    private fun failed(outcome: ImageOutcome): ImageOutcome.Failed = outcome as ImageOutcome.Failed

    private fun successBody(extraParts: String = "", usage: String = """"usageMetadata":{"promptTokenCount":7,"candidatesTokenCount":1290,"totalTokenCount":1297}""") =
        """{"candidates":[{"content":{"role":"model","parts":[{"text":"Here is your door."},
            {"inlineData":{"mimeType":"image/png","data":"$pictureBase64"}}$extraParts]},"finishReason":"STOP"}],$usage}"""

    @Test
    fun theBodyHoldsPromptModalitiesAndOnlyTheChosenAspectRatio() {
        val full = GeminiImageGenerator.requestBody(request)
        assertEquals(
            Json.parseToJsonElement(
                """{"contents":[{"parts":[{"text":"A blue door"}]}],
                    "generationConfig":{"responseModalities":["TEXT","IMAGE"],"imageConfig":{"aspectRatio":"16:9"}}}""",
            ),
            full,
        )
        val bare = GeminiImageGenerator.requestBody(ImageRequest("gemini", "m", "p"))
        assertTrue(bare["generationConfig"]!!.jsonObject["imageConfig"] == null)
        assertEquals("TEXT", bare["generationConfig"]!!.jsonObject["responseModalities"]!!.jsonArray[0].jsonPrimitive.content)
    }

    @Test
    fun theRequestGoesToTheModelsGenerateContentPathWithTheKeyHeader() = runBlocking {
        server.enqueue(MockResponse().setBody(successBody()))

        generator().generate(request)

        val recorded = server.takeRequest()
        assertEquals("/v1beta/models/gemini-2.5-flash-image:generateContent", recorded.path)
        assertEquals("AIza-test", recorded.getHeader("x-goog-api-key"))
        assertEquals("POST", recorded.method)
    }

    @Test
    fun aModelIdTypedWithTheModelsPrefixIsNotDoubled() = runBlocking {
        server.enqueue(MockResponse().setBody(successBody()))

        generator().generate(request.copy(modelId = "models/gemini-2.5-flash-image"))

        assertEquals("/v1beta/models/gemini-2.5-flash-image:generateContent", server.takeRequest().path)
    }

    @Test
    fun aPictureAnswerGivesBytesMediaTypeTokensAndNoCost() = runBlocking {
        server.enqueue(MockResponse().setBody(successBody()))

        val success = generator().generate(request) as ImageOutcome.Success

        assertArrayEquals(pictureBytes, success.bytes)
        assertEquals("image/png", success.mediaType)
        assertNull(success.costUsd)
        assertEquals(7, success.inputTokens)
        assertEquals(1290, success.outputTokens)
    }

    @Test
    fun thinkingTokensCountAsOutput() {
        val body = successBody(usage = """"usageMetadata":{"promptTokenCount":7,"candidatesTokenCount":1290,"thoughtsTokenCount":100}""")

        val success = GeminiImageGenerator.answerFrom(200, body) as ImageOutcome.Success

        assertEquals(1390, success.outputTokens)
    }

    @Test
    fun aThoughtPictureIsSkippedAndTheLastRealOneIsUsed() {
        val thought = Base64.getEncoder().encodeToString(byteArrayOf(9, 9))
        val final = Base64.getEncoder().encodeToString(byteArrayOf(7, 7, 7))
        val body = """{"candidates":[{"content":{"parts":[
            {"thought":true,"inlineData":{"mimeType":"image/png","data":"$thought"}},
            {"inlineData":{"mimeType":"image/jpeg","data":"$final"}}]}}]}"""

        val success = GeminiImageGenerator.answerFrom(200, body) as ImageOutcome.Success

        assertArrayEquals(byteArrayOf(7, 7, 7), success.bytes)
        assertEquals("image/jpeg", success.mediaType)
        assertEquals(0, success.inputTokens)
    }

    @Test
    fun snakeCaseFieldsAreReadToo() {
        val body = """{"candidates":[{"content":{"parts":[{"inline_data":{"mime_type":"image/webp","data":"$pictureBase64"}}]}}]}"""

        val success = GeminiImageGenerator.answerFrom(200, body) as ImageOutcome.Success

        assertEquals("image/webp", success.mediaType)
    }

    @Test
    fun aBlockedPromptIsBlockedWithTheReason() {
        val body = """{"promptFeedback":{"blockReason":"PROHIBITED_CONTENT","blockReasonMessage":"Not allowed."}}"""

        val outcome = failed(GeminiImageGenerator.answerFrom(200, body))

        assertEquals(ImageFailure.BLOCKED, outcome.kind)
        assertTrue(outcome.message, outcome.message.contains("PROHIBITED_CONTENT"))
        assertTrue(outcome.message.contains("Not allowed."))
    }

    @Test
    fun aSafetyFinishReasonWithoutPictureIsBlocked() {
        for (reason in listOf("SAFETY", "IMAGE_SAFETY", "IMAGE_PROHIBITED_CONTENT", "PROHIBITED_CONTENT", "BLOCKLIST")) {
            val body = """{"candidates":[{"finishReason":"$reason"}]}"""

            val outcome = failed(GeminiImageGenerator.answerFrom(200, body))

            assertEquals(reason, ImageFailure.BLOCKED, outcome.kind)
            assertTrue(outcome.message.contains(reason))
        }
    }

    @Test
    fun noImageFinishReasonAndTextOnlyAnswersAreNoImage() {
        val noImage = failed(GeminiImageGenerator.answerFrom(200, """{"candidates":[{"finishReason":"NO_IMAGE"}]}"""))
        assertEquals(ImageFailure.NO_IMAGE, noImage.kind)

        val textOnly = failed(
            GeminiImageGenerator.answerFrom(
                200,
                """{"candidates":[{"content":{"parts":[{"text":"I cannot draw that, sorry."}]},"finishReason":"STOP"}]}""",
            ),
        )
        assertEquals(ImageFailure.NO_IMAGE, textOnly.kind)
        assertTrue(textOnly.message.contains("I cannot draw that, sorry."))
    }

    @Test
    fun noCandidatesOrUnreadableBodiesAreNoImageNotACrash() {
        assertEquals(ImageFailure.NO_IMAGE, failed(GeminiImageGenerator.answerFrom(200, "{}")).kind)
        assertEquals(ImageFailure.NO_IMAGE, failed(GeminiImageGenerator.answerFrom(200, "not json")).kind)
        assertEquals(ImageFailure.NO_IMAGE, failed(GeminiImageGenerator.answerFrom(200, """{"candidates":"x"}""")).kind)
        assertEquals(ImageFailure.NO_IMAGE, failed(GeminiImageGenerator.answerFrom(200, """{"candidates":[{"content":{"parts":[{"inlineData":{"data":"%%%"}}]}}]}""")).kind)
    }

    @Test
    fun anErrorObjectGivesItsMessageAndTheRightKind() {
        fun error(code: Int, status: String, message: String) =
            failed(GeminiImageGenerator.answerFrom(code, """{"error":{"code":$code,"message":"$message","status":"$status"}}"""))

        assertEquals(ImageFailure.KEY_PROBLEM, error(400, "INVALID_ARGUMENT", "API key not valid. Please pass a valid API key.").kind)
        assertEquals(ImageFailure.KEY_PROBLEM, error(403, "PERMISSION_DENIED", "The caller does not have permission").kind)
        assertEquals(ImageFailure.OUT_OF_CREDIT, error(429, "RESOURCE_EXHAUSTED", "You exceeded your current quota").kind)
        assertEquals(ImageFailure.TIMED_OUT, error(504, "DEADLINE_EXCEEDED", "Deadline expired").kind)
        assertEquals(ImageFailure.OTHER, error(404, "NOT_FOUND", "models/x is not found").kind)
        val said = error(404, "NOT_FOUND", "models/x is not found")
        assertTrue(said.message.contains("models/x is not found"))
        assertTrue(said.message.contains("HTTP 404"))
    }

    @Test
    fun anErrorStatusWithAnHtmlBodyKeepsTheStartOfTheBody() {
        val outcome = failed(GeminiImageGenerator.answerFrom(502, "<html>Bad gateway</html>"))

        assertEquals(ImageFailure.OTHER, outcome.kind)
        assertTrue(outcome.message.contains("Bad gateway"))
    }

    @Test
    fun anErrorObjectInsideA200IsAFailure() {
        val outcome = failed(GeminiImageGenerator.answerFrom(200, """{"error":{"code":500,"message":"Internal error","status":"INTERNAL"}}"""))

        assertEquals(ImageFailure.OTHER, outcome.kind)
        assertTrue(outcome.message.contains("Internal error"))
    }

    @Test
    fun withoutAKeyNothingIsSent() = runBlocking {
        val outcome = failed(generator(key = null).generate(request))

        assertEquals(ImageFailure.KEY_PROBLEM, outcome.kind)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun aDroppedConnectionIsAFailureNotACrash() = runBlocking {
        server.shutdown()

        val outcome = failed(generator().generate(request))

        assertEquals(ImageFailure.OTHER, outcome.kind)
    }
}
