package app.jonaki.ui

import app.jonaki.core.modelcatalog.OpenRouterVideoModels
import app.jonaki.core.toolapi.VideoModelFacts
import app.jonaki.run.VideoToolSetup
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoTextsTest {
    private val models = OpenRouterVideoModels.parse(
        File(System.getProperty("jonaki.testdata") ?: "../testdata", "openrouter/video-models-trimmed.json").readText(),
    ).associateBy { it.id }

    private val listWords = VideoListWords(
        priceRange = { lowest, highest -> "$lowest to $highest per second" },
        priceSingle = { price -> "$price per second" },
        perToken = "per token",
        lengthsRun = { first, last -> "$first to $last s" },
        lengthsList = { seconds -> "$seconds s" },
    )

    private val stepWords = VideoStepWords(
        collectsEarlierJob = { jobId -> "Collect video job $jobId, no new cost" },
        aboutCost = { dollars -> "about $dollars" },
        priceIsPerToken = "price per token, not estimated",
        seconds = { seconds -> "$seconds s" },
    )

    private fun price(id: String) = VideoListText.price(models.getValue(id), listWords)

    @Test
    fun theRecordedModelsGetTheirPriceRangeOrSinglePriceOrPerToken() {
        assertEquals("$0.02 to $0.14 per second", price("x-ai/grok-imagine-video-1.5-lite"))
        assertEquals("$0.12 per second", price("runway/gen-4.5"))
        assertEquals("$0.05 to $0.20 per second", price("alibaba/wan-3.0"))
        assertEquals("$0.17 to $0.29 per second", price("black-forest-labs/flux-3-video"))
        assertEquals("per token", price("bytedance/seedance-2.0"))
    }

    @Test
    fun aModelWithAudioByDefaultIsPricedWithSound() {
        // google/veo-3.1-lite: with sound 720p $0.05 and 1080p falls back to the plain "with audio" entry, $0.08.
        assertEquals("$0.05 to $0.08 per second", price("google/veo-3.1-lite"))
    }

    @Test
    fun hailuoUsesItsPlainEntryForTheResolutionWithoutOne() {
        assertEquals("$0.05 to $0.08 per second", price("minimax/hailuo-3-max"))
    }

    @Test
    fun aModelThatListsNoResolutionsUsesItsPlainEntryAndAModelWithoutEntriesHasNoPriceLine() {
        assertEquals("$0.03 per second", price("black-forest-labs/flux-video-edit"))
        val noEntries = models.getValue("runway/gen-4.5").copy(priceSkus = mapOf("minimum_cents_per_generation" to "5"))
        assertNull(VideoListText.price(noEntries, listWords))
    }

    @Test
    fun lengthsAreARunOrAList() {
        assertEquals("1 to 15 s", VideoListText.lengths(models.getValue("x-ai/grok-imagine-video-1.5-lite"), listWords))
        assertEquals("4, 6, 8 s", VideoListText.lengths(models.getValue("google/veo-3.1-lite"), listWords))
        assertNull(VideoListText.lengths(models.getValue("black-forest-labs/flux-video-edit"), listWords))
    }

    private fun stepText(vararg ids: String, default: String? = "openrouter:${ids.first()}") = VideoStepText(
        factsByModelKey = ids.associate { id -> "openrouter:$id" to VideoToolSetup.factsOf(models.getValue(id)) },
        defaultModelKey = default,
        words = stepWords,
    )

    private fun target(text: VideoStepText, json: String) = text.target(Json.parseToJsonElement(json).jsonObject)

    @Test
    fun theCardShowsModelDefaultLengthDefaultResolutionEstimateAndPrompt() {
        val text = stepText("x-ai/grok-imagine-video-1.5-lite")

        // 4 s at 720p is 3 cents a second.
        assertEquals(
            "openrouter:x-ai/grok-imagine-video-1.5-lite · 4 s · 720p · about $0.12 · A boat at dawn",
            target(text, """{"prompt":"A boat at dawn"}"""),
        )
    }

    @Test
    fun theCardUsesTheAgentsValuesAndTheNamedModel() {
        val text = stepText("x-ai/grok-imagine-video-1.5-lite", "runway/gen-4.5")

        assertEquals(
            "openrouter:runway/gen-4.5 · 5 s · 720p · about $0.60 · A boat",
            target(text, """{"prompt":"A boat","model":"openrouter:runway/gen-4.5","duration_seconds":5}"""),
        )
    }

    @Test
    fun aPerTokenModelSaysThePriceIsNotEstimated() {
        val text = stepText("bytedance/seedance-2.0")

        val line = target(text, """{"prompt":"A boat"}""")!!

        assertTrue(line, line.contains("price per token, not estimated"))
        assertTrue(line, !line.contains("about"))
    }

    @Test
    fun aCollectingCallSaysItCostsNothingNew() {
        val text = stepText("runway/gen-4.5")

        assertEquals("Collect video job job-1, no new cost", target(text, """{"job_id":"job-1"}"""))
    }

    @Test
    fun withoutFactsOnlyTheModelAndThePromptAreShown() {
        val text = VideoStepText(emptyMap(), "openrouter:a/b", stepWords)

        assertEquals("openrouter:a/b · A boat", target(text, """{"prompt":"A boat"}"""))
    }

    @Test
    fun aRefusedValueIsShownAsAskedBecauseTheToolRefusesItBeforeAnyRequest() {
        val facts: Map<String, VideoModelFacts> = mapOf("openrouter:runway/gen-4.5" to VideoToolSetup.factsOf(models.getValue("runway/gen-4.5")))
        val text = VideoStepText(facts, "openrouter:runway/gen-4.5", stepWords)

        assertEquals(
            "openrouter:runway/gen-4.5 · 99 s · about $11.88 · A boat",
            target(text, """{"prompt":"A boat","duration_seconds":99}"""),
        )
    }

    @Test
    fun theDefaultsOfAPlainCallAreTheShortestLengthTheLowestResolutionAndAnEstimate() {
        val text = stepText("x-ai/grok-imagine-video-1.5-lite")

        val defaults = text.defaultsFor("openrouter:x-ai/grok-imagine-video-1.5-lite")

        assertEquals("4 s", defaults.lengthText)
        assertEquals("720p", defaults.resolution)
        assertEquals("about $0.12", defaults.estimateText)
    }

    @Test
    fun withoutFactsThereAreNoDefaultsAndNoEstimate() {
        val defaults = VideoStepText(emptyMap(), "openrouter:a/b", stepWords).defaultsFor("openrouter:a/b")

        assertNull(defaults.lengthText)
        assertNull(defaults.resolution)
        assertNull(defaults.estimateText)
    }

    @Test
    fun aModelWithPerTokenPricesHasTheNotEstimatedText() {
        val text = stepText("bytedance/seedance-2.0")

        assertEquals("price per token, not estimated", text.defaultsFor("openrouter:bytedance/seedance-2.0").estimateText)
    }

    @Test
    fun theModelIsNamedFromTheServicesListElseByItsId() {
        val named = VideoStepText(emptyMap(), null, stepWords, mapOf("openrouter:a/b" to "Model B"))

        assertEquals("Model B", named.displayNameOf("openrouter:a/b"))
        assertEquals("c/d", named.displayNameOf("openrouter:c/d"))
    }
}
