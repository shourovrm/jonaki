package app.jonaki.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchOrderTest {
    private val order = listOf("tavily", "ollama", "exa")

    @Test
    fun movingUpSwapsWithThePreviousService() {
        assertEquals(listOf("tavily", "exa", "ollama"), moveInOrder(order, "exa", -1))
    }

    @Test
    fun movingDownSwapsWithTheNextService() {
        assertEquals(listOf("ollama", "tavily", "exa"), moveInOrder(order, "tavily", 1))
    }

    @Test
    fun movingPastEitherEndChangesNothing() {
        assertEquals(order, moveInOrder(order, "tavily", -1))
        assertEquals(order, moveInOrder(order, "exa", 1))
    }

    @Test
    fun unknownKeyChangesNothing() {
        assertEquals(order, moveInOrder(order, "brave", 1))
    }
}
