package app.jonaki.core.modelcatalog

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class OpenRouterEndpointCacheTest {
    private val recorded = File(System.getProperty("jonaki.testdata"), "openrouter/model-endpoints-glm-5.3-flash.json").readText()
    private val server = MockWebServer()
    private val folder: File = Files.createTempDirectory("endpoint-cache").toFile()
    private var nowMillis = 1_000_000L

    @Before
    fun startServer() = server.start()

    @After
    fun stopServer() {
        server.shutdown()
        folder.deleteRecursively()
    }

    private fun cache() = OpenRouterEndpointCache(OkHttpClient(), folder, server.url("/api/v1").toString(), clock = { nowMillis })

    @Test
    fun aSecondLoadWithinADayMakesNoRequest() = runBlocking {
        server.enqueue(MockResponse().setBody(recorded))

        val first = cache().load("z-ai/glm-5.3-flash")
        // A new instance, as after the app was closed: only the file is shared.
        val second = cache().load("z-ai/glm-5.3-flash")

        assertEquals(32, first.size)
        assertEquals(first, second)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun afterADayTheListIsDownloadedAgain() = runBlocking {
        server.enqueue(MockResponse().setBody(recorded))
        server.enqueue(MockResponse().setBody(recorded))

        cache().load("z-ai/glm-5.3-flash")
        nowMillis += OpenRouterEndpointCache.FRESH_FOR_MILLIS + 1
        cache().load("z-ai/glm-5.3-flash")

        assertEquals(2, server.requestCount)
    }

    @Test
    fun anOldListIsShownWhenTheDownloadFails() = runBlocking {
        server.enqueue(MockResponse().setBody(recorded))
        server.enqueue(MockResponse().setResponseCode(503))

        cache().load("z-ai/glm-5.3-flash")
        nowMillis += OpenRouterEndpointCache.FRESH_FOR_MILLIS + 1
        val stale = cache().load("z-ai/glm-5.3-flash")

        assertEquals(32, stale.size)
    }

    @Test
    fun aFailedDownloadWithNothingSavedIsAFailureAndIsNotSaved() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setBody(recorded))

        try {
            cache().load("z-ai/glm-5.3-flash")
            fail("expected an IOException")
        } catch (expected: IOException) {
            assertTrue(expected.message.orEmpty().contains("503"))
        }
        // The failure left no file behind, so the next load asks again and succeeds.
        assertEquals(32, cache().load("z-ai/glm-5.3-flash").size)
    }

    @Test
    fun anAnswerWithoutProvidersIsNotSaved() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":{"endpoints":[]}}"""))
        server.enqueue(MockResponse().setBody(recorded))

        assertEquals(0, cache().load("z-ai/glm-5.3-flash").size)
        assertEquals(32, cache().load("z-ai/glm-5.3-flash").size)
    }

    @Test
    fun eachModelHasItsOwnFileAndAModelIdCannotLeaveTheFolder() = runBlocking {
        server.enqueue(MockResponse().setBody(recorded))
        server.enqueue(MockResponse().setBody(recorded))

        cache().load("z-ai/glm-5.3-flash")
        cache().load("../../outside/model")

        val saved = folder.walkTopDown().filter { it.isFile }.toList()
        assertEquals(2, saved.size)
        assertTrue(saved.all { file -> file.parentFile == folder })
    }
}
