package app.jonaki.core.toolapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoModelFactsTest {
    private val grok = mapOf(
        "cents_per_image_input" to "1",
        "cents_per_video_output_second_480p" to "2",
        "cents_per_video_output_second_720p" to "3",
        "cents_per_video_output_second_1080p" to "14",
    )
    private val hailuo = mapOf("duration_seconds" to "0.08", "duration_seconds_480p" to "0.05", "duration_seconds_768p" to "0.08")
    private val wan = mapOf("duration_seconds_480p" to "0.05", "duration_seconds_720p" to "0.1", "duration_seconds_1080p" to "0.2")
    private val veo = mapOf(
        "duration_seconds_with_audio" to "0.08",
        "duration_seconds_without_audio" to "0.05",
        "duration_seconds_with_audio_720p" to "0.05",
        "duration_seconds_without_audio_720p" to "0.03",
    )
    private val runway = mapOf("cents_per_second_output" to "12")
    private val flux = mapOf(
        "cents_per_second_output" to "17",
        "cents_per_second_output_720p" to "17",
        "cents_per_second_output_1080p" to "29",
    )
    private val seedance = mapOf("video_tokens" to "0.000007", "video_tokens_without_audio" to "0.000005")

    private fun perSecond(skus: Map<String, String>, resolution: String?, withAudio: Boolean = false) =
        VideoPricing.perSecondUsd(skus, resolution, withAudio)

    @Test
    fun grokPricesInCentsPerSecondOfOutputPerResolution() {
        assertEquals(0.03, perSecond(grok, "720p")!!, 1e-9)
        assertEquals(0.14, perSecond(grok, "1080p")!!, 1e-9)
        assertEquals(0.02, perSecond(grok, "480p")!!, 1e-9)
    }

    @Test
    fun theImageInputEntryIsNeverTakenForAPerSecondPrice() {
        assertNull(perSecond(mapOf("cents_per_image_input" to "1"), "720p"))
    }

    @Test
    fun hailuoPricesInDollarsAndFallsBackToThePlainEntry() {
        assertEquals(0.05, perSecond(hailuo, "480p")!!, 1e-9)
        assertEquals(0.08, perSecond(hailuo, "768p")!!, 1e-9)
        assertEquals(0.08, perSecond(hailuo, null)!!, 1e-9)
        assertEquals(0.08, perSecond(hailuo, "1080p")!!, 1e-9)
    }

    @Test
    fun wanHasOnlyPerResolutionEntries() {
        assertEquals(0.1, perSecond(wan, "720p")!!, 1e-9)
        assertNull(perSecond(wan, null))
    }

    @Test
    fun veoTellsSoundFromSilenceAndFallsBackWhenAResolutionHasNoOwnEntry() {
        assertEquals(0.05, perSecond(veo, "720p", withAudio = true)!!, 1e-9)
        assertEquals(0.03, perSecond(veo, "720p", withAudio = false)!!, 1e-9)
        assertEquals(0.08, perSecond(veo, "1080p", withAudio = true)!!, 1e-9)
        assertEquals(0.05, perSecond(veo, "1080p", withAudio = false)!!, 1e-9)
    }

    @Test
    fun runwayHasOnePriceForEveryResolution() {
        assertEquals(0.12, perSecond(runway, "720p")!!, 1e-9)
        assertEquals(0.12, perSecond(runway, null)!!, 1e-9)
    }

    @Test
    fun fluxPricesPerResolutionInCents() {
        assertEquals(0.17, perSecond(flux, "720p")!!, 1e-9)
        assertEquals(0.29, perSecond(flux, "1080p")!!, 1e-9)
    }

    @Test
    fun resolutionsWrittenWithCapitalsFindTheirLowerCaseEntry() {
        val skus = mapOf("duration_seconds_4k" to "0.4")
        assertEquals(0.4, perSecond(skus, "4K")!!, 1e-9)
    }

    @Test
    fun theKlingTextToVideoEntriesAreRead() {
        val skus = mapOf("text_to_video_duration_seconds_720p" to "0.084", "image_to_video_duration_seconds_720p" to "0.2")
        assertEquals(0.084, perSecond(skus, "720p")!!, 1e-9)
    }

    @Test
    fun estimateMultipliesBySecondsAndAPerTokenModelIsNotEstimated() {
        val grokEstimate = VideoPricing.estimate(grok, "720p", withAudio = false, seconds = 5) as VideoEstimate.Dollars
        assertEquals(0.15, grokEstimate.totalUsd, 1e-9)
        assertEquals(VideoEstimate.PerToken, VideoPricing.estimate(seedance, "720p", withAudio = true, seconds = 5))
        assertEquals(VideoEstimate.Unknown, VideoPricing.estimate(null, "720p", withAudio = true, seconds = 5))
        assertEquals(VideoEstimate.Unknown, VideoPricing.estimate(mapOf("minimum_cents_per_generation" to "5"), null, false, 5))
    }

    @Test
    fun theRangeSpansTheLowestAndHighestResolution() {
        val range = VideoPricing.priceOf(grok, listOf("480p", "720p", "1080p"), withAudio = false) as VideoPrice.PerSecond
        assertEquals(0.02, range.lowestUsd, 1e-9)
        assertEquals(0.14, range.highestUsd, 1e-9)
        assertEquals(VideoPrice.PerSecond(0.12, 0.12), VideoPricing.priceOf(runway, listOf("720p"), withAudio = false))
        assertEquals(VideoPrice.PerSecond(0.12, 0.12), VideoPricing.priceOf(runway, null, withAudio = false))
        assertEquals(VideoPrice.PerToken, VideoPricing.priceOf(seedance, listOf("480p"), withAudio = true))
        assertEquals(VideoPrice.Unknown, VideoPricing.priceOf(emptyMap(), listOf("480p"), withAudio = true))
    }

    @Test
    fun dollarsShowTwoDecimalsFromTenCentsAndThreeBelow() {
        assertEquals("$0.03", VideoPricing.dollars(0.03))
        assertEquals("$0.014", VideoPricing.dollars(0.014))
        assertEquals("$0.15", VideoPricing.dollars(0.15))
        assertEquals("$2.40", VideoPricing.dollars(2.4))
        assertEquals("$0.14", VideoPricing.dollars(0.14))
    }

    private val veoFacts = VideoModelFacts(
        modelKey = "openrouter:google/veo-3.1-lite",
        supportedDurations = listOf(8, 4, 6),
        supportedResolutions = listOf("720p", "1080p"),
        supportedAspectRatios = listOf("16:9", "9:16"),
        generatesAudio = true,
    )

    private fun chosen(result: VideoChoiceResult) = (result as VideoChoiceResult.Chosen).choice

    @Test
    fun theDefaultsAreTheShortestLengthOfAtLeastFourSecondsAnd720p() {
        val choice = chosen(VideoChoices.resolve(veoFacts, null, null, null, null))
        assertEquals(VideoChoice(4, "720p", null, null), choice)
    }

    @Test
    fun aModelWithOnlyShortLengthsGetsItsShortestAndTheLowestResolutionWhen720pIsNotOffered() {
        val facts = VideoModelFacts("openrouter:x/y", supportedDurations = listOf(3, 2, 1), supportedResolutions = listOf("1080p", "480p", "2K"))
        assertEquals(VideoChoice(1, "480p", null, null), chosen(VideoChoices.resolve(facts, null, null, null, null)))
    }

    @Test
    fun aLengthOfFourOrMoreIsPreferredEvenWhenShorterOnesExist() {
        val facts = VideoModelFacts("openrouter:x/y", supportedDurations = (1..15).toList(), supportedResolutions = listOf("480p", "720p"))
        assertEquals(4, chosen(VideoChoices.resolve(facts, null, null, null, null)).durationSeconds)
    }

    @Test
    fun valuesTheAgentNamesAreKeptWhenTheModelSupportsThem() {
        val choice = chosen(VideoChoices.resolve(veoFacts, 8, "1080P", "9:16", false))
        assertEquals(VideoChoice(8, "1080p", "9:16", false), choice)
    }

    @Test
    fun anUnsupportedLengthIsRefusedWithTheSupportedOnes() {
        val refused = VideoChoices.resolve(veoFacts, 5, null, null, null) as VideoChoiceResult.Refused
        assertTrue(refused.message, "4, 6, 8 s" in refused.message)
    }

    @Test
    fun anUnsupportedResolutionAndShapeAreRefusedWithTheSupportedOnes() {
        val resolution = VideoChoices.resolve(veoFacts, null, "480p", null, null) as VideoChoiceResult.Refused
        assertTrue(resolution.message, "720p, 1080p" in resolution.message)
        val shape = VideoChoices.resolve(veoFacts, null, null, "1:1", null) as VideoChoiceResult.Refused
        assertTrue(shape.message, "16:9, 9:16" in shape.message)
    }

    @Test
    fun soundIsRefusedForAModelThatCannotMakeIt() {
        val silent = veoFacts.copy(generatesAudio = false)
        assertTrue(VideoChoices.resolve(silent, null, null, null, true) is VideoChoiceResult.Refused)
        assertEquals(false, chosen(VideoChoices.resolve(silent, null, null, null, false)).withAudio)
    }

    @Test
    fun withoutFactsEverythingIsPassedOnUnchecked() {
        assertEquals(VideoChoice(5, "999p", "2:1", true), chosen(VideoChoices.resolve(null, 5, "999p", "2:1", true)))
        assertEquals(VideoChoice(null, null, null, null), chosen(VideoChoices.resolve(null, null, null, null, null)))
    }

    @Test
    fun secondsAreDescribedAsAListOrARun() {
        assertEquals("4, 6, 8 s", VideoChoices.describeSeconds(listOf(8, 4, 6)))
        assertEquals("1 to 15 s", VideoChoices.describeSeconds((1..15).toList()))
        assertEquals("5, 10 s", VideoChoices.describeSeconds(listOf(10, 5)))
    }

    @Test
    fun theResultTextContractNamesTheFile() {
        assertEquals("videos/a.mp4", GeneratedVideos.pathIn(GeneratedVideos.firstLine("videos/a.mp4") + "\nLength: 4 s"))
        assertNull(GeneratedVideos.pathIn("The video is still being made."))
        assertNull(GeneratedVideos.pathIn("Error: nope"))
        assertNull(GeneratedVideos.pathIn("Video saved: "))
    }
}
