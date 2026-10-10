package app.jonaki.core.modelcatalog

import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OpenRouterImagePricesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private var nowMillis = 1_000_000L

    private fun recorded(name: String) = File(System.getProperty("jonaki.testdata"), "openrouter/$name").readText()

    private fun endpointsBody(unit: String, costUsd: Double) =
        """{"endpoints":[{"pricing":[{"billable":"output_image","unit":"$unit","cost_usd":$costUsd}]}]}"""

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        server.shutdown()
    }

    private fun prices(cacheFile: File = File(folder.root, "prices.json"), maxInFlight: Int = 6) =
        OpenRouterImagePrices(
            httpClient = OkHttpClient(),
            cacheFile = cacheFile,
            baseUrl = server.url("/api/v1").toString(),
            clock = { nowMillis },
            maxInFlight = maxInFlight,
        )

    /** Answers each request from a map of model path to body; unknown paths get 404. */
    private fun answerFrom(bodies: Map<String, String>) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val modelId = request.path.orEmpty().removePrefix("/api/v1/images/models/").removeSuffix("/endpoints")
                val body = bodies[modelId] ?: return MockResponse().setResponseCode(404)
                return MockResponse().setBody(body)
            }
        }
    }

    private suspend fun OpenRouterImagePrices.loadAll(modelIds: List<String>): Map<String, ImagePriceResult> {
        val results = mutableMapOf<String, ImagePriceResult>()
        load(modelIds) { modelId, result -> synchronized(results) { results[modelId] = result } }
        return results
    }

    @Test
    fun threeUnitsAreReadAndDescribed() = runBlocking {
        answerFrom(
            mapOf(
                "black-forest-labs/flux.2-klein-4b" to recorded("image-model-endpoints-flux.2-klein-4b.json"),
                "recraft/recraft-v4.1-flash" to recorded("image-model-endpoints-recraft-v4.1-flash.json"),
                "google/gemini-nano-banana-2.1" to recorded("image-model-endpoints-gemini-nano-banana-2.1.json"),
            ),
        )

        val results = prices().loadAll(
            listOf("black-forest-labs/flux.2-klein-4b", "recraft/recraft-v4.1-flash", "google/gemini-nano-banana-2.1"),
        )

        fun described(modelId: String) = (results.getValue(modelId) as ImagePriceResult.Priced).price.describe()
        assertEquals("$0.014 per megapixel", described("black-forest-labs/flux.2-klein-4b"))
        assertEquals("$0.007 per image", described("recraft/recraft-v4.1-flash"))
        assertEquals("$30 per 1M image tokens", described("google/gemini-nano-banana-2.1"))
    }

    @Test
    fun theLowestOfSeveralProvidersIsKept() = runBlocking {
        val body = """{"endpoints":[
            {"pricing":[{"billable":"output_image","unit":"image","cost_usd":0.04}]},
            {"pricing":[{"billable":"output_image","unit":"image","cost_usd":0.02}]},
            {"pricing":[{"billable":"output_image","unit":"image","cost_usd":0.03}]}]}"""
        answerFrom(mapOf("a/b" to body))

        val result = prices().loadAll(listOf("a/b")).getValue("a/b") as ImagePriceResult.Priced

        assertEquals(0.02, result.price.costUsd, 1e-9)
    }

    @Test
    fun noMoreThanSixRequestsAreInFlightAtOnce() = runBlocking {
        val inFlight = AtomicInteger(0)
        val highest = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val now = inFlight.incrementAndGet()
                highest.accumulateAndGet(now) { a, b -> maxOf(a, b) }
                Thread.sleep(150)
                inFlight.decrementAndGet()
                return MockResponse().setBody(endpointsBody("image", 0.01))
            }
        }
        val modelIds = (1..18).map { number -> "a/model-$number" }

        val results = prices().loadAll(modelIds)

        assertEquals(18, results.size)
        assertTrue("saw ${highest.get()} in flight", highest.get() in 2..6)
    }

    @Test
    fun aSecondLoadReadsTheCacheAndMakesNoRequest() = runBlocking {
        answerFrom(mapOf("a/b" to endpointsBody("image", 0.05)))
        val cacheFile = File(folder.root, "prices.json")
        prices(cacheFile).loadAll(listOf("a/b"))
        assertEquals(1, server.requestCount)

        // A new instance, as after the app restarts: only the file carries over.
        val second = prices(cacheFile)
        val results = second.loadAll(listOf("a/b"))

        assertEquals(1, server.requestCount)
        assertEquals("$0.05 per image", (results.getValue("a/b") as ImagePriceResult.Priced).price.describe())
        assertEquals(setOf("a/b"), second.cachedPrices().keys)
    }

    @Test
    fun anEntryOlderThanADayIsFetchedAgain() = runBlocking {
        answerFrom(mapOf("a/b" to endpointsBody("image", 0.05)))
        val cacheFile = File(folder.root, "prices.json")
        prices(cacheFile).loadAll(listOf("a/b"))

        nowMillis += 24 * 60 * 60 * 1000L - 1
        prices(cacheFile).loadAll(listOf("a/b"))
        assertEquals("just under a day is still fresh", 1, server.requestCount)

        nowMillis += 1
        val expired = prices(cacheFile)
        assertTrue(expired.cachedPrices().isEmpty())
        expired.loadAll(listOf("a/b"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun aFailedRequestIsReportedButNotCached() = runBlocking {
        answerFrom(emptyMap())
        val cacheFile = File(folder.root, "prices.json")

        val first = prices(cacheFile).loadAll(listOf("a/b"))
        assertEquals(ImagePriceResult.Failed, first.getValue("a/b"))
        assertTrue(prices(cacheFile).cachedPrices().isEmpty())

        answerFrom(mapOf("a/b" to endpointsBody("image", 0.05)))
        val second = prices(cacheFile).loadAll(listOf("a/b"))
        assertTrue(second.getValue("a/b") is ImagePriceResult.Priced)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun aModelWithoutAnOutputPriceIsCachedAsHavingNone() = runBlocking {
        answerFrom(mapOf("a/b" to """{"endpoints":[]}"""))
        val cacheFile = File(folder.root, "prices.json")

        assertEquals(ImagePriceResult.NoPrice, prices(cacheFile).loadAll(listOf("a/b")).getValue("a/b"))
        assertEquals(ImagePriceResult.NoPrice, prices(cacheFile).loadAll(listOf("a/b")).getValue("a/b"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun cancellingStopsTheRequestsThatHaveNotStarted() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setBody(endpointsBody("image", 0.01)).setBodyDelay(3, java.util.concurrent.TimeUnit.SECONDS)
        }
        val modelIds = (1..20).map { number -> "a/model-$number" }
        val arrived = AtomicInteger(0)

        val loading = launch(start = CoroutineStart.UNDISPATCHED) {
            prices().load(modelIds) { _, _ -> arrived.incrementAndGet() }
        }
        delay(500)
        val startedAt = System.nanoTime()
        loading.cancel()
        loading.join()

        assertTrue("cancel took too long", (System.nanoTime() - startedAt) / 1_000_000 < 1500)
        assertEquals(0, arrived.get())
        assertTrue("sent ${server.requestCount}", server.requestCount <= 6)
        // Nothing may go out after the cancel.
        delay(300)
        assertTrue(server.requestCount <= 6)
    }

    @Test
    fun resultsArriveOneByOneAsTheyFinish() = runBlocking {
        answerFrom(mapOf("a/b" to endpointsBody("image", 0.05), "c/d" to endpointsBody("image", 0.06)))
        val order = mutableListOf<String>()

        val deferred = async { prices().load(listOf("a/b", "c/d")) { modelId, _ -> synchronized(order) { order += modelId } } }
        deferred.await()

        assertEquals(setOf("a/b", "c/d"), order.toSet())
    }
}
