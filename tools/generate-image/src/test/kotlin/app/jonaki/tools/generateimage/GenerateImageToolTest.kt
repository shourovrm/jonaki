package app.jonaki.tools.generateimage

import app.jonaki.core.toolapi.GeneratedImages
import app.jonaki.core.toolapi.ImageFailure
import app.jonaki.core.toolapi.ImageGenerator
import app.jonaki.core.toolapi.ImageModelFacts
import app.jonaki.core.toolapi.ImageQuality
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
    fun theDefaultIsAskedAtEveryCallSoAPickDuringARunCountsForTheNextPicture() {
        var currentDefault: String? = models[0]
        val outcome = success()
        val tool = GenerateImageTool(ImageGenerator { request -> requests += request; outcome }, models, defaultModelKey = { currentDefault })

        run(tool, """{"prompt":"x","file_name":"first"}""")
        currentDefault = models[2]
        run(tool, """{"prompt":"x","file_name":"second"}""")

        assertEquals(listOf("openrouter", "gemini"), requests.map { request -> request.serviceKey })
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
            ImageFailure.SERVICE_LIMIT to "key and credit are fine",
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

    // Quality and reference pictures.

    private val gptKey = "openrouter:openai/gpt-image-2.5-sunburst"
    private val bananaKey = "openrouter:google/gemini-nano-banana-2.1"
    private val fluxKey = "openrouter:black-forest-labs/flux.2-klein-4b"
    private val facts = listOf(
        ImageModelFacts(gptKey, qualityValues = listOf("auto", "low", "medium", "high", "xhigh", "max"), maxReferences = 16),
        ImageModelFacts(bananaKey, resolutionValues = listOf("1K", "2K", "4K"), maxReferences = 14),
        ImageModelFacts(fluxKey, maxReferences = 4),
        ImageModelFacts("openrouter:a/none", maxReferences = 0),
    ).associateBy { it.modelKey }

    private fun toolWithFacts(
        default: String = gptKey,
        defaultQuality: ImageQuality = ImageQuality.STANDARD,
        factsByKey: Map<String, ImageModelFacts> = facts,
    ) = GenerateImageTool(
        ImageGenerator { request -> requests += request; success() },
        listOf(gptKey, bananaKey, fluxKey, "openrouter:a/none"),
        { default },
        factsByKey,
        { defaultQuality },
    )

    private fun picture(path: String): String {
        val file = File(threadFolder, path)
        file.parentFile.mkdirs()
        file.writeBytes(ImageDimensionsTest.png(8, 8))
        return path
    }

    @Test
    fun aCallWithOnlyAPromptSendsNoQualityNoResolutionAndNoReferences() {
        val output = run(toolWithFacts(), """{"prompt":"A blue door"}""")

        assertFalse(output.text, output.isError)
        assertEquals(ImageRequest("openrouter", "openai/gpt-image-2.5-sunburst", "A blue door"), requests.single())
        assertFalse(output.text.contains("Quality"))
        assertFalse(output.text.contains("Reference"))
    }

    @Test
    fun highQualityIsTranslatedForTheModelAndTheResultNamesWhatWasSent() {
        val gpt = run(toolWithFacts(), """{"prompt":"x","quality":"high"}""")
        assertEquals("high", requests.last().quality)
        assertEquals(null, requests.last().resolution)
        assertTrue(gpt.text, gpt.text.contains("Quality: high (sent quality high)"))

        val banana = run(toolWithFacts(), """{"prompt":"x","quality":"high","model":"$bananaKey"}""")
        assertEquals(null, requests.last().quality)
        assertEquals("2K", requests.last().resolution)
        assertTrue(banana.text, banana.text.contains("Quality: high (sent resolution 2K)"))
    }

    @Test
    fun highOnAModelWithoutASettingSendsNothingAndSaysSo() {
        val output = run(toolWithFacts(), """{"prompt":"x","quality":"high","model":"$fluxKey"}""")

        assertEquals(null, requests.last().quality)
        assertEquals(null, requests.last().resolution)
        assertTrue(output.text, output.text.contains("this model has no quality setting"))
    }

    @Test
    fun withoutAModelListHighSendsNothingAndSaysItWasNotChecked() {
        val output = run(toolWithFacts(factsByKey = emptyMap()), """{"prompt":"x","quality":"high"}""")

        assertEquals(null, requests.last().quality)
        assertTrue(output.text, output.text.contains("could not be checked"))
    }

    @Test
    fun theSettingsDefaultCountsWhenTheCallNamesNoQualityAndTheCallWinsOtherwise() {
        run(toolWithFacts(defaultQuality = ImageQuality.HIGH), """{"prompt":"x"}""")
        assertEquals("high", requests.last().quality)

        run(toolWithFacts(defaultQuality = ImageQuality.HIGH), """{"prompt":"x","quality":"standard"}""")
        assertEquals(null, requests.last().quality)
    }

    @Test
    fun anUnknownQualityWordIsAnErrorBeforeAnyRequest() {
        val output = run(toolWithFacts(), """{"prompt":"x","quality":"ultra"}""")

        assertTrue(output.isError)
        assertTrue(output.text.contains("standard or high"))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun referencesAreReadAndPassedToTheGeneratorAndOnlyPathsAppearInTheResult() {
        picture("images/firefly.png")
        picture("inbox/photo.png")

        val output = run(toolWithFacts(), """{"prompt":"make the title bigger","reference_images":["images/firefly.png","inbox/photo.png"]}""")

        assertFalse(output.text, output.isError)
        val sent = requests.single().references
        assertEquals(listOf("images/firefly.png", "inbox/photo.png"), sent.map { it.path })
        assertEquals("image/png", sent[0].mediaType)
        assertTrue(output.text.contains("Reference pictures: images/firefly.png, inbox/photo.png"))
        assertFalse(output.text.contains("iVBOR"))
        assertFalse(output.text.contains("base64"))
        assertTrue(output.text.length < 600)
    }

    @Test
    fun everyReferenceProblemIsRefusedBeforeAnyRequestAndWithoutBase64() {
        picture("images/a.png")
        File(threadFolder, "inbox").mkdirs()
        File(threadFolder, "inbox/logo.svg").writeText("<svg xmlns=\"http://www.w3.org/2000/svg\"/>")
        File(threadFolder, "inbox/words.txt").writeText("hello")
        val fiveCopies = List(5) { "\"images/a.png\"" }.joinToString(",")
        val cases = mapOf(
            """{"prompt":"x","reference_images":["../outside.png"]}""" to "not inside this thread's folder",
            """{"prompt":"x","reference_images":["images/missing.png"]}""" to "does not exist",
            """{"prompt":"x","reference_images":["inbox/words.txt"]}""" to "not a png, jpeg or webp",
            """{"prompt":"x","reference_images":["inbox/logo.svg"]}""" to "SVG",
            """{"prompt":"x","model":"$fluxKey","reference_images":[$fiveCopies]}""" to "takes at most 4",
            """{"prompt":"x","model":"openrouter:a/none","reference_images":["images/a.png"]}""" to "takes no reference pictures",
        )
        for ((json, expected) in cases) {
            val output = run(toolWithFacts(), json)
            assertTrue(json, output.isError)
            assertTrue("$json -> ${output.text}", output.text.contains(expected))
            assertFalse(output.text.contains("base64"))
        }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun aModelThatTakesNoPicturesNamesAnAddedModelThatDoes() {
        picture("images/a.png")

        val output = run(toolWithFacts(), """{"prompt":"x","model":"openrouter:a/none","reference_images":["images/a.png"]}""")

        assertTrue(output.text, output.text.contains(gptKey))
    }

    @Test
    fun withoutAModelListReferencesAreRefusedForOpenRouterAndNotCheckedForGemini() {
        picture("images/a.png")

        val refused = run(toolWithFacts(factsByKey = emptyMap()), """{"prompt":"x","reference_images":["images/a.png"]}""")
        assertTrue(refused.text, refused.isError && refused.text.contains("could not be loaded"))
        assertTrue(requests.isEmpty())

        val gemini = GenerateImageTool(
            ImageGenerator { request -> requests += request; success() },
            listOf("gemini:gemini-2.5-flash-image"),
            { "gemini:gemini-2.5-flash-image" },
        )
        assertFalse(run(gemini, """{"prompt":"x","reference_images":["images/a.png"]}""").isError)
        assertEquals(1, requests.single().references.size)
    }

    @Test
    fun theSchemaAndGuidelinesCarryTheNewArgumentsAndAdvice() {
        val tool = toolWithFacts()
        val schema = tool.parameterSchema.toString()
        assertTrue(schema.contains("\"quality\""))
        assertTrue(schema.contains("\"reference_images\""))
        assertTrue(schema.contains("\"required\":[\"prompt\"]"))
        val guidelines = tool.guidelines.joinToString("\n")
        assertTrue(guidelines.contains("reference_images"))
    }
}
