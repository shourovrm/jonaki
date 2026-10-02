package app.jonaki.tools.artifact

import app.jonaki.core.toolapi.ArtifactVersions
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactToolTest {
    private val threadFolder: File = Files.createTempDirectory("thread").toFile()
    private val context = ToolContext(threadFolder, OkHttpClient())
    private val tool = ArtifactTool()

    private fun show(path: String): ToolOutput = runBlocking {
        tool.run(JsonObject(mapOf("path" to JsonPrimitive(path))), context)
    }

    private fun writeArtifact(path: String, html: String) {
        val file = File(threadFolder, path)
        file.parentFile.mkdirs()
        file.writeText(html)
    }

    @Test
    fun keepingVersionsNeedsNoApproval() {
        assertEquals(SideEffect.CHANGES_APP_DATA, tool.sideEffect)
    }

    @Test
    fun theFirstShowKeepsVersionOne() {
        writeArtifact("artifacts/report.html", "<!doctype html><p>one</p>")

        val output = show("artifacts/report.html")

        assertFalse(output.text, output.isError)
        assertEquals("Showing artifacts/report.html, version 1. The user can open it from the chat.", output.text)
        assertEquals("<!doctype html><p>one</p>", File(threadFolder, "artifacts/.versions/report/v1.html").readText())
    }

    @Test
    fun anUnchangedFileKeepsItsVersion() {
        writeArtifact("artifacts/report.html", "<p>one</p>")
        show("artifacts/report.html")

        val output = show("artifacts/report.html")

        assertTrue(output.text.contains("version 1"))
        assertEquals(1, ArtifactVersions(threadFolder).list("artifacts/report.html").size)
    }

    @Test
    fun anEditedFileGetsTheNextVersion() {
        writeArtifact("artifacts/report.html", "<p>one</p>")
        show("artifacts/report.html")
        writeArtifact("artifacts/report.html", "<p>two</p>")

        val output = show("artifacts/report.html")

        assertTrue(output.text.contains("version 2"))
        val versions = ArtifactVersions(threadFolder).list("artifacts/report.html")
        assertEquals(listOf(1, 2), versions.map { version -> version.number })
        assertEquals("<p>two</p>", versions.last().file.readText())
    }

    @Test
    fun onlyHtmlInsideArtifactsIsShown() {
        writeArtifact("work/notes.html", "<p>x</p>")
        writeArtifact("artifacts/data.csv", "a,b")

        assertTrue(show("work/notes.html").isError)
        assertTrue(show("artifacts/data.csv").isError)
        assertTrue(show("../outside.html").isError)
        assertTrue(show("artifacts/missing.html").isError)
    }

    @Test
    fun webResourcesAreNamedSoTheModelCanFixThem() {
        writeArtifact(
            "artifacts/report.html",
            """<script src="https://cdn.jsdelivr.net/npm/chart.js"></script><link rel="stylesheet" href="http://x.org/a.css"><a href="https://example.com">ok</a>""",
        )

        val output = show("artifacts/report.html")

        assertFalse(output.isError)
        assertTrue(output.text.contains("https://cdn.jsdelivr.net/npm/chart.js"))
        assertTrue(output.text.contains("http://x.org/a.css"))
        // A link the user taps is fine; it opens in the browser.
        assertFalse(output.text.contains("https://example.com"))
        assertTrue(output.text.contains("lib/chart.js"))
    }
}
