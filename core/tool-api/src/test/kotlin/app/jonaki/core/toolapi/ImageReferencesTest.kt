package app.jonaki.core.toolapi

import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageReferencesTest {
    private val threadFolder: File = Files.createTempDirectory("thread").toFile()
    private val paths = ThreadPaths(threadFolder)
    private val modelKey = "openrouter:openai/gpt-image-2.5-sunburst"
    private val takesSixteen = ImageModelFacts(modelKey, maxReferences = 16)
    private val takesTwo = ImageModelFacts(modelKey, maxReferences = 2)
    private val takesNone = ImageModelFacts("openrouter:a/none", maxReferences = 0)
    private val takesFour = ImageModelFacts("openrouter:black-forest-labs/flux.2-klein-4b", maxReferences = 4)

    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 0, 0, 0, 0)
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0)

    private fun file(path: String, bytes: ByteArray): File {
        val file = File(threadFolder, path)
        file.parentFile.mkdirs()
        file.writeBytes(bytes)
        return file
    }

    private fun load(vararg given: String, facts: ImageModelFacts? = takesSixteen, others: List<String> = emptyList(), key: String = modelKey) =
        ImageReferences.load(given.toList(), paths, key, facts, others)

    private fun refusal(result: ReferenceResult): String {
        val refused = result as ReferenceResult.Refused
        assertTrue(refused.output.isError)
        return refused.output.text
    }

    @Test
    fun picturesAreReadWithTheirMediaTypeFromTheFirstBytes() {
        file("images/a.png", png)
        file("inbox/b.dat", jpeg)

        val loaded = load("images/a.png", "inbox/b.dat") as ReferenceResult.Loaded

        assertEquals(listOf("images/a.png", "inbox/b.dat"), loaded.references.map { it.path })
        assertEquals(listOf("image/png", "image/jpeg"), loaded.references.map { it.mediaType })
        assertEquals(png.size, loaded.references[0].bytes.size)
    }

    @Test
    fun noPathsIsNoReferencesWhateverTheModel() {
        assertTrue((load(facts = takesNone, key = takesNone.modelKey) as ReferenceResult.Loaded).references.isEmpty())
        assertTrue((load(facts = null) as ReferenceResult.Loaded).references.isEmpty())
    }

    @Test
    fun aPathOutsideTheThreadFolderIsRefusedWithAHint() {
        file("images/a.png", png)
        for (outside in listOf("../secret.png", "/etc/passwd", "images/../../x.png")) {
            val text = refusal(load(outside))
            assertTrue(text, text.contains("not inside this thread's folder"))
            assertTrue(text, text.contains("relative to the thread folder"))
        }
    }

    @Test
    fun aLinkThatLeadsOutOfTheFolderIsRefused() {
        val outsideFile = Files.createTempFile("outside", ".png").toFile()
        outsideFile.writeBytes(png)
        File(threadFolder, "images").mkdirs()
        Files.createSymbolicLink(File(threadFolder, "images/link.png").toPath(), outsideFile.toPath())

        assertTrue(refusal(load("images/link.png")).contains("not inside this thread's folder"))
    }

    @Test
    fun aMissingFileIsRefusedAndTheHintNamesFindFiles() {
        val text = refusal(load("images/missing.png"))
        assertTrue(text.contains("does not exist"))
        assertTrue(text.contains("find_files"))
    }

    @Test
    fun aFileThatIsNotAPictureIsRefusedWhateverItsEnding() {
        file("inbox/notes.png", "hello".toByteArray())
        assertTrue(refusal(load("inbox/notes.png")).contains("not a png, jpeg or webp picture"))
    }

    @Test
    fun anSvgIsRefusedWithTheVectorHint() {
        file("images/logo.svg", """<?xml version="1.0"?><svg xmlns="http://www.w3.org/2000/svg"/>""".toByteArray())
        file("images/bare.svg", """<svg xmlns="http://www.w3.org/2000/svg"/>""".toByteArray())

        assertTrue(refusal(load("images/logo.svg")).contains("vector file cannot be a reference"))
        assertTrue(refusal(load("images/bare.svg")).contains("SVG"))
    }

    @Test
    fun aFileOverTenMegabytesIsRefusedBeforeItIsRead() {
        val big = file("inbox/big.png", png)
        java.io.RandomAccessFile(big, "rw").use { it.setLength(ImageReferences.MAX_FILE_BYTES + 1) }

        val text = refusal(load("inbox/big.png"))

        assertTrue(text, text.contains("at most 10 MB"))
    }

    @Test
    fun filesTogetherOverTwentyMegabytesAreRefused() {
        for (name in listOf("a", "b", "c")) {
            val big = file("inbox/$name.png", png)
            java.io.RandomAccessFile(big, "rw").use { it.setLength(ImageReferences.MAX_FILE_BYTES - 100) }
        }

        val text = refusal(load("inbox/a.png", "inbox/b.png", "inbox/c.png"))

        assertTrue(text, text.contains("together"))
        assertTrue(text, text.contains("20 MB"))
    }

    @Test
    fun moreThanTheModelsMaximumIsRefusedAndTheMaximumIsNamed() {
        file("images/a.png", png)
        val text = refusal(load("images/a.png", "images/a.png", "images/a.png", facts = takesTwo))
        assertTrue(text, text.contains("takes at most 2"))
    }

    @Test
    fun aModelWithMaximumZeroOrNoRangeRefusesAndNamesAModelThatAccepts() {
        file("images/a.png", png)
        val others = listOf(takesFour.modelKey)

        val text = refusal(load("images/a.png", facts = takesNone, others = others, key = takesNone.modelKey))
        assertTrue(text, text.contains("takes no reference pictures"))
        assertTrue(text, text.contains("openrouter:black-forest-labs/flux.2-klein-4b"))

        val noRange = ImageModelFacts("openrouter:a/norange")
        assertTrue(refusal(load("images/a.png", facts = noRange, key = noRange.modelKey)).contains("takes no reference pictures"))
    }

    @Test
    fun whenNoAddedModelTakesPicturesTheHintSaysWhereToAddOne() {
        file("images/a.png", png)
        val text = refusal(load("images/a.png", facts = takesNone, key = takesNone.modelKey))
        assertTrue(text, text.contains("Settings > Models > Image generation"))
    }

    @Test
    fun withoutAModelListAnOpenRouterModelRefusesButGeminiGoesOn() {
        file("images/a.png", png)

        assertTrue(refusal(load("images/a.png", facts = null)).contains("could not be loaded"))

        val gemini = load("images/a.png", facts = null, key = "gemini:gemini-2.5-flash-image") as ReferenceResult.Loaded
        assertEquals(1, gemini.references.size)
    }

    @Test
    fun aModelThatNeedsPicturesRefusesACallWithFewer() {
        val needsOne = ImageModelFacts("openrouter:recraft/x", minReferences = 1, maxReferences = 5)

        assertTrue(ImageReferences.minimumProblem(0, needsOne.modelKey, needsOne)!!.text.contains("needs at least 1 reference picture,"))
        assertEquals(null, ImageReferences.minimumProblem(1, needsOne.modelKey, needsOne))
        assertEquals(null, ImageReferences.minimumProblem(0, takesFour.modelKey, takesFour))
        assertEquals(null, ImageReferences.minimumProblem(0, takesFour.modelKey, null))
    }

    @Test
    fun theArgumentIsReadAsAListOrASingleTextAndBlanksAreDropped() {
        fun paths(json: String) = ImageReferences.pathsIn(Json.parseToJsonElement(json).jsonObject)

        assertEquals(listOf("images/a.png", "inbox/b.jpg"), paths("""{"reference_images":[" images/a.png ","","inbox/b.jpg"]}"""))
        assertEquals(listOf("images/a.png"), paths("""{"reference_images":"images/a.png"}"""))
        assertTrue(paths("""{"prompt":"x"}""").isEmpty())
        assertTrue(paths("""{"reference_images":[1,{"a":2}]}""").isEmpty())
    }

    @Test
    fun theTextOfARefusalNeverHoldsPictureBytes() {
        file("images/a.png", png)
        val text = refusal(load("images/a.png", "images/a.png", "images/a.png", facts = takesTwo))
        assertFalse(text.contains("base64"))
    }
}
