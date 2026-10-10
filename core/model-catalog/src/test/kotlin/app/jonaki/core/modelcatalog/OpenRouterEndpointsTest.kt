package app.jonaki.core.modelcatalog

import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OpenRouterEndpointsTest {
    private val recorded = File(System.getProperty("jonaki.testdata"), "openrouter/model-endpoints-glm-5.3-flash.json").readText()

    @Test
    fun theRecordedAnswerIsParsedWithEachTagOnce() {
        val endpoints = OpenRouterEndpoints.parse(recorded)

        // 33 entries are recorded; baseten/fp8 is listed twice.
        assertEquals(32, endpoints.size)
        assertEquals(endpoints.map { it.tag }.distinct(), endpoints.map { it.tag })
    }

    @Test
    fun providersAreOrderedByInputPlusOutputPrice() {
        val endpoints = OpenRouterEndpoints.parse(recorded)

        assertEquals("deepinfra/fp4", endpoints.first().tag)
        assertEquals("reka", endpoints.last().tag)
        val prices = endpoints.map { it.combinedUsdPerMillion!! }
        assertEquals(prices.sorted(), prices)
    }

    @Test
    fun fieldsAreReadAndPricesAreDollarsPerMillionTokens() {
        val deepInfra = OpenRouterEndpoints.parse(recorded).first { it.tag == "deepinfra/fp4" }

        assertEquals("DeepInfra", deepInfra.providerName)
        assertEquals("fp4", deepInfra.quantization)
        assertEquals(0.075, deepInfra.inputUsdPerMillion!!, 1e-9)
        assertEquals(0.25, deepInfra.outputUsdPerMillion!!, 1e-9)
        assertEquals(1_048_576, deepInfra.contextLength)
    }

    @Test
    fun unknownQuantizationBecomesNull() {
        assertNull(OpenRouterEndpoints.parse(recorded).first { it.tag == "fireworks" }.quantization)
    }

    @Test
    fun aVariantTagIsShownButAQuantizationSuffixIsNot() {
        val endpoints = OpenRouterEndpoints.parse(recorded)

        assertEquals("parasail/fast", endpoints.first { it.tag == "parasail/fast" }.variantTag)
        assertEquals("fireworks/us", endpoints.first { it.tag == "fireworks/us" }.variantTag)
        assertNull(endpoints.first { it.tag == "deepinfra/fp4" }.variantTag)
        assertNull(endpoints.first { it.tag == "fireworks" }.variantTag)
    }

    @Test
    fun theCheaperEntryOfADuplicatedTagIsKept() {
        val json = """{"data":{"endpoints":[
            {"provider_name":"A","tag":"a","pricing":{"prompt":"0.000002","completion":"0.000002"}},
            {"provider_name":"A","tag":"a","pricing":{"prompt":"0.000001","completion":"0.000001"}}
        ]}}"""

        val endpoints = OpenRouterEndpoints.parse(json)

        assertEquals(1, endpoints.size)
        assertEquals(2.0, endpoints.single().combinedUsdPerMillion!!, 1e-9)
    }

    @Test
    fun providersWithoutAPriceComeLast() {
        val json = """{"data":{"endpoints":[
            {"provider_name":"Free","tag":"free"},
            {"provider_name":"Paid","tag":"paid","pricing":{"prompt":"0.000001","completion":"0.000001"}}
        ]}}"""

        assertEquals(listOf("paid", "free"), OpenRouterEndpoints.parse(json).map { it.tag })
    }

    @Test
    fun chosenTagsFollowThePriceOrderAndDropUnlistedTags() {
        val endpoints = OpenRouterEndpoints.parse(recorded)

        val ordered = OpenRouterEndpoints.chosenTagsInPriceOrder(setOf("fireworks", "deepinfra/fp4", "gone/fp8"), endpoints)

        assertEquals(listOf("deepinfra/fp4", "fireworks"), ordered)
    }

    @Test
    fun textThatIsNotTheExpectedJsonGivesAnEmptyList() {
        assertTrue(OpenRouterEndpoints.parse("not json").isEmpty())
        assertTrue(OpenRouterEndpoints.parse("""{"data":[]}""").isEmpty())
        assertTrue(OpenRouterEndpoints.parse("""{"error":{"message":"x"}}""").isEmpty())
    }

    @Test
    fun fetchAsksForTheModelsEndpointsPath() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody(recorded))

            val endpoints = OpenRouterEndpoints.fetch(OkHttpClient(), "z-ai/glm-5.3-flash", server.url("/api/v1").toString())

            assertEquals(32, endpoints.size)
            assertEquals("/api/v1/models/z-ai/glm-5.3-flash/endpoints", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun fetchFailsWithAnIoExceptionOnAnErrorAnswer() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(404))

            try {
                OpenRouterEndpoints.fetch(OkHttpClient(), "x/y", server.url("/api/v1").toString())
                fail("expected an IOException")
            } catch (expected: IOException) {
                assertTrue(expected.message!!.contains("404"))
            }
        } finally {
            server.shutdown()
        }
    }
}
