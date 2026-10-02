package app.jonaki.feature.artifact

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactRequestsTest {
    private val threadFolder: File = Files.createTempDirectory("thread").toFile().apply {
        File(this, "artifacts/.versions/report").mkdirs()
        File(this, "artifacts/report.html").writeText("<p>now</p>")
        File(this, "artifacts/.versions/report/v1.html").writeText("<p>then</p>")
        File(this, "artifacts/logo.png").writeBytes(byteArrayOf(1, 2, 3))
        File(this, "work").mkdirs()
        File(this, "work/secret.md").writeText("private")
    }
    private val requests = ArtifactRequests(threadFolder)

    @Test
    fun theDocumentIsServedWithAStrictPolicy() {
        val served = requests.resolve("https://artifact.jonaki/artifacts/report.html") as ArtifactResponse.File

        assertEquals("text/html", served.mimeType)
        assertEquals(File(threadFolder, "artifacts/report.html").canonicalFile, served.file)
        val policy = served.headers.getValue("Content-Security-Policy")
        assertTrue(policy.contains("connect-src 'none'"))
        assertTrue(policy.contains("default-src 'self'"))
    }

    @Test
    fun aVersionAndItsImagesAreServed() {
        assertTrue(requests.resolve("https://artifact.jonaki/artifacts/.versions/report/v1.html") is ArtifactResponse.File)
        val image = requests.resolve("https://artifact.jonaki/artifacts/logo.png") as ArtifactResponse.File
        assertEquals("image/png", image.mimeType)
    }

    @Test
    fun chartJsComesFromTheAppNextToAnyPage() {
        assertEquals(ArtifactResponse.ChartLibrary, requests.resolve("https://artifact.jonaki/artifacts/lib/chart.js"))
        assertEquals(ArtifactResponse.ChartLibrary, requests.resolve("https://artifact.jonaki/artifacts/.versions/report/lib/chart.js"))
    }

    @Test
    fun theInternetIsBlocked() {
        assertEquals(ArtifactResponse.Blocked, requests.resolve("https://cdn.jsdelivr.net/npm/chart.js"))
        assertEquals(ArtifactResponse.Blocked, requests.resolve("http://artifact.jonaki/artifacts/report.html"))
    }

    @Test
    fun filesOutsideArtifactsAreBlocked() {
        assertEquals(ArtifactResponse.Blocked, requests.resolve("https://artifact.jonaki/work/secret.md"))
        assertEquals(ArtifactResponse.Blocked, requests.resolve("https://artifact.jonaki/artifacts/../work/secret.md"))
        assertEquals(ArtifactResponse.Blocked, requests.resolve("https://artifact.jonaki/artifacts/%2e%2e/work/secret.md"))
        assertEquals(ArtifactResponse.Blocked, requests.resolve("https://artifact.jonaki/artifacts/missing.html"))
    }

    @Test
    fun theAddressOfAPageIsInsideTheArtifactHost() {
        assertEquals("https://artifact.jonaki/artifacts/report.html", ArtifactRequests.urlOf("artifacts/report.html"))
    }
}
