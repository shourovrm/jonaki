package app.jonaki.providers.openaicompatible

import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.providerapi.ChatRequest
import app.jonaki.core.providerapi.StreamEvent
import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenRouterRoutingTest {
    private val recordedDataPolicyError =
        File(System.getProperty("jonaki.testdata"), "openrouter/error-404-data-policy.json").readText()
    private val request = ChatRequest(model = "m", systemPrompt = "s", messages = listOf(Message(Role.USER, "hi")))
    private val okStream = "data: {\"choices\":[{\"delta\":{\"content\":\"Hi\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"

    private lateinit var server: MockWebServer
    private var fallbacks = 0

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        server.shutdown()
    }

    private fun provider(routing: OpenRouterRouting, preset: ProviderPreset = ProviderPresets.openRouter) =
        OpenAiCompatibleProvider(
            preset = preset,
            apiKey = "k",
            httpClient = OkHttpClient(),
            baseUrl = server.url("/api/v1").toString(),
            openRouterRouting = routing,
            onRoutingFallback = { fallbacks += 1 },
        )

    private fun bodyOf(index: Int = 0): JsonObject {
        repeat(index) { server.takeRequest() }
        return Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
    }

    @Test
    fun privateThenCheapestDeniesDataCollectionAndSortsByPrice() = runBlocking {
        server.enqueue(MockResponse().setBody(okStream))

        provider(OpenRouterRouting.PRIVATE_THEN_CHEAPEST).stream(request).toList()

        assertEquals(
            Json.parseToJsonElement("""{"data_collection":"deny","sort":"price"}"""),
            bodyOf().getValue("provider"),
        )
    }

    @Test
    fun cheapestOnlySortsByPrice() = runBlocking {
        server.enqueue(MockResponse().setBody(okStream))

        provider(OpenRouterRouting.CHEAPEST).stream(request).toList()

        assertEquals(Json.parseToJsonElement("""{"sort":"price"}"""), bodyOf().getValue("provider"))
    }

    @Test
    fun automaticSendsNoProviderBlock() = runBlocking {
        server.enqueue(MockResponse().setBody(okStream))

        provider(OpenRouterRouting.AUTOMATIC).stream(request).toList()

        assertFalse(bodyOf().containsKey("provider"))
    }

    @Test
    fun otherServicesNeverGetAProviderBlock() = runBlocking {
        server.enqueue(MockResponse().setBody(okStream))

        provider(OpenRouterRouting.PRIVATE_THEN_CHEAPEST, preset = ProviderPresets.deepSeek).stream(request).toList()

        assertFalse(bodyOf().containsKey("provider"))
    }

    @Test
    fun theRecordedDataPolicyErrorIsRecognised() {
        assertTrue(OpenRouterRouting.isDataPolicyRejection(404, recordedDataPolicyError))
    }

    @Test
    fun otherErrorsAreNotDataPolicyRejections() {
        assertFalse(OpenRouterRouting.isDataPolicyRejection(429, recordedDataPolicyError))
        assertFalse(OpenRouterRouting.isDataPolicyRejection(404, """{"error":{"message":"Model not found","code":404}}"""))
    }

    @Test
    fun aDataPolicyRejectionIsRetriedOnceAsCheapestAndReported() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody(recordedDataPolicyError))
        server.enqueue(MockResponse().setBody(okStream))

        val events = provider(OpenRouterRouting.PRIVATE_THEN_CHEAPEST).stream(request).toList()

        assertEquals(StreamEvent.TextDelta("Hi"), events.first())
        assertEquals(1, fallbacks)
        assertEquals(Json.parseToJsonElement("""{"sort":"price"}"""), bodyOf(index = 1).getValue("provider"))
    }

    @Test
    fun cheapestRoutingDoesNotRetryADataPolicyError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody(recordedDataPolicyError))

        val events = provider(OpenRouterRouting.CHEAPEST).stream(request).toList()

        assertTrue(events.single() is StreamEvent.Failed)
        assertEquals(0, fallbacks)
        assertEquals(1, server.requestCount)
    }
}
