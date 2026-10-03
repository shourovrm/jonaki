package app.jonaki.core.localmodels

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ModelDownloaderTest {
    private val server = MockWebServer()
    private val folder: File = Files.createTempDirectory("model-download").toFile()
    private val downloader = ModelDownloader(OkHttpClient())

    /** 300,000 bytes, larger than one 256 KB read, so progress is reported more than once. */
    private val content = ByteArray(300_000) { index -> (index % 251).toByte() }
    private val contentSha256 = sha256(content)

    private val partFile = File(folder, "downloading/model.gguf.part")
    private val targetFile = File(folder, "models/model.gguf")

    @Before
    fun start() {
        server.start()
    }

    @After
    fun stop() {
        server.shutdown()
        folder.deleteRecursively()
    }

    private fun request(expectedSha256: String = contentSha256) = DownloadRequest(
        url = server.url("/repo/resolve/abc/model.gguf").toString(),
        expectedBytes = content.size.toLong(),
        expectedSha256 = expectedSha256,
        partFile = partFile,
        targetFile = targetFile,
    )

    private fun body(bytes: ByteArray): Buffer = Buffer().write(bytes)

    @Test
    fun aWholeDownloadThatMatchesIsMovedIntoPlace() = runBlocking {
        server.enqueue(MockResponse().setBody(body(content)))
        val progress = mutableListOf<Long>()
        val outcome = downloader.download(request()) { downloaded -> progress += downloaded }
        assertEquals(DownloadOutcome.Finished, outcome)
        assertArrayEquals(content, targetFile.readBytes())
        assertFalse(partFile.exists())
        assertEquals(300_000L, progress.last())
        assertNull(server.takeRequest().getHeader("Range"))
    }

    @Test
    fun aPartFileResumesWithARangeAndTheWholeFileIsHashed() = runBlocking {
        partFile.parentFile.mkdirs()
        partFile.writeBytes(content.copyOfRange(0, 120_000))
        server.enqueue(MockResponse().setResponseCode(206).setBody(body(content.copyOfRange(120_000, content.size))))
        val outcome = downloader.download(request()) {}
        assertEquals(DownloadOutcome.Finished, outcome)
        assertEquals("bytes=120000-", server.takeRequest().getHeader("Range"))
        assertArrayEquals(content, targetFile.readBytes())
    }

    @Test
    fun aServerThatIgnoresTheRangeStartsTheFileAgain() = runBlocking {
        partFile.parentFile.mkdirs()
        partFile.writeBytes(content.copyOfRange(0, 120_000))
        server.enqueue(MockResponse().setResponseCode(200).setBody(body(content)))
        assertEquals(DownloadOutcome.Finished, downloader.download(request()) {})
        assertArrayEquals(content, targetFile.readBytes())
    }

    @Test
    fun aWrongHashDeletesTheFile() = runBlocking {
        server.enqueue(MockResponse().setBody(body(content)))
        val outcome = downloader.download(request(expectedSha256 = "0".repeat(64))) {}
        assertEquals(DownloadOutcome.HashMismatch, outcome)
        assertFalse(partFile.exists())
        assertFalse(targetFile.exists())
    }

    @Test
    fun aDroppedConnectionKeepsThePartFileForTheNextAttempt() = runBlocking {
        server.enqueue(MockResponse().setBody(body(content)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        try {
            downloader.download(request()) {}
            fail("expected a network error")
        } catch (expected: IOException) {
            // The next attempt resumes from what arrived.
        }
        assertTrue(partFile.exists())
        assertTrue(partFile.length() in 1 until content.size)
        assertFalse(targetFile.exists())
    }

    @Test
    fun aMissingFileIsRefusedWithoutRetrying() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(DownloadOutcome.Refused(404), downloader.download(request()) {})
    }

    @Test
    fun aCompletePartFileIsCheckedWithoutAskingTheServer() = runBlocking {
        partFile.parentFile.mkdirs()
        partFile.writeBytes(content)
        assertEquals(DownloadOutcome.Finished, downloader.download(request()) {})
        assertEquals(0, server.requestCount)
        assertArrayEquals(content, targetFile.readBytes())
    }

    @Test
    fun aRedirectIsFollowedWithTheRange() = runBlocking {
        partFile.parentFile.mkdirs()
        partFile.writeBytes(content.copyOfRange(0, 1_000))
        val cdn = server.url("/cdn/signed").toString()
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", cdn))
        server.enqueue(MockResponse().setResponseCode(206).setBody(body(content.copyOfRange(1_000, content.size))))
        assertEquals(DownloadOutcome.Finished, downloader.download(request()) {})
        server.takeRequest()
        val followed = server.takeRequest()
        assertEquals("/cdn/signed", followed.path)
        assertEquals("bytes=1000-", followed.getHeader("Range"))
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte -> "%02x".format(byte) }
}
