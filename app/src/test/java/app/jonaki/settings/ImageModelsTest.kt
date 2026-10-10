package app.jonaki.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageModelsTest {
    private val flux = "black-forest-labs/flux.2-klein-4b"
    private val gpt = "openai/gpt-image-1-mini"

    @Test
    fun theFirstModelAddedIsStarred() {
        val models = ImageModels().add(flux).add(gpt)

        assertEquals(listOf(flux, gpt), models.modelIds)
        assertEquals(flux, models.defaultModelId)
    }

    @Test
    fun blankAndRepeatedIdsChangeNothing() {
        val models = ImageModels().add(flux)

        assertEquals(models, models.add("  "))
        assertEquals(models, models.add(" $flux "))
    }

    @Test
    fun starringMovesTheStarAndIgnoresUnlistedIds() {
        val models = ImageModels().add(flux).add(gpt)

        assertEquals(gpt, models.setDefault(gpt).defaultModelId)
        assertEquals(flux, models.setDefault("other/model").defaultModelId)
    }

    @Test
    fun removingTheStarredModelStarsTheFirstLeft() {
        val models = ImageModels().add(flux).add(gpt).setDefault(gpt)

        val afterRemoving = models.remove(gpt)

        assertEquals(listOf(flux), afterRemoving.modelIds)
        assertEquals(flux, afterRemoving.defaultModelId)
    }

    @Test
    fun removingAnotherModelKeepsTheStar() {
        val models = ImageModels().add(flux).add(gpt).setDefault(gpt)

        assertEquals(gpt, models.remove(flux).defaultModelId)
    }

    @Test
    fun removingTheLastModelLeavesNoStar() {
        val empty = ImageModels().add(flux).remove(flux)

        assertEquals(emptyList<String>(), empty.modelIds)
        assertNull(empty.defaultModelId)
    }

    @Test
    fun textRoundTripsAndRepairsAStarThatIsNotListed() {
        val saved = ImageModels.toText(listOf(flux, gpt))

        assertEquals(ImageModels(listOf(flux, gpt), gpt), ImageModels.fromText(saved, gpt))
        assertEquals(flux, ImageModels.fromText(saved, "gone/model").defaultModelId)
        assertEquals(ImageModels(), ImageModels.fromText("", null))
        assertEquals(listOf(flux), ImageModels.fromText("$flux\n\n $flux \n", null).modelIds)
    }
}
