package app.jonaki.ui

import app.jonaki.feature.chat.ImageModelChoiceUi
import app.jonaki.feature.chat.MediaKind
import app.jonaki.feature.chat.MediaModeUi
import app.jonaki.feature.chat.MediaModelRowUi
import app.jonaki.feature.chat.MediaSettingChange
import app.jonaki.run.MediaCallOptions
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

    private val otherVideoKey = "openrouter:minimax/hailuo"
    private val videoRows = listOf(
        MediaModelRowUi(videoKey, "Grok Imagine", "\$0.03 per second"),
        MediaModelRowUi(otherVideoKey, "Hailuo", "\$0.08 per second"),
    )

    private fun build(
        selected: MediaKind?,
        kinds: List<MediaKind> = MediaKind.entries.toList(),
        videoModelKey: String? = videoKey,
        choices: MediaChoices = MediaChoices(),
        defaultIsHighQuality: Boolean = false,
    ): MediaModeUi? = MediaModeUiBuilder.build(
        videoModels = videoRows,
        choices = choices,
        defaultIsHighQuality = defaultIsHighQuality,
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

    @Test
    fun theMenuNamesEachKindsModelAndPriceEvenWhileTheModeIsOff() {
        val rows = build(selected = null)!!.kindRows

        assertEquals(MediaKind.entries.toList(), rows.map { it.kind })
        assertEquals(listOf("FLUX.2 Klein", "Recraft V3 Vector", "Grok Imagine"), rows.map { it.modelName })
        assertEquals(listOf("\$0.08 per image", null, "about \$0.12"), rows.map { it.priceText })
    }

    @Test
    fun aPictureSheetListsOnlyRasterModelsAndFollowsTheDefaultQualityUntilOneIsPicked() {
        val followsDefault = build(MediaKind.PICTURE, defaultIsHighQuality = true)!!.settings!!
        val picked = build(MediaKind.PICTURE, defaultIsHighQuality = true, choices = MediaChoices(isHighQuality = false))!!.settings!!

        assertEquals(listOf(flux.key, gemini.key), followsDefault.models.map { it.key })
        assertEquals(flux.key, followsDefault.selectedModelKey)
        assertEquals(true, followsDefault.isHighQuality)
        assertEquals(false, picked.isHighQuality)
        assertNull(followsDefault.selectedShape)
    }

    @Test
    fun aVectorSheetListsOnlyVectorModelsAndHasNoQuality() {
        val settings = build(MediaKind.VECTOR, choices = MediaChoices(vectorShape = "16:9"))!!.settings!!

        assertEquals(listOf(recraft.key), settings.models.map { it.key })
        assertNull(settings.isHighQuality)
        assertEquals("16:9", settings.selectedShape)
    }

    @Test
    fun aVideoSheetOffersTheModelsLengthsAndTheEstimateFollowsTheChosenLength() {
        val defaults = build(MediaKind.VIDEO)!!
        val longer = build(MediaKind.VIDEO, choices = MediaChoices(videoLengthSeconds = 8))!!

        assertEquals(listOf(4, 8), defaults.settings!!.lengthsSeconds)
        assertEquals(4, defaults.settings!!.selectedLengthSeconds)
        assertEquals("about \$0.12", defaults.videoCostText)
        assertEquals(8, longer.settings!!.selectedLengthSeconds)
        assertEquals("8 s", longer.videoLengthText)
        assertEquals("about \$0.24", longer.videoCostText)
    }

    @Test
    fun aVideoModelPickedInTheSheetIsUsedWhileItIsStillAdded() {
        assertEquals(otherVideoKey, build(MediaKind.VIDEO, choices = MediaChoices(videoModelKey = otherVideoKey))!!.settings!!.selectedModelKey)
        assertEquals(videoKey, build(MediaKind.VIDEO, choices = MediaChoices(videoModelKey = "openrouter:removed"))!!.settings!!.selectedModelKey)
    }

    @Test
    fun aLongListOfLengthsIsSpreadOverFiveChoicesThatKeepTheEndsAndTheOneInUse() {
        val everySecond = (2..30).toList()

        assertEquals(listOf(2, 9, 16, 23, 30), MediaModeUiBuilder.spread(everySecond, 5, mustInclude = null))
        assertEquals(listOf(2, 5, 16, 23, 30), MediaModeUiBuilder.spread(everySecond, 5, mustInclude = 5))
        assertEquals(listOf(4, 8), MediaModeUiBuilder.spread(listOf(4, 8), 5, mustInclude = 4))
    }

    @Test
    fun aChangeInTheSheetSetsTheChoiceOfItsKindAndANewVideoModelResetsLengthAndSize() {
        val choices = MediaChoices(videoLengthSeconds = 8, videoResolution = "720p")

        assertEquals("16:9", choices.after(MediaKind.PICTURE, MediaSettingChange.Shape("16:9")).pictureShape)
        assertEquals("3:4", choices.after(MediaKind.VECTOR, MediaSettingChange.Shape("3:4")).vectorShape)
        assertEquals(true, choices.after(MediaKind.PICTURE, MediaSettingChange.Quality(isHigh = true)).isHighQuality)
        assertEquals(
            MediaChoices(videoModelKey = otherVideoKey),
            choices.after(MediaKind.VIDEO, MediaSettingChange.Model(otherVideoKey)),
        )
    }

    @Test
    fun theCallOptionsCarryOnlyWhatTheUserSetAndDropAVideoModelThatWasRemoved() {
        val choices = MediaChoices(isHighQuality = true, pictureShape = "9:16", videoModelKey = "openrouter:removed", videoLengthSeconds = 8)

        assertEquals(MediaCallOptions(isHighQuality = true, aspectRatio = "9:16"), choices.callOptionsFor(MediaKind.PICTURE, listOf(videoKey)))
        assertEquals(MediaCallOptions(), choices.callOptionsFor(MediaKind.VECTOR, listOf(videoKey)))
        assertEquals(MediaCallOptions(durationSeconds = 8), choices.callOptionsFor(MediaKind.VIDEO, listOf(videoKey)))
    }
}
