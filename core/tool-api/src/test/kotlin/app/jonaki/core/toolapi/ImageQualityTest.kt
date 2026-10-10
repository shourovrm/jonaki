package app.jonaki.core.toolapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageQualityTest {
    private fun facts(quality: List<String>? = null, resolution: List<String>? = null) =
        ImageModelFacts("openrouter:x/y", qualityValues = quality, resolutionValues = resolution)

    private val gptImage = facts(quality = listOf("auto", "low", "medium", "high", "xhigh", "max"))
    private val nanoBanana = facts(resolution = listOf("1K", "2K", "4K"))
    private val grok = facts(quality = listOf("low", "medium"), resolution = listOf("1K", "2K"))
    private val flux = facts()

    @Test
    fun standardSendsNothingWhateverTheModelDeclares() {
        for (model in listOf(gptImage, nanoBanana, grok, flux)) {
            assertEquals(ImageQualityPlan.NOTHING, ImageQualityChoices.plan(model, ImageQuality.STANDARD))
        }
    }

    @Test
    fun highSendsHighWhenTheModelListsItAndNeverXhighOrMax() {
        val plan = ImageQualityChoices.plan(gptImage, ImageQuality.HIGH)
        assertEquals("high", plan.quality)
        assertNull(plan.resolution)
    }

    @Test
    fun highSendsTheHighestListedValueNotAboveHigh() {
        assertEquals("medium", ImageQualityChoices.plan(grok, ImageQuality.HIGH).quality)
        assertEquals("medium", ImageQualityChoices.plan(facts(quality = listOf("low", "medium", "xhigh")), ImageQuality.HIGH).quality)
    }

    @Test
    fun aQualityListWithOnlyUnknownOrHigherValuesSendsNoQuality() {
        assertNull(ImageQualityChoices.plan(facts(quality = listOf("auto", "xhigh", "max")), ImageQuality.HIGH).quality)
    }

    @Test
    fun highOnAModelWithOnlyResolutionSendsTwoK() {
        val plan = ImageQualityChoices.plan(nanoBanana, ImageQuality.HIGH)
        assertNull(plan.quality)
        assertEquals("2K", plan.resolution)
    }

    @Test
    fun withoutTwoKTheSmallestValueAboveOneKIsSent() {
        assertEquals("1.5K", ImageQualityChoices.plan(facts(resolution = listOf("1K", "4K", "1.5K")), ImageQuality.HIGH).resolution)
        assertEquals("4K", ImageQualityChoices.plan(facts(resolution = listOf("1K", "4K")), ImageQuality.HIGH).resolution)
        assertNull(ImageQualityChoices.plan(facts(resolution = listOf("512", "1K")), ImageQuality.HIGH).resolution)
    }

    @Test
    fun aModelWithBothGetsOnlyTheQuality() {
        val plan = ImageQualityChoices.plan(grok, ImageQuality.HIGH)
        assertEquals("medium", plan.quality)
        assertNull(plan.resolution)
    }

    @Test
    fun aModelWithNeitherSendsNothingAndSaysSo() {
        val plan = ImageQualityChoices.plan(flux, ImageQuality.HIGH)
        assertNull(plan.quality)
        assertNull(plan.resolution)
        assertEquals(ImageQualityPlan.Note.NO_SETTING, plan.note)
    }

    @Test
    fun withoutFactsNothingIsSentAndTheNoteSaysTheModelWasNotChecked() {
        val plan = ImageQualityChoices.plan(null, ImageQuality.HIGH)
        assertNull(plan.quality)
        assertNull(plan.resolution)
        assertEquals(ImageQualityPlan.Note.NOT_CHECKED, plan.note)
    }

    @Test
    fun theArgumentIsReadAsWordsAndAnythingElseIsNull() {
        assertEquals(ImageQuality.HIGH, ImageQuality.fromArgument(" High "))
        assertEquals(ImageQuality.STANDARD, ImageQuality.fromArgument("standard"))
        assertNull(ImageQuality.fromArgument("ultra"))
        assertNull(ImageQuality.fromArgument(null))
        assertEquals(ImageQuality.STANDARD, ImageQuality.fromStored(null))
        assertEquals(ImageQuality.HIGH, ImageQuality.fromStored("high"))
    }
}
