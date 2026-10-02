package app.jonaki.core.modelcatalog

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ServiceModelListTest {
    private lateinit var server: MockWebServer
    private lateinit var cacheFolder: File

    private val openAiList = """{"object":"list","data":[
        {"id":"gpt-6-luna","object":"model","owned_by":"openai"},
        {"id":"gpt-brand-new","object":"model","owned_by":"openai"},
        {"id":"text-embedding-3-small","object":"model","owned_by":"openai"},
        {"id":"tts-1-hd","object":"model","owned_by":"openai"},
        {"id":"whisper-1","object":"model","owned_by":"openai"},
        {"id":"gpt-image-2","object":"model","owned_by":"openai"},
        {"id":"omni-moderation-latest","object":"model","owned_by":"openai"}
    ]}"""

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        cacheFolder = Files.createTempDirectory("catalog").toFile()
    }

    @After
    fun stop() {
        server.shutdown()
        cacheFolder.deleteRecursively()
    }

    private fun catalog() = ModelCatalog(
        cacheFile = File(cacheFolder, "openrouter-models.json"),
        httpClient = OkHttpClient(),
        openRouterModelsUrl = server.url("/unused").toString(),
        clock = { 1_000_000L },
    )

    @Test
    fun parserKeepsChatModelsAndDropsEmbeddingsSpeechImagesAndModeration() {
        assertEquals(listOf("gpt-6-luna", "gpt-brand-new"), ServiceModelList.parseIds(openAiList))
    }

    @Test
    fun parserReturnsNothingForABodyThatIsNotAList() {
        assertEquals(emptyList<String>(), ServiceModelList.parseIds("""{"error":"no"}"""))
        assertEquals(emptyList<String>(), ServiceModelList.parseIds("not json"))
    }

    @Test
    fun listedModelsJoinTheBuiltInRowsWhichKeepTheirPrices() = runBlocking {
        server.enqueue(MockResponse().setBody(openAiList))
        val catalog = catalog()

        catalog.refreshServiceModels("openai", server.url("/v1").toString(), apiKey = "sk-test")

        val request = server.takeRequest()
        assertEquals("/v1/models", request.path)
        assertEquals("Bearer sk-test", request.getHeader("Authorization"))
        val luna = catalog.find("openai", "gpt-6-luna")!!
        assertEquals(0.10, luna.inputUsdPerMillion!!, 1e-9)
        val newModel = catalog.find("openai", "gpt-brand-new")!!
        assertNull(newModel.inputUsdPerMillion)
        assertEquals("gpt-brand-new", newModel.displayName)
        assertFalse(catalog.models("openai").any { model -> model.modelId == "whisper-1" })
    }

    @Test
    fun aServiceWithoutKeySendsNoAuthorization() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"llama3.2:latest"}]}"""))
        val catalog = catalog()

        catalog.refreshServiceModels("ollama-local", server.url("/v1").toString(), apiKey = null)

        assertNull(server.takeRequest().getHeader("Authorization"))
        assertTrue(catalog.models("ollama-local").any { model -> model.modelId == "llama3.2:latest" })
    }

    @Test
    fun theListSurvivesARestartThroughItsCacheFile() = runBlocking {
        server.enqueue(MockResponse().setBody(openAiList))
        catalog().refreshServiceModels("openai", server.url("/v1").toString(), apiKey = "sk-test")

        val restarted = catalog()

        assertTrue(restarted.models("openai").any { model -> model.modelId == "gpt-brand-new" })
    }

    @Test
    fun aFailedListKeepsTheLastOne() = runBlocking {
        server.enqueue(MockResponse().setBody(openAiList))
        server.enqueue(MockResponse().setResponseCode(401))
        val catalog = catalog()
        catalog.refreshServiceModels("openai", server.url("/v1").toString(), apiKey = "sk-test")

        catalog.refreshServiceModels("openai", server.url("/v1").toString(), apiKey = "sk-wrong")

        assertTrue(catalog.models("openai").any { model -> model.modelId == "gpt-brand-new" })
    }

    @Test
    fun newServicesHaveBuiltInRowsWithTheirOwnPrices() {
        val catalog = catalog()
        val minimax = catalog.find("minimax", "MiniMax-M3")!!
        val qwen = catalog.find("qwen", "qwen3.7-plus")!!

        assertEquals(0.30, minimax.inputUsdPerMillion!!, 1e-9)
        assertEquals(1.20, minimax.outputUsdPerMillion!!, 1e-9)
        assertEquals(0.06, minimax.cachedInputUsdPerMillion!!, 1e-9)
        assertFalse(minimax.isEstimate)
        assertEquals(0.40, qwen.inputUsdPerMillion!!, 1e-9)
        assertEquals(1.60, qwen.outputUsdPerMillion!!, 1e-9)
    }
}
