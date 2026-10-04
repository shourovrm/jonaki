package app.jonaki.tools.viewimage

import app.jonaki.core.toolapi.ImageSource
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import app.jonaki.core.toolapi.ViewedImages
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewImageToolTest {
    private val threadFolder: File = Files.createTempDirectory("thread").toFile()
    private val context = ToolContext(threadFolder, OkHttpClient())

    private fun view(vararg arguments: Pair<String, Any>): ToolOutput = runBlocking {
        val json = JsonObject(
            arguments.associate { (key, value) ->
                key to if (value is Int) JsonPrimitive(value) else JsonPrimitive(value.toString())
            },
        )
        ViewImageTool().run(json, context)
    }

    private fun touch(path: String) {
        val file = File(threadFolder, path)
        file.parentFile.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
    }

    @Test
    fun anImageAnswersWithTheTextTheLoopLooksFor() {
        touch("inbox/Photo.JPG")
        val output = view("path" to "inbox/Photo.JPG")

        assertEquals(false, output.isError)
        assertEquals(ImageSource("inbox/Photo.JPG"), ViewedImages.sourceIn(output.text))
    }

    @Test
    fun aPdfPageIsNamedWithItsPage() {
        touch("inbox/scan.pdf")
        val output = view("path" to "inbox/scan.pdf", "page" to "3")

        assertEquals(ImageSource("inbox/scan.pdf", 3), ViewedImages.sourceIn(output.text))
    }

    @Test
    fun otherFilesMissingFilesAndEscapesAreErrors() {
        touch("work/notes.txt")

        assertTrue(view("path" to "work/notes.txt").text.contains("is not an image"))
        assertTrue(view("path" to "work/none.png").text.contains("does not exist"))
        assertTrue(view("path" to "../other/a.png").text.contains("outside the thread folder"))
        assertTrue(view("path" to "work/notes.txt").isError)
    }

    @Test
    fun onlyAnInboxImageIsOutsideContent() {
        fun sourceOf(path: String) = ViewImageTool().outsideContentSourceOf(JsonObject(mapOf("path" to JsonPrimitive(path))))

        assertEquals("Photo.JPG", sourceOf("inbox/Photo.JPG"))
        assertEquals("Photo.JPG", sourceOf("./inbox/Photo.JPG"))
        assertEquals(null, sourceOf("artifacts/chart.png"))
        assertEquals(null, sourceOf("work/inbox/a.png"))
    }
}
