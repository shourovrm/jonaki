package app.jonaki.tools.generateimage

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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateImageToolTest {
    private val threadFolder: File = Files.createTempDirectory("thread").toFile()
    private val context = ToolContext(threadFolder, OkHttpClient())
    private val requests = mutableListOf<ImageRequest>()
    private val models = listOf("openrouter:black-forest-labs/flux.2-klein-4b", "openrouter:openai/gpt-image-1-mini", "gemini:gemini-2.5-flash-image")

    private fun success(cost: Double? = 0.014) =
        ImageOutcome.Success(ImageDimensionsTest.png(1024, 576), "image/png", cost)

    private fun tool(outcome: ImageOutcome, modelKeys: List<String> = models, default: String? = models[1]) =
        GenerateImageTool(ImageGenerator { request -> requests += request; outcome }, modelKeys, default)

    private fun run(tool: GenerateImageTool, json: String) =
        runBlocking { tool.run(Json.parseToJsonElement(json).jsonObject, context) }

    @Test
    fun savesThePictureAndAnswersWithPathSizeModelAndCost() {
        val output = run(tool(success()), """{"prompt":"A blue door","aspect_ratio":"16:9","file_name":"blue-door"}""")

        assertFalse(output.text, output.isError)
        assertEquals("images/blue-door.png", GeneratedImages.pathIn(output.text))
        assertTrue(output.text.contains("Size: 1024x576 px"))
        assertTrue(output.text.contains("Model: openrouter:openai/gpt-image-1-mini"))
        assertTrue(output.text.contains("Cost: $0.0140"))
        assertTrue(File(threadFolder, "images/blue-door.png").isFile)
        assertEquals(ImageRequest("openrouter", "openai/gpt-image-1-mini", "A blue door", "16:9"), requests.single())
    }

    @Test
    fun theResultNeverHoldsBase64() {
        val output = run(tool(success()), """{"prompt":"x"}""")
        assertFalse(output.text.contains("iVBOR"))
        assertTrue(output.text.length < 400)
    }

    @Test
    fun aSecondPictureWithTheSameNameGetsANumber() {
        val tool = tool(success())
        run(tool, """{"prompt":"x","file_name":"a"}""")
        val second = run(tool, """{"prompt":"x","file_name":"a"}""")

        assertEquals("images/a (2).png", GeneratedImages.pathIn(second.text))
    }

    @Test
    fun theExtensionFollowsTheMediaTypeNotAnAssumption() {
        val jpeg = ImageOutcome.Success(ImageDimensionsTest.jpeg(640, 480), "image/jpeg", 0.01)
        val output = run(tool(jpeg), """{"prompt":"x","file_name":"photo.png"}""")

        assertEquals("images/photo.jpg", GeneratedImages.pathIn(output.text))
    }

    @Test
    fun anUnknownCostIsSaidSo() {
        val output = run(tool(success(cost = null)), """{"prompt":"x"}""")
        assertTrue(output.text.contains("Cost: not reported"))
    }

    @Test
    fun aModelNotAddedIsRefusedWithTheListOfAddedOnes() {
        val output = run(tool(success()), """{"prompt":"x","model":"stability/other"}""")

        assertTrue(output.isError)
        assertTrue(output.text.contains("stability/other is not one of the user's image models"))
        assertTrue(models.all { key -> output.text.contains(key) })
        assertTrue(requests.isEmpty())
    }

    @Test
    fun aNamedModelIsUsedAndRoutedToItsService() {
        run(tool(success()), """{"prompt":"x","model":"gemini:gemini-2.5-flash-image"}""")
        assertEquals("gemini", requests.single().serviceKey)
        assertEquals("gemini-2.5-flash-image", requests.single().modelId)
    }

    @Test
    fun aModelIdWithColonsKeepsEverythingAfterTheServiceKey() {
        run(tool(success(), modelKeys = listOf("openrouter:x/y:free"), default = null), """{"prompt":"x"}""")
        assertEquals("openrouter", requests.single().serviceKey)
        assertEquals("x/y:free", requests.single().modelId)
    }

    @Test
    fun aModelWithoutItsServiceMatchesWhenOnlyOneServiceHasIt() {
        run(tool(success()), """{"prompt":"x","model":"gemini-2.5-flash-image"}""")
        assertEquals("gemini", requests.single().serviceKey)
    }

    @Test
    fun aModelWithoutItsServiceIsRefusedWhenTwoServicesHaveIt() {
        val twice = listOf("openrouter:same-id", "gemini:same-id")
        val output = run(tool(success(), modelKeys = twice, default = null), """{"prompt":"x","model":"same-id"}""")

        assertTrue(output.isError)
        assertTrue(output.text.contains("openrouter:same-id") && output.text.contains("gemini:same-id"))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun theSchemaListsTheServiceModelKeys() {
        val schema = tool(success()).parameterSchema.toString()
        assertTrue(schema.contains("\"gemini:gemini-2.5-flash-image\""))
        assertTrue(schema.contains("service:model"))
    }

    @Test
    fun withoutAnAddedModelTheAnswerSaysWhereToAddOne() {
        val output = run(tool(success(), modelKeys = emptyList(), default = null), """{"prompt":"x"}""")

        assertTrue(output.isError)
        assertTrue(output.text.contains("no image model is added"))
        assertTrue(output.text.contains("Settings"))
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
    fun anAnswerThatIsNotAPictureIsNotSaved() {
        val html = ImageOutcome.Success("<html>".toByteArray(), "text/html", 0.01)
        val output = run(tool(html), """{"prompt":"x"}""")

        assertTrue(output.isError)
        assertTrue(output.text.contains("not a png, jpeg or webp"))
    }

    @Test
    fun theToolAsksBeforeEveryCallAndHasAMinuteAndMore() {
        val tool = tool(success())
        assertEquals(SideEffect.CHANGES, tool.sideEffect)
        assertEquals(120, tool.timeLimit.inWholeSeconds)
        assertEquals("generate_image", tool.name)
        val schema: JsonObject = tool.parameterSchema
        assertTrue(schema.toString().contains("\"required\":[\"prompt\"]"))
    }
}
