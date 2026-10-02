package app.jonaki.core.skills

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillDownloaderTest {
    private val server = MockWebServer()
    private val responses = mutableMapOf<String, MockResponse>()
    private val requestedPaths = mutableListOf<String>()

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                synchronized(requestedPaths) { requestedPaths += path }
                return responses[path] ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    private fun downloader() = SkillDownloader(OkHttpClient(), gitHubApiBaseUrl = server.url("/api").toString().removeSuffix("/"))

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun fileEntry(name: String, path: String) =
        """{"type":"file","name":"$name","path":"$path","size":10,"download_url":"${server.url("/raw/$path")}"}"""

    private fun dirEntry(name: String, path: String) = """{"type":"dir","name":"$name","path":"$path","download_url":null}"""

    @Test
    fun downloadsAGitHubFolderWithItsSubfolders() {
        responses["/api/repos/o/r/contents/skills/report?ref=main"] = json(
            "[" + fileEntry("SKILL.md", "skills/report/SKILL.md") + "," + dirEntry("templates", "skills/report/templates") + "]",
        )
        responses["/api/repos/o/r/contents/skills/report/templates?ref=main"] =
            json("[" + fileEntry("page.html", "skills/report/templates/page.html") + "]")
        responses["/raw/skills/report/SKILL.md"] = MockResponse().setBody("---\nname: report\n---\n")
        responses["/raw/skills/report/templates/page.html"] = MockResponse().setBody("<html></html>")

        val result = runBlocking { downloader().download(SkillSource.GitHubFolder("o", "r", "main", "skills/report")) }

        result as SkillFilesResult.Files
        assertEquals(setOf("SKILL.md", "templates/page.html"), result.files.keys)
        assertEquals("<html></html>", result.files.getValue("templates/page.html").decodeToString())
    }

    @Test
    fun rootFolderOnTheDefaultBranchHasNoRefOrPath() {
        responses["/api/repos/o/r/contents/"] = json("[" + fileEntry("SKILL.md", "SKILL.md") + "]")
        responses["/raw/SKILL.md"] = MockResponse().setBody("x")

        val result = runBlocking { downloader().download(SkillSource.GitHubFolder("o", "r", null, "")) }

        assertEquals(setOf("SKILL.md"), (result as SkillFilesResult.Files).files.keys)
    }

    @Test
    fun aSingleFileBecomesSkillMd() {
        responses["/letter.md"] = MockResponse().setBody("---\nname: letter\n---\n")

        val result = runBlocking { downloader().download(SkillSource.SingleFile(server.url("/letter.md").toString())) }

        assertEquals("---\nname: letter\n---\n", (result as SkillFilesResult.Files).files.getValue("SKILL.md").decodeToString())
    }

    @Test
    fun failuresSayWhatWentWrong() {
        responses["/api/repos/o/r/contents/limited"] = MockResponse().setResponseCode(403).setHeader("X-RateLimit-Remaining", "0")
        responses["/api/repos/o/r/contents/file"] = json(fileEntry("SKILL.md", "file"))

        val notFound = runBlocking { downloader().download(SkillSource.GitHubFolder("o", "r", null, "missing")) }
        val limited = runBlocking { downloader().download(SkillSource.GitHubFolder("o", "r", null, "limited")) }
        val notAFolder = runBlocking { downloader().download(SkillSource.GitHubFolder("o", "r", null, "file")) }
        val serverError = runBlocking {
            responses["/broken.md"] = MockResponse().setResponseCode(500)
            downloader().download(SkillSource.SingleFile(server.url("/broken.md").toString()))
        }

        assertEquals(SkillFilesResult.Failed("not found on GitHub (404)"), notFound)
        assertEquals(SkillFilesResult.Failed("GitHub's hourly download limit is used up; try again later"), limited)
        assertEquals(SkillFilesResult.Failed("the link is a file, not a folder"), notAFolder)
        assertEquals(SkillFilesResult.Failed("the server answered 500"), serverError)
    }

    @Test
    fun tooManyFilesIsRefusedBeforeDownloadingThem() {
        val entries = (1..101).joinToString(",") { number -> fileEntry("f$number.txt", "big/f$number.txt") }
        responses["/api/repos/o/r/contents/big"] = json("[$entries]")

        val result = runBlocking { downloader().download(SkillSource.GitHubFolder("o", "r", null, "big")) }

        assertEquals(SkillFilesResult.Failed("the folder has more than 100 files"), result)
        assertTrue(requestedPaths.none { path -> path.startsWith("/raw/") })
    }

    @Test
    fun tooLargeIsRefused() {
        responses["/huge.md"] = MockResponse().setBody("x".repeat(SkillDownloader.MAX_TOTAL_BYTES + 1))

        val result = runBlocking { downloader().download(SkillSource.SingleFile(server.url("/huge.md").toString())) }

        assertEquals(SkillFilesResult.Failed("the skill is larger than 2 MB"), result)
    }
}
