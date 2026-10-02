package app.jonaki.providers.openaicompatible

import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.providerapi.ChatRequest
import app.jonaki.core.providerapi.StreamEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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

class ProviderPresetsTest {
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

    @Test
    fun keysAreUniqueAndBaseUrlsHaveNoTrailingSlash() {
        val keys = ProviderPresets.all.map { preset -> preset.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(ProviderPresets.all.none { preset -> preset.baseUrl.endsWith("/") })
    }

    @Test
    fun theNewServicesUseTheirDocumentedEndpoints() {
        assertEquals("https://api.minimax.io/v1", ProviderPresets.miniMax.baseUrl)
        assertEquals("https://dashscope-intl.aliyuncs.com/compatible-mode/v1", ProviderPresets.qwen.baseUrl)
        assertTrue(ProviderPresets.miniMax.listsModels)
        assertTrue(ProviderPresets.qwen.listsModels)
        assertFalse("Z.ai documents no model list", ProviderPresets.glm.listsModels)
    }

    @Test
    fun miniMaxAndQwenStreamThroughTheOpenAiFormat() = runBlocking {
        for (preset in listOf(ProviderPresets.miniMax, ProviderPresets.qwen)) {
            server.enqueue(
                MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                    "data: {\"choices\":[{\"delta\":{\"content\":\"Hi\"},\"finish_reason\":\"stop\"}]}\n\n" +
                        "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":1}}\n\n" +
                        "data: [DONE]\n\n",
                ),
            )
            val provider = OpenAiCompatibleProvider(
                preset = preset,
                apiKey = "key-${preset.key}",
                httpClient = OkHttpClient(),
                baseUrl = server.url("/v1").toString(),
            )

            val events = provider.stream(
                ChatRequest(model = preset.defaultModel, systemPrompt = "You are Jonaki.", messages = listOf(Message(Role.USER, "Hello"))),
            ).toList()

            val text = events.filterIsInstance<StreamEvent.TextDelta>().joinToString("") { delta -> delta.text }
            assertEquals("Hi", text)
            val sent = server.takeRequest()
            assertEquals("/v1/chat/completions", sent.path)
            assertEquals("Bearer key-${preset.key}", sent.getHeader("Authorization"))
            val body = Json.parseToJsonElement(sent.body.readUtf8()).jsonObject
            assertEquals(preset.defaultModel, body["model"]!!.jsonPrimitive.content)
        }
    }
}
