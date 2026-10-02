package app.jonaki.core.modelcatalog

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ModelCatalogTest {
    private val recorded = File(System.getProperty("jonaki.testdata"), "openrouter/models-trimmed.json").readText()
    private lateinit var server: MockWebServer
    private lateinit var cacheFolder: File

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

    private fun catalog(nowMillis: Long = 1_000_000L) = ModelCatalog(
        cacheFile = File(cacheFolder, "openrouter-models.json"),
        httpClient = OkHttpClient(),
        openRouterModelsUrl = server.url("/api/v1/models").toString(),
        clock = { nowMillis },
    )

    @Test
    fun parserReadsPricesPerTokenAsPerMillion() {
        val models = OpenRouterModels.parse(recorded)
        val glm = models.first { it.modelId == "z-ai/glm-5.3-flash" }

        assertEquals("openrouter", glm.serviceKey)
        assertEquals("Z.ai: GLM 5.3 Flash", glm.displayName)
        assertEquals(1_048_576, glm.contextWindowTokens)
        assertEquals(0.15, glm.inputUsdPerMillion!!, 1e-9)
        assertEquals(0.50, glm.outputUsdPerMillion!!, 1e-9)
        assertEquals(0.03, glm.cachedInputUsdPerMillion!!, 1e-9)
    }

    @Test
    fun parserTreatsNegativeOrMissingPricesAsUnknown() {
        val models = OpenRouterModels.parse(recorded)

        assertNull(models.first { it.modelId == "openrouter/auto-beta" }.inputUsdPerMillion)
        assertNull(models.first { it.modelId == "deepseek/deepseek-chat" }.cachedInputUsdPerMillion)
    }

    @Test
    fun refreshDownloadsOnceAndThenUsesTheCacheForADay() = runBlocking {
        server.enqueue(MockResponse().setBody(recorded))
        val first = catalog(nowMillis = 1_000_000L)
        first.refreshIfStale()
        val sameDay = catalog(nowMillis = 1_000_000L + 23 * 3_600_000L)
        sameDay.refreshIfStale()

        assertEquals(1, server.requestCount)
        assertEquals(0.15, sameDay.find("openrouter", "z-ai/glm-5.3-flash")!!.inputUsdPerMillion!!, 1e-9)
    }

    @Test
    fun refreshDownloadsAgainAfterADay() = runBlocking {
        server.enqueue(MockResponse().setBody(recorded))
        server.enqueue(MockResponse().setBody(recorded))
        catalog(nowMillis = 1_000_000L).refreshIfStale()

        catalog(nowMillis = 1_000_000L + 25 * 3_600_000L).refreshIfStale()

        assertEquals(2, server.requestCount)
    }

    @Test
    fun aFailedDownloadKeepsTheOldCacheAndDoesNotThrow() = runBlocking {
        server.enqueue(MockResponse().setBody(recorded))
        server.enqueue(MockResponse().setResponseCode(503))
        catalog(nowMillis = 1_000_000L).refreshIfStale()
        val later = catalog(nowMillis = 1_000_000L + 25 * 3_600_000L)

        later.refreshIfStale()

        assertTrue(later.models("openrouter").isNotEmpty())
    }

    @Test
    fun otherServicesComeFromTheBuiltInList() {
        val gemini = catalog().find("gemini", "gemini-3.8-flash")

        assertEquals(1_048_576, gemini!!.contextWindowTokens)
        assertTrue(gemini.isEstimate)
    }

    @Test
    fun searchMatchesIdOrNameIgnoringCase() = runBlocking {
        server.enqueue(MockResponse().setBody(recorded))
        val catalog = catalog()
        catalog.refreshIfStale()

        val found = catalog.search("openrouter", "SONNET")

        assertEquals(listOf("anthropic/claude-sonnet-5.5"), found.map { it.modelId })
    }
}
