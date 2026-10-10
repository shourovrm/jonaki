package app.jonaki.core.modelcatalog

import java.io.File
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

class OpenRouterImageModelsTest {
    private fun recorded(name: String) = File(System.getProperty("jonaki.testdata"), "openrouter/$name").readText()

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
    fun theRecordedListGivesIdsNamesAndAspectRatios() {
        val models = OpenRouterImageModels.parse(recorded("image-models-trimmed.json"))

        assertTrue(models.size >= 3)
        val flux = models.first { model -> model.id == "black-forest-labs/flux.2-klein-4b" }
        assertEquals("Black Forest Labs: FLUX.2 Klein 4B", flux.name)
        assertTrue("16:9" in flux.aspectRatios)
        assertEquals("Google: Nano Banana 2.1", models.first { it.id == "google/gemini-nano-banana-2.1" }.name)
    }

    @Test
    fun theRecordedEndpointsGiveThePricePerMegapixel() {
        val price = OpenRouterImageModels.parsePrice(recorded("image-model-endpoints-flux.2-klein-4b.json"))!!

        assertEquals(0.014, price.costUsd, 1e-9)
        assertEquals("megapixel", price.unit)
        assertEquals("$0.014 per megapixel", price.describe())
    }

    @Test
    fun theLowestEndpointPriceWins() {
        val json = """{"endpoints":[
            {"pricing":[{"billable":"output_image","unit":"image","cost_usd":0.08}]},
            {"pricing":[{"billable":"input_image","unit":"image","cost_usd":0.001},{"billable":"output_image","unit":"image","cost_usd":0.04}]}
        ]}"""

        assertEquals("$0.04 per image", OpenRouterImageModels.parsePrice(json)!!.describe())
    }

    @Test
    fun badOrEmptyAnswersGiveNothing() {
        assertTrue(OpenRouterImageModels.parse("not json").isEmpty())
        assertTrue(OpenRouterImageModels.parse("""{"data":[{"name":"no id"},"x"]}""").isEmpty())
        assertNull(OpenRouterImageModels.parsePrice("""{"endpoints":[]}"""))
        assertNull(OpenRouterImageModels.parsePrice("""{"endpoints":[{"pricing":[{"billable":"input_image","cost_usd":1}]}]}"""))
        assertNull(OpenRouterImageModels.parsePrice("nope"))
    }

    @Test
    fun fetchListLoadsThroughTheListUrl() = runBlocking {
        server.enqueue(MockResponse().setBody(recorded("image-models-trimmed.json")))

        val result = OpenRouterImageModels.fetchList(OkHttpClient(), server.url("/api/v1").toString())

        assertTrue(result is ImageModelListResult.Loaded)
        assertEquals("/api/v1/images/models", server.takeRequest().path)
    }

    @Test
    fun fetchListReportsAFailureAndAnEmptyList() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))
        val base = server.url("/api/v1").toString()

        assertEquals(ImageModelListResult.Failed("HTTP 503"), OpenRouterImageModels.fetchList(OkHttpClient(), base))
        assertEquals(ImageModelListResult.Failed("the list was empty"), OpenRouterImageModels.fetchList(OkHttpClient(), base))
    }

    @Test
    fun fetchPriceAsksTheModelsEndpointsAndToleratesFailure() = runBlocking {
        server.enqueue(MockResponse().setBody(recorded("image-model-endpoints-flux.2-klein-4b.json")))
        server.enqueue(MockResponse().setResponseCode(404))
        val base = server.url("/api/v1").toString()

        val price = OpenRouterImageModels.fetchPrice(OkHttpClient(), "black-forest-labs/flux.2-klein-4b", base) as ImagePriceResult.Priced
        assertEquals(0.014, price.price.costUsd, 1e-9)
        assertEquals("/api/v1/images/models/black-forest-labs/flux.2-klein-4b/endpoints", server.takeRequest().path)
        assertEquals(ImagePriceResult.Failed, OpenRouterImageModels.fetchPrice(OkHttpClient(), "x/y", base))
    }
}
