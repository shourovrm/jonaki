package app.jonaki.ui

import app.jonaki.feature.chat.ImageModelChoiceUi
import app.jonaki.feature.chat.MediaKind
import app.jonaki.feature.chat.MediaModeUi
import app.jonaki.core.toolapi.VideoModelFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaModeUiBuilderTest {
    private val flux = ImageModelChoiceUi("openrouter:flux", "FLUX.2 Klein", "OpenRouter", priceText = "\$0.08 per image")
    private val recraft = ImageModelChoiceUi("openrouter:recraft-vector", "Recraft V3 Vector", "OpenRouter", isVector = true)
    private val gemini = ImageModelChoiceUi("gemini:imagen", "imagen", "Gemini")

    private val videoKey = "openrouter:x-ai/grok"
    private val videoFacts = VideoModelFacts(
        modelKey = videoKey,
        supportedDurations = listOf(4, 8),
        supportedResolutions = listOf("720p"),
        priceSkus = mapOf("duration_seconds_720p" to "0.03"),
    )
    private val words = VideoStepWords(
        collectsEarlierJob = { jobId -> "Collect $jobId" },
        aboutCost = { dollars -> "about $dollars" },
        priceIsPerToken = "per token",
        seconds = { seconds -> "$seconds s" },
    )
    private val videoText = VideoStepText(
        factsByModelKey = mapOf(videoKey to videoFacts),
        defaultModelKey = videoKey,
        words = words,
        displayNamesByModelKey = mapOf(videoKey to "Grok Imagine"),
    )

    private fun build(
        selected: MediaKind?,
        kinds: List<MediaKind> = MediaKind.entries.toList(),
        videoModelKey: String? = videoKey,
    ): MediaModeUi? = MediaModeUiBuilder.build(
        availableKinds = kinds,
        selected = selected,
        canChange = true,
        imageChoices = listOf(flux, recraft, gemini),
        pictureModelKey = flux.key,
        vectorModelKey = recraft.key,
        videoModelKey = videoModelKey,
        videoStepText = videoText,
    )

    @Test
    fun noAvailableKindMeansNoChips() {
        assertNull(build(selected = null, kinds = emptyList()))
    }

    @Test
    fun withNothingSelectedThereAreNoDetails() {
        val ui = build(selected = null)!!
        assertNull(ui.modelName)
        assertNull(ui.priceText)
        assertNull(ui.videoCostText)
    }

    @Test
    fun aPictureNamesItsModelAndShowsTheKnownPrice() {
        val ui = build(MediaKind.PICTURE)!!
        assertEquals("FLUX.2 Klein", ui.modelName)
        assertEquals("\$0.08 per image", ui.priceText)
    }

    @Test
    fun aVectorImageNamesTheVectorModelAndNoPriceWhenNotKnown() {
        val ui = build(MediaKind.VECTOR)!!
        assertEquals("Recraft V3 Vector", ui.modelName)
        assertNull(ui.priceText)
    }

    @Test
    fun aVideoShowsModelLengthResolutionAndTheEstimateOfAPlainCall() {
        val ui = build(MediaKind.VIDEO)!!
        assertEquals("Grok Imagine", ui.modelName)
        assertEquals("4 s", ui.videoLengthText)
        assertEquals("720p", ui.videoResolution)
        assertEquals("about \$0.12", ui.videoCostText)
    }

    @Test
    fun aVideoModelWithoutAListEntryIsNamedByItsIdAndHasNoEstimate() {
        val ui = build(MediaKind.VIDEO, videoModelKey = "openrouter:other/model")!!
        assertEquals("other/model", ui.modelName)
        assertNull(ui.videoLengthText)
        assertNull(ui.videoCostText)
    }
}
