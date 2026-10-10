package app.jonaki.core.modelcatalog

import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImageModelListTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var server: MockWebServer

    private val body = """{"data":[{"id":"a/b","name":"B","supported_parameters":{"quality":{"type":"enum","values":["low","high"]}}}]}"""

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        server.shutdown()
    }

    private fun list(nowMillis: Long) =
        ImageModelList(File(folder.root, "images.json"), OkHttpClient(), server.url("/api/v1/images/models").toString(), { nowMillis })

    @Test
    fun withNoCacheAndNoDownloadThereAreNoModels() {
        assertTrue(list(1_000L).cachedModels().isEmpty())
    }

    @Test
    fun aDownloadIsSavedAndReadBackWithoutAnotherRequest() = runBlocking {
        server.enqueue(MockResponse().setBody(body))

        val loaded = list(1_000_000L).load() as ImageModelListResult.Loaded
        assertEquals(listOf("low", "high"), loaded.models.single().qualityValues)
        assertEquals(listOf("low", "high"), list(1_000_000L).cachedModels().single().qualityValues)

        val sameDay = list(1_000_000L + 23 * 3_600_000L).load() as ImageModelListResult.Loaded
        assertEquals("a/b", sameDay.models.single().id)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun anOldCacheStillCountsWhenTheDownloadFails() = runBlocking {
        server.enqueue(MockResponse().setBody(body))
        list(1_000_000L).load()
        server.enqueue(MockResponse().setResponseCode(503))

        val later = list(1_000_000L + 25 * 3_600_000L).load()

        assertTrue(later is ImageModelListResult.Loaded)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun withNothingCachedAFailedDownloadIsAFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))

        assertEquals(ImageModelListResult.Failed("HTTP 503"), list(5L).load())
    }
}
