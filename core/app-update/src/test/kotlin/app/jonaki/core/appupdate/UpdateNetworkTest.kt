package app.jonaki.core.appupdate

import java.io.File
import java.net.ServerSocket
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateNetworkTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val httpClient = OkHttpClient()

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    private fun checker() = UpdateChecker(httpClient, server.url("/latest").toString())

    private fun releaseJson(tag: String, apkUrl: String) =
        """{"tag_name":"$tag","assets":[{"name":"j.apk","browser_download_url":"$apkUrl"}]}"""

    @Test
    fun `a newer release is reported with its apk`() = runBlocking {
        server.enqueue(MockResponse().setBody(releaseJson("v1.4.10", server.url("/j.apk").toString())))

        val outcome = checker().check(installedVersion = "1.4.9")

        assertTrue(outcome is UpdateCheckOutcome.NewerAvailable)
        assertEquals("1.4.10", (outcome as UpdateCheckOutcome.NewerAvailable).release.versionName)
    }

    @Test
    fun `the same release is reported as up to date`() = runBlocking {
        server.enqueue(MockResponse().setBody(releaseJson("v1.4.4", "https://h/j.apk")))

        assertEquals(UpdateCheckOutcome.UpToDate("1.4.4"), checker().check(installedVersion = "1.4.4"))
    }

    @Test
    fun `an http error carries its status code`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))

        assertEquals(
            UpdateCheckOutcome.Failed(UpdateFailure.HttpStatus(403)),
            checker().check(installedVersion = "1.4.4"),
        )
    }

    @Test
    fun `an unreadable body is an unreadable answer`() = runBlocking {
        server.enqueue(MockResponse().setBody("<html>"))

        assertEquals(
            UpdateCheckOutcome.Failed(UpdateFailure.UnreadableAnswer),
            checker().check(installedVersion = "1.4.4"),
        )
    }

    @Test
    fun `an unreachable server is a network failure`() = runBlocking {
        val closedPort = ServerSocket(0).use { socket -> socket.localPort }
        val unreachable = UpdateChecker(httpClient, "http://localhost:$closedPort/latest")

        assertEquals(UpdateCheckOutcome.Failed(UpdateFailure.NoNetwork), unreachable.check("1.4.4"))
    }

    @Test
    fun `the download is written to the target file with progress`() = runBlocking {
        val content = ByteArray(50_000) { index -> (index % 251).toByte() }
        server.enqueue(MockResponse().setBody(Buffer().write(content)))
        val target = File(temporaryFolder.root, "cache/update.apk")
        val reportedPercents = mutableListOf<Int>()

        val failure = ApkDownloader(httpClient).download(server.url("/j.apk").toString(), target) { percent ->
            reportedPercents.add(percent)
        }

        assertNull(failure)
        assertTrue(content.contentEquals(target.readBytes()))
        assertEquals(100, reportedPercents.last())
    }

    @Test
    fun `a second download overwrites the first`() = runBlocking {
        val target = File(temporaryFolder.root, "update.apk")
        target.writeText("old and longer than the new file")
        server.enqueue(MockResponse().setBody("new"))

        ApkDownloader(httpClient).download(server.url("/j.apk").toString(), target) {}

        assertEquals("new", target.readText())
    }

    @Test
    fun `a failed status leaves a failure and no file`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        val target = File(temporaryFolder.root, "update.apk")

        val failure = ApkDownloader(httpClient).download(server.url("/j.apk").toString(), target) {}

        assertEquals(UpdateFailure.HttpStatus(404), failure)
        assertTrue(!target.exists())
    }

    @Test
    fun `a body cut short is a broken download`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setBody(Buffer().write(ByteArray(10_000)))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )
        val target = File(temporaryFolder.root, "update.apk")

        val failure = ApkDownloader(httpClient).download(server.url("/j.apk").toString(), target) {}

        assertEquals(UpdateFailure.DownloadBroken, failure)
        assertTrue(!target.exists())
    }
}
