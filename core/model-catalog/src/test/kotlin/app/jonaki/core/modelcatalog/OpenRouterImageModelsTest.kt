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
    fun qualityResolutionAndReferenceRangeAreReadAsTheModelDeclaresThem() {
        val json = """{"data":[
            {"id":"openai/gpt-image-2.5-sunburst","supported_parameters":{
              "quality":{"type":"enum","values":["auto","low","medium","high","xhigh","max"]},
              "input_references":{"type":"range","min":0,"max":16}}},
            {"id":"google/gemini-nano-banana-2.1","supported_parameters":{
              "resolution":{"type":"enum","values":["1K","2K","4K"]},
              "input_references":{"type":"range","min":0,"max":14}}},
            {"id":"recraft/needs-one","supported_parameters":{"input_references":{"type":"range","min":1,"max":5}}},
            {"id":"a/plain","supported_parameters":{}}]}"""
        val models = OpenRouterImageModels.parse(json).associateBy { model -> model.id }

        val gpt = models.getValue("openai/gpt-image-2.5-sunburst")
        assertEquals(listOf("auto", "low", "medium", "high", "xhigh", "max"), gpt.qualityValues)
        assertNull(gpt.resolutionValues)
        assertEquals(0..16, gpt.referenceRange)
        val banana = models.getValue("google/gemini-nano-banana-2.1")
        assertNull(banana.qualityValues)
        assertEquals(listOf("1K", "2K", "4K"), banana.resolutionValues)
        assertEquals(0..14, banana.referenceRange)
        assertEquals(1..5, models.getValue("recraft/needs-one").referenceRange)
        val plain = models.getValue("a/plain")
        assertNull(plain.qualityValues)
        assertNull(plain.resolutionValues)
        assertNull(plain.referenceRange)
    }

    @Test
    fun onlyAModelWhoseOutputFormatIsExactlySvgIsAVectorModel() {
        val models = OpenRouterImageModels.parse(recorded("image-models-vector-trimmed.json")).associateBy { model -> model.id }

        assertTrue(models.getValue("recraft/recraft-v4.1-vector").isVector)
        assertTrue("1:1" in models.getValue("recraft/recraft-v4.1-vector").aspectRatios)
        // This one lists raster formats in output_format; this one has no output_format at all.
        assertEquals(false, models.getValue("inclusionai/ming-image-0.1-design").isVector)
        assertEquals(false, models.getValue("recraft/recraft-v4.1-flash").isVector)
        assertEquals(false, OpenRouterImageModels.parse(recorded("image-models-trimmed.json")).any { model -> model.isVector })
    }

    @Test
    fun anOutputFormatWithSvgAndOtherFormatsIsNotVector() {
        val json = """{"data":[{"id":"a/b","supported_parameters":{"output_format":{"type":"enum","values":["svg","png"]}}}]}"""
        assertEquals(false, OpenRouterImageModels.parse(json).single().isVector)
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
