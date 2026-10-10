package app.jonaki.core.modelcatalog

import java.io.File
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OpenRouterVideoModelsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var server: MockWebServer

    private val recorded = File(System.getProperty("jonaki.testdata"), "openrouter/video-models-trimmed.json").readText()

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        server.shutdown()
    }

    private fun list(nowMillis: Long, cache: File = File(folder.root, "video-models.json")) =
        VideoModelList(cache, OkHttpClient(), server.url("/api/v1/videos/models").toString(), { nowMillis })

    @Test
    fun theRecordedListGivesDurationsResolutionsAudioAndPriceEntries() {
        val models = OpenRouterVideoModels.parse(recorded).associateBy { it.id }

        val veo = models.getValue("google/veo-3.1-lite")
        assertEquals(listOf(8, 4, 6), veo.supportedDurations)
        assertEquals(listOf("720p", "1080p"), veo.supportedResolutions)
        assertEquals(true, veo.generatesAudio)
        assertEquals("0.05", veo.priceSkus["duration_seconds_with_audio_720p"])

        val hailuo = models.getValue("minimax/hailuo-3-max")
        assertEquals((5..15).toList(), hailuo.supportedDurations)
        assertEquals(false, hailuo.generatesAudio)

        val grok = models.getValue("x-ai/grok-imagine-video-1.5-lite")
        assertNull(grok.generatesAudio)
        assertEquals("3", grok.priceSkus["cents_per_video_output_second_720p"])
        assertTrue("16:9" in grok.supportedAspectRatios!!)

        assertEquals("0.000007", models.getValue("bytedance/seedance-2.0").priceSkus["video_tokens"])
    }

    @Test
    fun aModelWithNullListsKeepsThemNull() {
        val edit = OpenRouterVideoModels.parse(recorded).first { it.id == "black-forest-labs/flux-video-edit" }

        assertNull(edit.supportedDurations)
        assertNull(edit.supportedResolutions)
    }

    @Test
    fun textThatIsNotAListGivesNothing() {
        assertTrue(OpenRouterVideoModels.parse("<html>").isEmpty())
        assertTrue(OpenRouterVideoModels.parse("""{"data":"x"}""").isEmpty())
        assertTrue(OpenRouterVideoModels.parse("""{"data":[{"name":"no id"}]}""").isEmpty())
    }

    @Test
    fun loadDownloadsOnceAndUsesTheCacheForADay() = runBlocking {
        server.enqueue(MockResponse().setBody(recorded))
        list(1_000_000L).load()

        val sameDay = list(1_000_000L + 23 * 3_600_000L).load() as VideoModelListResult.Loaded

        assertEquals(1, server.requestCount)
        assertTrue(sameDay.models.any { it.id == "runway/gen-4.5" })
    }

    @Test
    fun loadDownloadsAgainAfterADay() = runBlocking {
        server.enqueue(MockResponse().setBody(recorded))
        server.enqueue(MockResponse().setBody(recorded))
        list(1_000_000L).load()

        list(1_000_000L + 25 * 3_600_000L).load()

        assertEquals(2, server.requestCount)
    }

    @Test
    fun aFailedDownloadFallsBackToTheOlderCacheAndFailsWithoutOne() = runBlocking {
        server.enqueue(MockResponse().setBody(recorded))
        server.enqueue(MockResponse().setResponseCode(503))
        list(1_000_000L).load()

        val later = list(1_000_000L + 25 * 3_600_000L).load()
        assertTrue(later is VideoModelListResult.Loaded)

        server.enqueue(MockResponse().setResponseCode(503))
        val empty = list(5L, File(folder.root, "other.json")).load() as VideoModelListResult.Failed
        assertEquals("HTTP 503", empty.reason)
    }

    @Test
    fun cachedModelsNeverUseTheNetworkAndAnEmptyAnswerIsNotSaved() = runBlocking {
        val cache = File(folder.root, "video-models.json")
        assertTrue(list(1L, cache).cachedModels().isEmpty())

        server.enqueue(MockResponse().setBody("""{"data":[]}"""))
        val result = list(1L, cache).load()

        assertTrue(result is VideoModelListResult.Failed)
        assertFalse(cache.exists())
        assertEquals(1, server.requestCount)
    }
}
