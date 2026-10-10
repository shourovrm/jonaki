package app.jonaki.tools.generatevectorimage

import app.jonaki.core.toolapi.GeneratedImages
import app.jonaki.core.toolapi.ImageFailure
import app.jonaki.core.toolapi.ImageGenerator
import app.jonaki.core.toolapi.ImageOutcome
import app.jonaki.core.toolapi.ImageRequest
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.ToolContext
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateVectorImageToolTest {
    private val threadFolder: File = Files.createTempDirectory("thread").toFile()
    private val context = ToolContext(threadFolder, OkHttpClient())
    private val requests = mutableListOf<ImageRequest>()
    private val models = listOf("openrouter:recraft/recraft-v4-vector", "openrouter:recraft/recraft-v4-pro-vector")

    private val goodSvg = """<?xml version="1.0" encoding="UTF-8"?>
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 256" width="512" height="256"><path d="M0 0 L10 10" fill="#123456"/></svg>"""

    private fun success(svg: String = goodSvg, mediaType: String = "image/svg+xml", cost: Double? = 0.08) =
        ImageOutcome.Success(svg.toByteArray(), mediaType, cost)

    private fun tool(outcome: ImageOutcome, modelKeys: List<String> = models, default: String? = null) =
        GenerateVectorImageTool(ImageGenerator { request -> requests += request; outcome }, modelKeys, default)

    private fun run(tool: GenerateVectorImageTool, json: String) =
        runBlocking { tool.run(Json.parseToJsonElement(json).jsonObject, context) }

    @Test
    fun savesTheSanitisedSvgAndAnswersWithPathSizeModelAndCost() {
        val output = run(tool(success()), """{"prompt":"A firefly logo","aspect_ratio":"1:1","file_name":"firefly-logo"}""")

        assertFalse(output.text, output.isError)
        assertEquals("images/firefly-logo.svg", GeneratedImages.pathIn(output.text))
        assertEquals("Image saved: images/firefly-logo.svg", output.text.lines().first())
        assertTrue(output.text, output.text.contains("Size: 512x256 units, "))
        assertTrue(output.text.contains("Model: openrouter:recraft/recraft-v4-vector"))
        assertTrue(output.text.contains("Cost: $0.0800"))
        assertTrue(File(threadFolder, "images/firefly-logo.svg").readText().contains("M0 0 L10 10"))
        assertEquals(ImageRequest("openrouter", "recraft/recraft-v4-vector", "A firefly logo", "1:1"), requests.single())
    }

    @Test
    fun theSavedFileIsTheSanitisedOneAndTheModelIsToldSo() {
        val hostile = """<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script><circle r="3" onclick="x()"/></svg>"""
        val output = run(tool(success(hostile)), """{"prompt":"x","file_name":"a"}""")

        val saved = File(threadFolder, "images/a.svg").readText()
        assertFalse(saved.contains("script"))
        assertFalse(saved.contains("onclick"))
        assertTrue(saved.contains("circle"))
        assertTrue(output.text, output.text.contains("Safety: 2 unsafe"))
    }

    @Test
    fun theResultNeverHoldsTheSvgText() {
        val output = run(tool(success()), """{"prompt":"x"}""")
        assertFalse(output.text.contains("<svg"))
        assertFalse(output.text.contains("M0 0"))
    }

    @Test
    fun theExtensionIsAlwaysSvgAndNothingIsOverwritten() {
        val tool = tool(success())
        val first = run(tool, """{"prompt":"x","file_name":"logo.png"}""")
        val second = run(tool, """{"prompt":"x","file_name":"logo.svg"}""")

        assertEquals("images/logo.svg", GeneratedImages.pathIn(first.text))
        assertEquals("images/logo (2).svg", GeneratedImages.pathIn(second.text))
    }

    @Test
    fun aMissingNameComesFromThePromptAndAFolderInTheNameIsDropped() {
        val named = run(tool(success()), """{"prompt":"A green leaf icon, flat style"}""")
        val unsafe = run(tool(success()), """{"prompt":"x","file_name":"../../etc/passwd"}""")

        assertEquals("images/a-green-leaf-icon-flat-style.svg", GeneratedImages.pathIn(named.text))
        assertEquals("images/passwd.svg", GeneratedImages.pathIn(unsafe.text))
    }

    @Test
    fun aMissingOrGenericMediaTypeIsFineWhenTheContentIsSvg() {
        for (mediaType in listOf("", "application/octet-stream", "text/xml", "image/svg+xml; charset=utf-8")) {
            val output = run(tool(success(mediaType = mediaType)), """{"prompt":"x"}""")
            assertFalse("$mediaType: ${output.text}", output.isError)
        }
    }

    @Test
    fun aByteOrderMarkBeforeTheXmlDeclarationIsAccepted() {
        val withMark = ImageOutcome.Success(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + goodSvg.toByteArray(), "image/svg+xml", 0.08)
        assertFalse(run(tool(withMark), """{"prompt":"x"}""").isError)
    }

    @Test
    fun anAnswerThatIsNotAnSvgIsRefusedAndSaysTheCharge() {
        val png = ImageOutcome.Success(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2, 3, 0xFF.toByte()), "image/png", 0.08)
        val html = success("<html><body><svg></svg></body></html>", mediaType = "image/svg+xml")
        for (outcome in listOf(png, html)) {
            val output = run(tool(outcome), """{"prompt":"x"}""")
            assertTrue(output.text, output.isError)
            assertTrue(output.text, output.text.contains("not an SVG document"))
            assertTrue(output.text, output.text.contains("charged"))
        }
        assertFalse(File(threadFolder, "images").exists())
    }

    @Test
    fun anSvgOverTwoMegabytesIsRefused() {
        val padding = "<!-- ${"x".repeat(2 * 1024 * 1024)} -->"
        val output = run(tool(success("<svg xmlns=\"http://www.w3.org/2000/svg\">$padding</svg>")), """{"prompt":"x"}""")

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("over the limit of 2 MB"))
        assertTrue(output.text.contains("charged"))
        assertFalse(File(threadFolder, "images").exists())
    }

    @Test
    fun anSvgThatCannotBeMadeSafeIsNotSavedAndSaysTheCharge() {
        val entity = success("""<!DOCTYPE svg [<!ENTITY a "b">]><svg xmlns="http://www.w3.org/2000/svg"><text>&a;</text></svg>""")
        val broken = success("""<svg xmlns="http://www.w3.org/2000/svg"><g></svg>""")
        for (outcome in listOf(entity, broken)) {
            val output = run(tool(outcome), """{"prompt":"x"}""")
            assertTrue(output.text, output.isError)
            assertTrue(output.text, output.text.contains("cannot be made safe"))
            assertTrue(output.text.contains("charged"))
        }
        assertFalse(File(threadFolder, "images").exists())
    }

    @Test
    fun anUnknownCostIsSaidSoAndAMissingSizeToo() {
        val noSize = success("""<svg xmlns="http://www.w3.org/2000/svg"><rect width="1" height="1"/></svg>""", cost = null)
        val output = run(tool(noSize), """{"prompt":"x"}""")

        assertTrue(output.text, output.text.contains("Cost: not reported"))
        assertTrue(output.text, output.text.contains("Size: drawing size not stated, "))
    }

    @Test
    fun theFirstVectorModelIsTheDefaultAndTheGivenDefaultWinsWhenListed() {
        run(tool(success()), """{"prompt":"x"}""")
        assertEquals("recraft/recraft-v4-vector", requests.last().modelId)

        run(tool(success(), default = models[1]), """{"prompt":"x"}""")
        assertEquals("recraft/recraft-v4-pro-vector", requests.last().modelId)

        run(tool(success(), default = "openrouter:black-forest-labs/flux.2-klein-4b"), """{"prompt":"x"}""")
        assertEquals("recraft/recraft-v4-vector", requests.last().modelId)
    }

    @Test
    fun aRasterModelIsRefusedWithTheListOfVectorModels() {
        val output = run(tool(success()), """{"prompt":"x","model":"openrouter:black-forest-labs/flux.2-klein-4b"}""")

        assertTrue(output.isError)
        assertTrue(output.text.contains("is not one of the user's vector models"))
        assertTrue(models.all { key -> output.text.contains(key) })
        assertTrue(output.text.contains("generate_image"))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun withoutAVectorModelTheAnswerSaysWhereToAddOne() {
        val output = run(tool(success(), modelKeys = emptyList()), """{"prompt":"x"}""")

        assertTrue(output.isError)
        assertTrue(output.text.contains("no vector image model is added"))
        assertTrue(output.text.contains("Settings"))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun aMissingPromptIsAnError() {
        val output = run(tool(success()), """{"aspect_ratio":"1:1"}""")
        assertTrue(output.isError)
        assertTrue(output.text.contains("argument prompt is missing"))
    }

    @Test
    fun everyFailureKindSaysWhatToDoNext() {
        val expectations = mapOf(
            ImageFailure.KEY_PROBLEM to "key for openrouter",
            ImageFailure.OUT_OF_CREDIT to "add credit",
            ImageFailure.SERVICE_LIMIT to "nothing was charged",
            ImageFailure.BLOCKED to "refused the prompt",
            ImageFailure.TIMED_OUT to "did not finish in time",
            ImageFailure.NO_IMAGE to "held no picture",
            ImageFailure.OTHER to "failed",
        )
        for ((kind, expected) in expectations) {
            val output = run(tool(ImageOutcome.Failed(kind, "service said no")), """{"prompt":"x"}""")
            assertTrue(output.isError)
            assertTrue("$kind: ${output.text}", output.text.contains(expected))
            assertTrue(output.text.contains("service said no"))
        }
        assertFalse(File(threadFolder, "images").exists())
    }

    @Test
    fun theToolAsksBeforeEveryCallHasTwoMinutesAndAnEnumOfVectorModels() {
        val tool = tool(success())
        assertEquals(SideEffect.CHANGES, tool.sideEffect)
        assertEquals(120, tool.timeLimit.inWholeSeconds)
        assertEquals("generate_vector_image", tool.name)
        val schema = tool.parameterSchema.toString()
        assertTrue(schema.contains("\"required\":[\"prompt\"]"))
        assertTrue(schema.contains("\"openrouter:recraft/recraft-v4-pro-vector\""))
    }

    @Test
    fun anSvgInUtf16IsRefusedAsNotAnSvg() {
        val utf16 = ImageOutcome.Success(goodSvg.toByteArray(Charsets.UTF_16LE), "image/svg+xml", 0.08)
        assertTrue(run(tool(utf16), """{"prompt":"x"}""").isError)
    }
}
