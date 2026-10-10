package app.jonaki.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelProvidersTest {
    private fun option(tag: String) = ProviderOptionUi(tag, tag, null, 0.1, 0.2, null)

    // Listed cheapest first, as the app delivers them.
    private val options = listOf(option("deepinfra/fp4"), option("fireworks"), option("z-ai/fp8"))

    @Test
    fun chosenTagsFollowThePriceOrderNotTheOrderOfTicking() {
        val ticked = linkedSetOf("z-ai/fp8", "deepinfra/fp4")

        assertEquals(listOf("deepinfra/fp4", "z-ai/fp8"), chosenTagsInListOrder(options, ticked))
    }

    @Test
    fun aTagThatIsNoLongerOfferedIsDropped() {
        assertEquals(listOf("fireworks"), chosenTagsInListOrder(options, setOf("fireworks", "gone/fp8")))
    }

    @Test
    fun nothingTickedGivesAnEmptyList() {
        assertEquals(emptyList<String>(), chosenTagsInListOrder(options, emptySet()))
    }

    @Test
    fun theProviderOfATagIsItsPartBeforeTheSlash() {
        assertEquals("deepinfra", providerOfTag("deepinfra/fp4"))
        assertEquals("fireworks", providerOfTag("fireworks"))
    }
}
