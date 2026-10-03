package app.jonaki.core.localmodels

import java.io.File
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

class HuggingFaceClientTest {
    private val server = MockWebServer()
    private lateinit var client: HuggingFaceClient

    private fun recorded(name: String): String = File(System.getProperty("jonaki.testdata"), "huggingface/$name").readText()

    @Before
    fun start() {
        server.start()
        client = HuggingFaceClient(OkHttpClient(), server.url("/").toString().removeSuffix("/"))
    }

    @After
    fun stop() {
        server.shutdown()
    }

    @Test
    fun searchAsksForGgufRepositoriesByDownloadsWithTheirMetadata() = runBlocking {
        server.enqueue(MockResponse().setBody(recorded("search-qwen3.5.json")))
        val repos = client.search("  qwen3.5 ")
        assertEquals(8, repos.size)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/api/models", url.encodedPath)
        assertEquals("qwen3.5", url.queryParameter("search"))
        assertEquals("gguf", url.queryParameter("filter"))
        assertEquals("downloads", url.queryParameter("sort"))
        assertEquals("30", url.queryParameter("limit"))
        assertEquals(listOf("gguf", "gated", "downloads", "cardData"), url.queryParameterValues("expand[]"))
    }

    @Test
    fun filesReadsTheMainBranchTree() = runBlocking {
        server.enqueue(MockResponse().setBody(recorded("tree-unsloth-qwen3.5-0.8b.json")))
        val files = client.files("unsloth/Qwen3.5-0.8B-GGUF")
        assertTrue(files.any { file -> file.path == "Qwen3.5-0.8B-Q4_K_M.gguf" })
        assertEquals("/api/models/unsloth/Qwen3.5-0.8B-GGUF/tree/main", server.takeRequest().path)
    }

    @Test
    fun anErrorStatusFails() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429))
        try {
            client.search("qwen")
            fail("expected a failure")
        } catch (failure: HubRequestFailure) {
            assertEquals("Hugging Face answered 429", failure.message)
        }
    }

    @Test
    fun theDownloadAddressPinsTheCommit() {
        assertEquals(
            "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/f6d5376be1edb4d416d56da11e5397a961aca8ae/Qwen3.5-2B-Q4_0.gguf",
            RecommendedModels.QWEN35_2B.downloadUrl,
        )
    }
}
